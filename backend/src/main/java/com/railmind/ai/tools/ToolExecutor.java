package com.railmind.ai.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.railmind.booking.BookingEntities.Booking;
import com.railmind.booking.BookingEntities.Pnr;
import com.railmind.booking.BookingRepositories;
import com.railmind.booking.BookingService;
import com.railmind.booking.dto.BookingDtos.BookingDto;
import com.railmind.cancellation.RefundPolicyService;
import com.railmind.cancellation.RefundPolicyService.RefundQuote;
import com.railmind.common.exceptions.AppExceptions.ForbiddenException;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import com.railmind.common.exceptions.AppExceptions.BadRequestException;
import com.railmind.station.Station;
import com.railmind.station.StationRepository;
import com.railmind.profile.TravelPreference;
import com.railmind.profile.TravelPreferenceRepository;
import com.railmind.rag.PolicyRetrievalService;
import com.railmind.train.TrainSearchService;
import com.railmind.train.dto.TrainDtos.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Executes a single named tool call against the real Spring services. This is
 * the ONLY bridge between the LLM and application data: every method here
 * either reads from TrainSearchService (safe, public data) or enforces
 * userId-scoping before touching a user's own booking. There is no path from
 * an AI tool call to another user's data, and no path that writes to the
 * database except via the deterministic services already used by the REST API.
 */
@Component
public class ToolExecutor {

    private final ObjectMapper mapper = new ObjectMapper();

    private final TrainSearchService trainSearchService;
    private final BookingService bookingService;
    private final BookingRepositories.PnrRepo pnrRepo;
    private final RefundPolicyService refundPolicyService;
    private final StationRepository stationRepository;
    private final com.railmind.train.JourneyRankingService journeyRankingService;
    private final com.railmind.notification.NotificationService notificationService;
    private final com.railmind.disruption.TripAlertRepository tripAlertRepository;
    private final TravelPreferenceRepository travelPreferenceRepository;
    private final PolicyRetrievalService policyRetrievalService;

    public ToolExecutor(TrainSearchService trainSearchService, BookingService bookingService,
                         BookingRepositories.PnrRepo pnrRepo, RefundPolicyService refundPolicyService,
                         StationRepository stationRepository, com.railmind.train.JourneyRankingService journeyRankingService,
                         com.railmind.notification.NotificationService notificationService,
                         com.railmind.disruption.TripAlertRepository tripAlertRepository,
                         TravelPreferenceRepository travelPreferenceRepository,
                         PolicyRetrievalService policyRetrievalService) {
        this.trainSearchService = trainSearchService;
        this.bookingService = bookingService;
        this.pnrRepo = pnrRepo;
        this.refundPolicyService = refundPolicyService;
        this.stationRepository = stationRepository;
        this.journeyRankingService = journeyRankingService;
        this.notificationService = notificationService;
        this.tripAlertRepository = tripAlertRepository;
        this.travelPreferenceRepository = travelPreferenceRepository;
        this.policyRetrievalService = policyRetrievalService;
    }

    /** @return a JSON string result (never a raw exception) - execute() always returns something the LLM can read. */
    public String execute(String toolName, JsonNode input, Long authenticatedUserId) {
        try {
            Object result = switch (toolName) {
                case "searchTrains", "findAlternativeTrains" -> searchTrains(input);
                case "getTrainDetails" -> trainSearchService.getTrainDetails(input.get("trainId").asLong());
                case "checkAvailability" -> trainSearchService.getAvailability(input.get("scheduleId").asLong());
                case "calculateFare" -> trainSearchService.calculateFare(
                        input.get("trainId").asLong(), input.get("travelClass").asText(), input.get("distanceKm").asInt());
                case "getBooking" -> bookingService.getBooking(input.get("bookingId").asLong(), authenticatedUserId);
                case "getPNR" -> getPnr(input.get("pnrNumber").asText(), authenticatedUserId);
                case "calculateRefund" -> calculateRefundQuote(input.get("bookingId").asLong(), authenticatedUserId);
                case "createBookingDraft" -> createBookingDraft(input);
                case "planJourney" -> planJourney(input);
                case "createCancellationDraft" -> createCancellationDraft(input.get("bookingId").asLong(), authenticatedUserId);
                case "getNotifications" -> notificationService.getForUser(authenticatedUserId);
                case "getTripStatus" -> getTripStatus(input.get("bookingId").asLong(), authenticatedUserId);
                case "getUserPreferences" -> getUserPreferences(authenticatedUserId);
                case "policySearch" -> policySearch(input.get("query").asText());
                default -> errorNode("Unknown tool: " + toolName);
            };
            return mapper.writeValueAsString(result);
        } catch (ResourceNotFoundException | ForbiddenException | BadRequestException ex) {
            return errorJson(ex.getMessage());
        } catch (Exception ex) {
            return errorJson("Tool execution failed: could not complete this request.");
        }
    }

