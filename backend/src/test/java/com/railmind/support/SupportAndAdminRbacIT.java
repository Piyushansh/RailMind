package com.railmind.support;

import com.railmind.auth.JwtService;
import com.railmind.auth.User;
import com.railmind.auth.UserRepository;
import com.railmind.booking.BookingEntities.Booking;
import com.railmind.booking.BookingRepositories;
import com.railmind.station.Station;
import com.railmind.station.StationRepository;
import com.railmind.train.ScheduleEntities.TrainSchedule;
import com.railmind.train.Train;
import com.railmind.train.TrainRepositories;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the RBAC boundaries this project's spec has repeatedly called out
 * explicitly, with real HTTP requests through real Spring Security filters -
 * not just a unit test of an @PreAuthorize annotation existing:
 *
 * - SUPPORT_AGENT can view a booking that belongs to a DIFFERENT user
 *   (the one legitimate cross-user read path in this app).
 * - A plain USER gets 403 on that same support endpoint.
 * - SUPPORT_AGENT gets 403 attempting an ADMIN-only mutation (creating a
 *   station) - support has read access, never admin write access.
 * - ADMIN can perform that same admin mutation successfully.
 *
 * Uses a real Postgres via Testcontainers (same pattern as
 * BookingConcurrencyIT); does not need live Redis/Kafka since the endpoints
 * exercised here don't call Kafka, and Redis cache errors now degrade
 * gracefully (see RedisConfig's CacheErrorHandler) rather than failing the
 * request if Redis happens to be unreachable in this test environment.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class SupportAndAdminRbacIT {

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

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired StationRepository stationRepository;
    @Autowired TrainRepositories.TrainRepo trainRepo;
    @Autowired TrainRepositories.TrainScheduleRepo scheduleRepo;
    @Autowired BookingRepositories.BookingRepo bookingRepo;
    @Autowired ObjectMapper objectMapper;

    private String customerToken;
    private String supportToken;
    private String adminToken;
    private Long someoneElsesBookingId;

    @BeforeEach
    void setUp() {
        // Flyway has already seeded 15 stations and 10 trains (see V2__seed_data.sql).
        User customer = createUser("USER");
        User support = createUser("USER,SUPPORT_AGENT");
        User admin = createUser("USER,ADMIN");

        customerToken = jwtService.generateAccessToken(customer);
        supportToken = jwtService.generateAccessToken(support);
        adminToken = jwtService.generateAccessToken(admin);

        Train train = trainRepo.findByNumber("RM101").orElseThrow();
        TrainSchedule schedule = scheduleRepo.findByTrainIdAndDepartureDate(train.getId(), LocalDate.now().plusDays(5))
                .orElseThrow();

        Booking booking = new Booking();
        booking.setUser(customer);
        booking.setSchedule(schedule);
        booking.setSourceStation(train.getSourceStation());
        booking.setDestinationStation(train.getDestinationStation());
        booking.setTravelClass("SL");
        booking.setStatus("CONFIRMED");
        booking.setTotalFare(BigDecimal.valueOf(500));
        booking.setIdempotencyKey("rbac-test-" + UUID.randomUUID());
        booking = bookingRepo.save(booking);
        someoneElsesBookingId = booking.getId();
    }

    private User createUser(String roles) {
        User user = new User();
        user.setFullName("RBAC Test User");
        user.setEmail("rbac-" + UUID.randomUUID() + "@railmind.demo");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRoles(roles);
        return userRepository.save(user);
    }

    @Test
    void supportAgent_canViewAnotherUsersBooking_butPlainUserCannot() throws Exception {
        mockMvc.perform(get("/api/v1/support/bookings/" + someoneElsesBookingId)
                        .header("Authorization", "Bearer " + supportToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/support/bookings/" + someoneElsesBookingId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void supportAgent_cannotPerformAdminMutation_butAdminCan() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "code", "TST" + UUID.randomUUID().toString().substring(0, 5).toUpperCase(),
                "name", "Test Station", "city", "Test City", "state", "Test State"));

        mockMvc.perform(post("/api/v1/admin/stations")
                        .header("Authorization", "Bearer " + supportToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/stations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
