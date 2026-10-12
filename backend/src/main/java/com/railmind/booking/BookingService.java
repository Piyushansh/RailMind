package com.railmind.booking;

import com.railmind.audit.AuditService;
import com.railmind.auth.User;
import com.railmind.auth.UserRepository;
import com.railmind.booking.BookingEntities.*;
import com.railmind.booking.dto.BookingDtos.*;
import com.railmind.common.exceptions.AppExceptions.*;
import com.railmind.station.Station;
import com.railmind.station.StationRepository;
import com.railmind.train.ScheduleEntities.TrainSchedule;
import com.railmind.train.SeatEntities.SeatAvailability;
import com.railmind.train.TrainRepositories;
import com.railmind.train.TrainSearchService;
import com.railmind.train.dto.TrainDtos.FareQuoteDto;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Owns the booking lifecycle: seat allocation, transactional booking creation,
 * and PNR generation. Seat allocation uses optimistic locking (see
 * SeatEntities.SeatAvailability's @Version field): each attempt to claim a seat
 * re-reads and re-saves the row, and a concurrent claim on the same seat causes
 * an OptimisticLockingFailureException, which is caught here and retried against
 * the next candidate seat rather than allowing a double booking.
 */
@Service
public class BookingService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingRepositories.BookingRepo bookingRepo;
    private final BookingRepositories.PassengerRepo passengerRepo;
    private final BookingRepositories.BookingPassengerRepo bookingPassengerRepo;
    private final BookingRepositories.PnrRepo pnrRepo;
    private final TrainRepositories.TrainScheduleRepo scheduleRepo;
    private final TrainRepositories.SeatAvailabilityRepo seatAvailabilityRepo;
    private final StationRepository stationRepository;
    private final UserRepository userRepository;
    private final TrainSearchService trainSearchService;
    private final AuditService auditService;
    private final long seatHoldMinutes;

    public BookingService(BookingRepositories.BookingRepo bookingRepo,
                           BookingRepositories.PassengerRepo passengerRepo,
                           BookingRepositories.BookingPassengerRepo bookingPassengerRepo,
                           BookingRepositories.PnrRepo pnrRepo,
                           TrainRepositories.TrainScheduleRepo scheduleRepo,
                           TrainRepositories.SeatAvailabilityRepo seatAvailabilityRepo,
                           StationRepository stationRepository,
                           UserRepository userRepository,
                           TrainSearchService trainSearchService,
                           AuditService auditService,
                           @org.springframework.beans.factory.annotation.Value("${railmind.booking.seat-hold-minutes}") long seatHoldMinutes) {
        this.bookingRepo = bookingRepo;
        this.passengerRepo = passengerRepo;
        this.bookingPassengerRepo = bookingPassengerRepo;
        this.pnrRepo = pnrRepo;
        this.scheduleRepo = scheduleRepo;
        this.seatAvailabilityRepo = seatAvailabilityRepo;
        this.stationRepository = stationRepository;
        this.userRepository = userRepository;
        this.trainSearchService = trainSearchService;
        this.auditService = auditService;
        this.seatHoldMinutes = seatHoldMinutes;
    }

    @Transactional
    public BookingDto createBooking(Long userId, CreateBookingRequest req) {
        var existing = bookingRepo.findByIdempotencyKey(req.idempotencyKey());
        if (existing.isPresent()) {
            Booking existingBooking = existing.get();
            if (!existingBooking.getUser().getId().equals(userId)) {
                // Idempotency keys are globally unique in the schema, so a key collision across
                // two different users is either a client bug or an attempted IDOR - either way,
                // we must never hand back another user's booking.
                throw new ConflictException("This idempotency key was already used by a different request.");
            }
            return toDto(existingBooking); // safe retry: same key, same user, never creates a second booking
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        TrainSchedule schedule = scheduleRepo.findById(req.scheduleId())
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));
        if (!"SCHEDULED".equals(schedule.getStatus()) && !"DELAYED".equals(schedule.getStatus())) {
            throw new ConflictException("This train is not currently accepting bookings (status: " + schedule.getStatus() + ").");
        }
        java.time.LocalDateTime scheduledDeparture = java.time.LocalDateTime.of(
                schedule.getDepartureDate(), schedule.getDepartureTime());
        if (!scheduledDeparture.isAfter(java.time.LocalDateTime.now())) {
            // Business rule: cannot book a schedule that has already departed (spec section 70,
            // "Cannot book in the past"). Delay minutes are not added here deliberately - a
            // schedule whose ORIGINAL departure is already in the past should never be
            // bookable, delayed or not.
            throw new ConflictException("This train has already departed and can no longer be booked.");
        }
        Station source = stationRepository.findById(req.sourceStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Source station not found"));
        Station destination = stationRepository.findById(req.destinationStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination station not found"));

        TrainSearchService.RouteSpan span = trainSearchService.getRouteSpan(
                schedule.getTrain().getId(), source.getId(), destination.getId());
        FareQuoteDto fareQuote = trainSearchService.calculateFare(
                schedule.getTrain().getId(), req.travelClass(), span.distanceKm());

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setSchedule(schedule);
        booking.setSourceStation(source);
        booking.setDestinationStation(destination);
        booking.setTravelClass(req.travelClass());
        booking.setIdempotencyKey(req.idempotencyKey());
        booking.setStatus("PENDING_PAYMENT");
        booking.setHoldExpiresAt(java.time.Instant.now().plus(seatHoldMinutes, java.time.temporal.ChronoUnit.MINUTES));

        BigDecimal totalFare = holdSeatsAndAttachPassengers(booking, schedule.getId(), req.travelClass(), req.passengers(), fareQuote);
        booking.setTotalFare(totalFare);
        booking = bookingRepo.save(booking);

        auditService.log(userId, "BOOKING_CREATED", "Booking", booking.getId(),
                "class=" + req.travelClass() + " passengers=" + req.passengers().size());

        return toDto(booking);
    }

    /**
     * Creates a booking that is CONFIRMED immediately (with a fresh PNR), skipping
     * PENDING_PAYMENT/payment entirely. Used ONLY by RebookingService when RailMind's
     * own disruption forces moving a passenger to a different schedule - the
     * passenger already paid once for the original booking, so this is compensation,
     * not a new discretionary purchase. Never exposed via any controller directly;
     * RebookingService is the only caller, and only after successfully securing
     * these seats does it go on to cancel-with-full-refund the original booking -
     * so a seat shortage here leaves the original booking completely untouched.
     */
    @Transactional
    public BookingDto createConfirmedRebooking(Long userId, Long newScheduleId, Long sourceStationId,
                                                Long destinationStationId, String travelClass,
                                                List<PassengerRequest> passengers, Long rebookedFromBookingId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        TrainSchedule schedule = scheduleRepo.findById(newScheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));
        Station source = stationRepository.findById(sourceStationId)
                .orElseThrow(() -> new ResourceNotFoundException("Source station not found"));
        Station destination = stationRepository.findById(destinationStationId)
                .orElseThrow(() -> new ResourceNotFoundException("Destination station not found"));

        TrainSearchService.RouteSpan span = trainSearchService.getRouteSpan(
                schedule.getTrain().getId(), source.getId(), destination.getId());
        FareQuoteDto fareQuote = trainSearchService.calculateFare(
                schedule.getTrain().getId(), travelClass, span.distanceKm());

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setSchedule(schedule);
        booking.setSourceStation(source);
        booking.setDestinationStation(destination);
        booking.setTravelClass(travelClass);
        booking.setIdempotencyKey("rebooking-" + rebookedFromBookingId + "-" + java.util.UUID.randomUUID());
        booking.setStatus("CONFIRMED"); // no PENDING_PAYMENT step - see method Javadoc
        booking.setHoldExpiresAt(null);
        if (rebookedFromBookingId != null) {
            bookingRepo.findById(rebookedFromBookingId).ifPresent(booking::setRebookedFromBooking);
        }

        BigDecimal totalFare = holdSeatsAndAttachPassengers(booking, schedule.getId(), travelClass, passengers, fareQuote);
        booking.setTotalFare(totalFare);
        booking = bookingRepo.save(booking);

        Pnr pnr = new Pnr();
        pnr.setBooking(booking);
        pnr.setPnrNumber(generatePnrNumber());
        pnrRepo.save(pnr);

        auditService.log(userId, "REBOOKING_COMPLETED", "Booking", booking.getId(),
                "rebookedFrom=" + rebookedFromBookingId + " newSchedule=" + newScheduleId);

        return toDto(booking);
    }

    /** Shared by createBooking and createConfirmedRebooking: hold one seat per passenger and attach them. */
    private BigDecimal holdSeatsAndAttachPassengers(Booking booking, Long scheduleId, String travelClass,
                                                      List<PassengerRequest> passengers, FareQuoteDto fareQuote) {
        BigDecimal totalFare = BigDecimal.ZERO;
        for (PassengerRequest pReq : passengers) {
            Passenger passenger;
            if (pReq.savedPassengerId() != null) {
                passenger = passengerRepo.findByIdAndOwnerUserId(pReq.savedPassengerId(), booking.getUser().getId())
                        .orElseThrow(() -> new com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException(
                                "Saved passenger not found."));
            } else {
                passenger = new Passenger();
                passenger.setFullName(pReq.fullName());
                passenger.setAge(pReq.age());
                passenger.setGender(pReq.gender());
                passenger = passengerRepo.save(passenger);
            }

            SeatAvailability held = holdOneSeat(scheduleId, travelClass, pReq.berthPreference());

            BookingPassenger bp = new BookingPassenger();
            bp.setBooking(booking);
            bp.setPassenger(passenger);
            bp.setSeat(held.getSeat());
            bp.setCoach(held.getSeat().getCoach());
            bp.setBerthPreference(pReq.berthPreference());
            booking.getPassengers().add(bp);

            totalFare = totalFare.add(fareQuote.totalFarePerPassenger());
        }
        return totalFare;
    }

    /**
     * Claims a seat of the given class on the given schedule. If a berth preference is given
     * and at least one AVAILABLE seat of that berth type exists, that's tried first (best
     * effort - a preference is not a guarantee, so this never fails just because the
     * preferred berth type specifically is full while other berths in the same class are
     * open). Falls back to any AVAILABLE seat in the class otherwise. Retries on concurrent-
     * claim conflicts up to a small bound before giving up, which is the correct behaviour
     * under real contention (as opposed to retrying forever or silently overselling).
     */
    private SeatAvailability holdOneSeat(Long scheduleId, String travelClass, String berthPreference) {
        final int maxAttempts = 5;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            List<SeatAvailability> candidates = seatAvailabilityRepo
                    .findByScheduleIdAndSeat_Coach_TravelClassAndStatus(scheduleId, travelClass, "AVAILABLE");
            if (candidates.isEmpty()) {
                throw new SeatUnavailableException("No available seats in class " + travelClass + " for this schedule.");
            }
            SeatAvailability candidate = null;
            if (berthPreference != null && !berthPreference.isBlank()) {
                candidate = candidates.stream()
                        .filter(s -> berthPreference.equalsIgnoreCase(s.getSeat().getBerthType()))
                        .findFirst()
                        .orElse(null);
            }
            if (candidate == null) {
                candidate = candidates.get(0); // preference unset, blank, or unavailable: any open seat in class
            }
            try {
                candidate.setStatus("HELD");
                return seatAvailabilityRepo.saveAndFlush(candidate);
            } catch (OptimisticLockingFailureException ex) {
                // another request claimed this exact seat between our read and write; try the next one
            }
        }
        throw new SeatUnavailableException("Could not secure a seat due to high demand. Please try again.");
    }

    @Transactional
    public BookingDto confirmAfterPayment(Long bookingId) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if ("CONFIRMED".equals(booking.getStatus())) {
            return toDto(booking); // idempotent: already confirmed
        }
        if (!"PENDING_PAYMENT".equals(booking.getStatus())) {
            throw new ConflictException("Booking is not awaiting payment (status: " + booking.getStatus() + ").");
        }

        for (BookingPassenger bp : booking.getPassengers()) {
            SeatAvailability sa = seatAvailabilityRepo.findByScheduleIdAndSeat_Coach_TravelClassAndStatus(
                            booking.getSchedule().getId(), booking.getTravelClass(), "HELD").stream()
                    .filter(s -> s.getSeat().getId().equals(bp.getSeat().getId()))
                    .findFirst()
                    .orElseThrow(() -> new ConflictException("Held seat could not be reconciled at confirmation time."));
            sa.setStatus("BOOKED");
            seatAvailabilityRepo.save(sa);
        }

        booking.setStatus("CONFIRMED");
        booking.setHoldExpiresAt(null);
        bookingRepo.save(booking);

        Pnr pnr = new Pnr();
        pnr.setBooking(booking);
        pnr.setPnrNumber(generatePnrNumber());
        pnrRepo.save(pnr);

        auditService.log(booking.getUser().getId(), "BOOKING_CONFIRMED", "Booking", booking.getId(), pnr.getPnrNumber());

        return toDto(booking);
    }

    @Transactional
    public void releaseHeldSeats(Long bookingId) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        for (BookingPassenger bp : booking.getPassengers()) {
            seatAvailabilityRepo.findByScheduleIdAndSeat_Coach_TravelClassAndStatus(
                            booking.getSchedule().getId(), booking.getTravelClass(), "HELD").stream()
                    .filter(s -> s.getSeat().getId().equals(bp.getSeat().getId()))
                    .forEach(s -> { s.setStatus("AVAILABLE"); seatAvailabilityRepo.save(s); });
        }
        booking.setStatus("EXPIRED");
        booking.setHoldExpiresAt(null);
        bookingRepo.save(booking);
    }

    @Transactional(readOnly = true)
    public List<Long> findExpiredHoldBookingIds() {
        return bookingRepo.findByStatusAndHoldExpiresAtBefore("PENDING_PAYMENT", java.time.Instant.now())
                .stream().map(Booking::getId).toList();
    }

    /** Used by CancellationService after refund calculation: frees the seats and marks the booking CANCELLED. */
    @Transactional
    public Booking cancelConfirmedBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepo.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        return doCancelConfirmedBooking(booking);
    }

    /** Admin-initiated cancellation: same rules, but not ownership-scoped since the admin is
     * acting on a customer's behalf (see AdminBookingController). */
    public Booking adminCancelConfirmedBooking(Long bookingId) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        return doCancelConfirmedBooking(booking);
    }

    private Booking doCancelConfirmedBooking(Booking booking) {
        if (!"CONFIRMED".equals(booking.getStatus())) {
            throw new ConflictException("Only confirmed bookings can be cancelled (status: " + booking.getStatus() + ").");
        }
        for (BookingPassenger bp : booking.getPassengers()) {
            seatAvailabilityRepo.findByScheduleIdAndSeat_Coach_TravelClassAndStatus(
                            booking.getSchedule().getId(), booking.getTravelClass(), "BOOKED").stream()
                    .filter(s -> s.getSeat().getId().equals(bp.getSeat().getId()))
                    .forEach(s -> { s.setStatus("AVAILABLE"); seatAvailabilityRepo.save(s); });
        }
        booking.setStatus("CANCELLED");
        return bookingRepo.save(booking);
    }

    /** Admin-safe fetch: no ownership check, used by AdminBookingController. */
    @Transactional(readOnly = true)
    public Booking getRawEntityById(Long bookingId) {
        return bookingRepo.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    public Booking getRawEntity(Long bookingId, Long userId) {
        return bookingRepo.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    @Transactional(readOnly = true)
    public BookingDto getBooking(Long bookingId, Long userId) {
        Booking booking = bookingRepo.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        return toDto(booking);
    }

    /**
     * Deliberate carve-out of "a user can only see their own booking", for
     * SUPPORT_AGENT/ADMIN use only. This method itself does NOT check the
     * caller's role - that enforcement lives at the controller
     * (@PreAuthorize) so it's visible and centralized, not duplicated here.
     * Every call site MUST audit-log who looked up what, since this is
     * exactly the kind of access that needs a trail. Never call this from
     * anything reachable by a plain USER.
     */
    @Transactional(readOnly = true)
    public BookingDto getBookingForSupportOrAdmin(Long bookingId) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        return toDto(booking);
    }

    /**
     * Plain-user PNR lookup (GET /api/v1/pnr/{pnr}), scoped to the requesting user's own
     * booking. Deliberately returns the same 404 ResourceNotFoundException whether the PNR
     * doesn't exist at all or exists but belongs to someone else, so this endpoint can never
     * be used to enumerate/verify other users' PNRs.
     */
    @Transactional(readOnly = true)
    public BookingDto getBookingByPnr(String pnrNumber, Long userId) {
        Pnr pnr = pnrRepo.findByPnrNumber(pnrNumber)
                .orElseThrow(() -> new ResourceNotFoundException("No booking found for PNR " + pnrNumber));
        Booking booking = pnr.getBooking();
        if (!booking.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("No booking found for PNR " + pnrNumber);
        }
        return toDto(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getUserBookings(Long userId) {
        return bookingRepo.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toDto).toList();
    }

    public Booking getEntityOwnedBy(Long bookingId, Long userId) {
        return bookingRepo.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
    }

    private String generatePnrNumber() {
        String candidate;
        do {
            candidate = String.format("%010d", RANDOM.nextLong(0, 10_000_000_000L));
        } while (pnrRepo.findByPnrNumber(candidate).isPresent());
        return candidate;
    }

    private BookingDto toDto(Booking booking) {
        List<PassengerAllocation> allocations = booking.getPassengers().stream()
                .map(bp -> new PassengerAllocation(
                        bp.getPassenger().getFullName(), bp.getPassenger().getAge(), bp.getPassenger().getGender(),
                        bp.getCoach() != null ? bp.getCoach().getCoachCode() : null,
                        bp.getSeat() != null ? bp.getSeat().getSeatNumber() : null,
                        bp.getSeat() != null ? bp.getSeat().getBerthType() : null))
                .collect(Collectors.toList());

        String pnrNumber = pnrRepo.findByBookingId(booking.getId()).map(Pnr::getPnrNumber).orElse(null);

        return new BookingDto(
                booking.getId(), booking.getSchedule().getId(), booking.getStatus(),
                booking.getSchedule().getTrain().getNumber(), booking.getSchedule().getTrain().getName(),
                booking.getSourceStation().getCode(), booking.getDestinationStation().getCode(),
                booking.getTravelClass(), booking.getTotalFare(), allocations, pnrNumber,
                booking.isTripGuardianEnabled(), booking.getCreatedAt()
        );
    }

    @Transactional
    public BookingDto setTripGuardianEnabled(Long bookingId, Long userId, boolean enabled) {
        Booking booking = bookingRepo.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!"CONFIRMED".equals(booking.getStatus())) {
            throw new ConflictException("Trip Guardian can only be enabled on a confirmed booking.");
        }
        booking.setTripGuardianEnabled(enabled);
        booking = bookingRepo.save(booking);
        auditService.log(userId, enabled ? "TRIP_GUARDIAN_ENABLED" : "TRIP_GUARDIAN_DISABLED", "Booking", bookingId, null);
        return toDto(booking);
    }
}