    private List<TrainSearchResult> searchTrains(JsonNode input) {
        String source = input.get("source").asText();
        String destination = input.get("destination").asText();
        LocalDate date = LocalDate.parse(input.get("date").asText());
        String travelClass = input.has("travelClass") ? input.get("travelClass").asText() : null;
        String sortBy = input.has("sortBy") ? input.get("sortBy").asText() : "recommended";
        return trainSearchService.search(source, destination, date, travelClass, sortBy);
    }

    private BookingDto getPnr(String pnrNumber, Long authenticatedUserId) {
        Pnr pnr = pnrRepo.findByPnrNumber(pnrNumber)
                .orElseThrow(() -> new ResourceNotFoundException("No booking found for PNR " + pnrNumber));
        Booking booking = pnr.getBooking();
        if (!booking.getUser().getId().equals(authenticatedUserId)) {
            throw new ForbiddenException("This PNR does not belong to the authenticated user.");
        }
        return bookingService.getBooking(booking.getId(), authenticatedUserId);
    }

    /**
     * Builds a non-binding draft priced with REAL fare/availability data. This never writes
     * to the database and never holds a seat - it exists purely so the AI can show the user
     * a structured summary to approve before the user separately calls the real booking API
     * (POST /api/v1/bookings). This is the human-in-the-loop boundary: the AI can propose,
     * only the user's own explicit follow-up action can commit money or seats.
     */
    private ObjectNode createBookingDraft(JsonNode input) {
        Long trainId = input.get("trainId").asLong();
        Long scheduleId = input.get("scheduleId").asLong();
        String travelClass = input.get("travelClass").asText();
        int passengerCount = input.get("passengerCount").asInt();
        if (passengerCount < 1 || passengerCount > 6) {
            throw new BadRequestException("passengerCount must be between 1 and 6.");
        }

        Station source = stationRepository.findByCode(input.get("sourceStationCode").asText())
                .orElseThrow(() -> new ResourceNotFoundException("Unknown source station code."));
        Station destination = stationRepository.findByCode(input.get("destinationStationCode").asText())
                .orElseThrow(() -> new ResourceNotFoundException("Unknown destination station code."));

        TrainSearchService.RouteSpan span = trainSearchService.getRouteSpan(trainId, source.getId(), destination.getId());
        FareQuoteDto fare = trainSearchService.calculateFare(trainId, travelClass, span.distanceKm());
        List<AvailabilityDto> availability = trainSearchService.getAvailability(scheduleId);
        int availableInClass = availability.stream()
                .filter(a -> a.travelClass().equalsIgnoreCase(travelClass))
                .mapToInt(AvailabilityDto::availableSeats)
                .findFirst().orElse(0);

        BigDecimal totalFare = fare.totalFarePerPassenger().multiply(BigDecimal.valueOf(passengerCount));

        ObjectNode node = mapper.createObjectNode();
        node.put("draft", true);
        node.put("scheduleId", scheduleId);
        node.put("trainId", trainId);
        node.put("sourceStationCode", source.getCode());
        node.put("destinationStationCode", destination.getCode());
        node.put("travelClass", travelClass);
        node.put("passengerCount", passengerCount);
        node.put("farePerPassenger", fare.totalFarePerPassenger());
        node.put("totalFare", totalFare);
        node.put("seatsAvailableInClass", availableInClass);
        node.put("canFulfill", availableInClass >= passengerCount);
        node.put("note", "This is a DRAFT only - nothing has been booked, no seat is held, and no payment has been taken. "
                + "To actually book, the user must confirm and call POST /api/v1/bookings themselves.");
        return node;
    }

    private com.railmind.train.dto.JourneyPlanDtos.JourneyPlanResult planJourney(JsonNode input) {
        String source = input.get("source").asText();
        String destination = input.get("destination").asText();
        LocalDate date = LocalDate.parse(input.get("date").asText());
        String travelClass = input.has("travelClass") && !input.get("travelClass").isNull() ? input.get("travelClass").asText() : null;
        BigDecimal maxFare = input.has("maxFare") && !input.get("maxFare").isNull() ? BigDecimal.valueOf(input.get("maxFare").asDouble()) : null;
        return journeyRankingService.plan(source, destination, date, travelClass, maxFare);
    }

    private ObjectNode createCancellationDraft(Long bookingId, Long authenticatedUserId) {
        ObjectNode quote = calculateRefundQuote(bookingId, authenticatedUserId);
        ObjectNode draft = mapper.createObjectNode();
        draft.setAll(quote);
        draft.put("draft", true);
        draft.put("note", "This is a CANCELLATION PREVIEW only - nothing has been cancelled. "
                + "To actually cancel, the user must confirm and call POST /api/v1/cancellations themselves.");
        return draft;
    }

