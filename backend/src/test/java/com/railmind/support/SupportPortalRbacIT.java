package com.railmind.support;

import com.railmind.audit.AuditLogRepository;
import com.railmind.auth.JwtService;
import com.railmind.auth.User;
import com.railmind.auth.UserRepository;
import com.railmind.booking.BookingEntities.Booking;
import com.railmind.booking.BookingRepositories;
import com.railmind.train.ScheduleEntities.TrainSchedule;
import com.railmind.train.Train;
import com.railmind.train.TrainRepositories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4 support-portal-specific coverage, extending (not duplicating)
 * SupportAndAdminRbacIT's general RBAC boundaries with the features added
 * this phase: customer search, AI action history, and audit-log proof that
 * sensitive lookups actually get recorded, not just permitted.
 *
 * IDOR framing for this controller is deliberately different from a typical
 * "can't see another user's data" test: SUPPORT_AGENT is SUPPOSED to see
 * any customer's booking (that's the whole point of the role). The actual
 * IDOR-shaped risks here are (a) a plain USER reaching support endpoints at
 * all regardless of which ID they guess, and (b) a support agent using
 * customer search to enumerate OTHER STAFF accounts, which they should
 * never be able to do - both are covered below.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class SupportPortalRbacIT {

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
    @Autowired TrainRepositories.TrainRepo trainRepo;
    @Autowired TrainRepositories.TrainScheduleRepo scheduleRepo;
    @Autowired BookingRepositories.BookingRepo bookingRepo;
    @Autowired AuditLogRepository auditLogRepository;

    private String customerToken;
    private String supportToken;
    private String uniqueSuffix;
    private Long customerUserId;
    private Long customerBookingId;

    @BeforeEach
    void setUp() {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        User customer = createUser("USER", "Findable Customer " + uniqueSuffix);
        User otherSupportAgent = createUser("USER,SUPPORT_AGENT", "Hidden Colleague " + uniqueSuffix);
        User support = createUser("USER,SUPPORT_AGENT", "Acting Agent " + uniqueSuffix);

        customerUserId = customer.getId();
        customerToken = jwtService.generateAccessToken(customer);
        supportToken = jwtService.generateAccessToken(support);

        Train train = trainRepo.findByNumber("RM101").orElseThrow();
        TrainSchedule schedule = scheduleRepo.findByTrainIdAndDepartureDate(train.getId(), LocalDate.now().plusDays(6))
                .orElseThrow();

        Booking booking = new Booking();
        booking.setUser(customer);
        booking.setSchedule(schedule);
        booking.setSourceStation(train.getSourceStation());
        booking.setDestinationStation(train.getDestinationStation());
        booking.setTravelClass("SL");
        booking.setStatus("CONFIRMED");
        booking.setTotalFare(BigDecimal.valueOf(500));
        booking.setIdempotencyKey("support-portal-test-" + UUID.randomUUID());
        booking = bookingRepo.save(booking);
        customerBookingId = booking.getId();
    }

    private User createUser(String roles, String fullName) {
        User user = new User();
        user.setFullName(fullName);
        user.setEmail("support-portal-" + UUID.randomUUID() + "@railmind.demo");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRoles(roles);
        return userRepository.save(user);
    }

    @Test
    void plainUser_cannotReachAnySupportEndpoint_regardlessOfWhichIdTheyTry() throws Exception {
        mockMvc.perform(get("/api/v1/support/bookings/" + customerBookingId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/support/customers/search?query=" + uniqueSuffix)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/support/users/" + customerUserId + "/ai-actions")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerSearch_findsCustomers_butNeverOtherStaffAccounts() throws Exception {
        // "Findable Customer" (USER role) must appear; "Hidden Colleague" (SUPPORT_AGENT role,
        // sharing the same unique suffix so a naive search-by-substring would wrongly return it
        // too) must never appear - this is the actual IDOR-shaped risk for this endpoint.
        mockMvc.perform(get("/api/v1/support/customers/search?query=" + uniqueSuffix)
                        .header("Authorization", "Bearer " + supportToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.fullName == 'Findable Customer " + uniqueSuffix + "')]").exists())
                .andExpect(jsonPath("$[?(@.fullName == 'Hidden Colleague " + uniqueSuffix + "')]").doesNotExist());
    }

    @Test
    void viewingABooking_enrichesWithPaymentCancellationDisruptionTimeline_andIsAudited() throws Exception {
        long auditCountBefore = auditLogRepository.count();

        mockMvc.perform(get("/api/v1/support/bookings/" + customerBookingId)
                        .header("Authorization", "Bearer " + supportToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.booking.bookingId").value(customerBookingId))
                .andExpect(jsonPath("$.customerEmail").exists())
                .andExpect(jsonPath("$.timeline").isArray())
                .andExpect(jsonPath("$.timeline[0].label").value("Booking created"));

        assertThat(auditLogRepository.count()).isGreaterThan(auditCountBefore);
        var lastLog = auditLogRepository.findAll().stream()
                .filter(a -> "SUPPORT_VIEWED_BOOKING".equals(a.getAction()))
                .filter(a -> customerBookingId.equals(a.getEntityId()))
                .findFirst();
        assertThat(lastLog).isPresent();
    }

    @Test
    void aiActionsForNonExistentUser_returns404NotPartialData() throws Exception {
        mockMvc.perform(get("/api/v1/support/users/999999999/ai-actions")
                        .header("Authorization", "Bearer " + supportToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void bookingDetailResponse_neverIncludesPasswordOrTokenFields() throws Exception {
        mockMvc.perform(get("/api/v1/support/bookings/" + customerBookingId)
                        .header("Authorization", "Bearer " + supportToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..passwordHash").doesNotExist())
                .andExpect(jsonPath("$..refreshToken").doesNotExist())
                .andExpect(jsonPath("$..token").doesNotExist());
    }
}
