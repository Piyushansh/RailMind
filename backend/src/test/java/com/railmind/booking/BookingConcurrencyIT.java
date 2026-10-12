package com.railmind.booking;

import com.railmind.auth.User;
import com.railmind.auth.UserRepository;
import com.railmind.booking.dto.BookingDtos.CreateBookingRequest;
import com.railmind.booking.dto.BookingDtos.PassengerRequest;
import com.railmind.station.StationRepository;
import com.railmind.train.ScheduleEntities.TrainSchedule;
import com.railmind.train.TrainRepositories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the core safety property of the booking engine: when many concurrent
 * requests race for a class that has exactly one seat left, exactly one of
 * them wins and the rest fail with SeatUnavailableException - never two
 * bookings for the same physical seat. Requires Docker to be available (it
 * spins up a real Postgres via Testcontainers, since the schema uses
 * Postgres-specific SQL that H2 cannot run).
 */
@Testcontainers
@SpringBootTest
class BookingConcurrencyIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("railmind").withUsername("railmind").withPassword("railmind");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("railmind.jwt.secret", () -> "test-only-secret-test-only-secret-32b");
    }

    @Autowired UserRepository userRepository;
    @Autowired StationRepository stationRepository;
    @Autowired TrainRepositories.TrainRepo trainRepo;
    @Autowired TrainRepositories.TrainScheduleRepo scheduleRepo;
    @Autowired TrainRepositories.SeatAvailabilityRepo seatAvailabilityRepo;
    @Autowired BookingService bookingService;

    private Long userId;
    private Long scheduleId;
    private Long sourceStationId;
    private Long destinationStationId;

    @BeforeEach
    void setUp() {
        // Flyway has already seeded 15 stations, 10 trains and 30 days of schedules (see V2__seed_data.sql).
        User user = new User();
        user.setFullName("Concurrency Test User");
        user.setEmail("concurrency-" + UUID.randomUUID() + "@railmind.demo");
        user.setPasswordHash("$2b$10$abcdefghijklmnopqrstuv"); // never authenticated through in this test
        user = userRepository.save(user);
        userId = user.getId();

        var train = trainRepo.findByNumber("RM101").orElseThrow();
        var schedule = scheduleRepo.findByTrainIdAndDepartureDate(train.getId(), LocalDate.now().plusDays(5)).orElseThrow();
        scheduleId = schedule.getId();
        sourceStationId = train.getSourceStation().getId();
        destinationStationId = train.getDestinationStation().getId();

        // Drain AC1 down to exactly one available seat so every concurrent request targets the same seat pool.
        var available = seatAvailabilityRepo.findByScheduleIdAndSeat_Coach_TravelClassAndStatus(scheduleId, "AC1", "AVAILABLE");
        for (int i = 1; i < available.size(); i++) {
            var sa = available.get(i);
            sa.setStatus("BOOKED");
            seatAvailabilityRepo.save(sa);
        }
    }

    @Test
    void onlyOneConcurrentRequestWinsTheLastSeat() throws InterruptedException {
        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    go.await();
                    CreateBookingRequest req = new CreateBookingRequest(
                            scheduleId, sourceStationId, destinationStationId, "AC1",
                            List.of(new PassengerRequest("Test Passenger", 30, "M", "LOWER", null)),
                            UUID.randomUUID().toString());
                    bookingService.createBooking(userId, req);
                    successCount.incrementAndGet();
                } catch (Exception ex) {
                    failureCount.incrementAndGet();
                } finally {
                    // no-op
                }
            });
        }

        ready.await();
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(attempts - 1);

        long stillAvailable = seatAvailabilityRepo.countByScheduleIdAndSeat_Coach_TravelClassAndStatus(scheduleId, "AC1", "AVAILABLE");
        assertThat(stillAvailable).isZero();
    }
}