    private ObjectNode getTripStatus(Long bookingId, Long authenticatedUserId) {
        var booking = bookingService.getRawEntity(bookingId, authenticatedUserId); // ownership-scoped
        var schedule = booking.getSchedule();

        ObjectNode node = mapper.createObjectNode();
        node.put("bookingId", bookingId);
        node.put("bookingStatus", booking.getStatus());
        node.put("trainNumber", schedule.getTrain().getNumber());
        node.put("trainName", schedule.getTrain().getName());
        node.put("scheduleStatus", schedule.getStatus());
        node.put("departureDate", schedule.getDepartureDate().toString());
        node.put("departureTime", schedule.getDepartureTime().toString());
        node.put("delayMinutes", schedule.getDelayMinutes());
        node.put("platform", schedule.getPlatform());
        node.put("tripGuardianEnabled", booking.isTripGuardianEnabled());

        var alerts = mapper.createArrayNode();
        tripAlertRepository.findByBookingIdOrderByCreatedAtDesc(bookingId).forEach(a -> {
            ObjectNode alertNode = mapper.createObjectNode();
            alertNode.put("alertId", a.getId());
            alertNode.put("alertType", a.getAlertType());
            alertNode.put("message", a.getMessage());
            alertNode.put("resolved", a.isResolved());
            alertNode.put("hasAlternative", a.getSuggestedAlternativeSchedule() != null);
            alerts.add(alertNode);
        });
        node.set("alerts", alerts);
        return node;
    }

    private ObjectNode calculateRefundQuote(Long bookingId, Long authenticatedUserId) {
        Booking booking = bookingService.getRawEntity(bookingId, authenticatedUserId);
        LocalDateTime departure = LocalDateTime.of(
                booking.getSchedule().getDepartureDate(), booking.getSchedule().getDepartureTime());
        double hoursBefore = Duration.between(LocalDateTime.now(), departure).toMinutes() / 60.0;
        RefundQuote quote = refundPolicyService.calculate(booking.getTotalFare(), hoursBefore);

        ObjectNode node = mapper.createObjectNode();
        node.put("bookingId", bookingId);
        node.put("bookingStatus", booking.getStatus());
        node.put("originalFare", quote.originalFare());
        node.put("deductionPercent", quote.deductionPercent());
        node.put("estimatedRefundAmount", quote.refundAmount());
        node.put("explanation", quote.explanation());
        node.put("note", "This is a quote only. Cancelling requires the user's explicit confirmation via POST /api/v1/cancellations.");
        return node;
    }

    private ObjectNode getUserPreferences(Long authenticatedUserId) {
        ObjectNode node = mapper.createObjectNode();
        TravelPreference pref = travelPreferenceRepository.findByUserId(authenticatedUserId).orElse(null);
        if (pref == null) {
            node.put("hasPreferences", false);
            node.put("note", "User has not set any travel preferences yet - ask them, or use sensible defaults.");
            return node;
        }
        node.put("hasPreferences", true);
        node.put("preferredClass", pref.getPreferredClass());
        node.put("preferredBerth", pref.getPreferredBerth());
        node.put("budgetMax", pref.getBudgetMax());
        node.put("preferFastest", pref.isPreferFastest());
        node.put("preferCheapest", pref.isPreferCheapest());
        node.put("preferOvernight", pref.isPreferOvernight());
        node.put("tripGuardianDefaultEnabled", pref.isTripGuardianDefaultEnabled());
        return node;
    }

    private ObjectNode policySearch(String query) {
        List<PolicyRetrievalService.RetrievedChunk> chunks = policyRetrievalService.retrieve(query);
        ObjectNode node = mapper.createObjectNode();
        if (chunks.isEmpty()) {
            node.put("sufficientInformation", false);
            node.put("note", "No policy excerpt cleared the similarity threshold. Do not answer from general "
                    + "knowledge - tell the user you don't have enough verified information on this.");
            return node;
        }
        node.put("sufficientInformation", true);
        var results = mapper.createArrayNode();
        for (var chunk : chunks) {
            ObjectNode c = mapper.createObjectNode();
            c.put("chunkId", chunk.chunkId());
            c.put("documentName", chunk.documentName());
            c.put("category", chunk.category().name());
            if (chunk.section() != null) c.put("section", chunk.section());
            if (chunk.page() != null) c.put("page", chunk.page());
            c.put("similarity", chunk.similarity());
            c.put("content", chunk.content());
            results.add(c);
        }
        node.set("results", results);
        node.put("note", "Base your answer ONLY on these excerpts. Cite the document name for each fact you use.");
        return node;
    }

    private ObjectNode errorNode(String message) {
        ObjectNode node = mapper.createObjectNode();
        node.put("error", message);
        return node;
    }

    private String errorJson(String message) {
        try {
            return mapper.writeValueAsString(errorNode(message));
        } catch (Exception e) {
            return "{\"error\":\"" + message + "\"}";
        }
    }
}
