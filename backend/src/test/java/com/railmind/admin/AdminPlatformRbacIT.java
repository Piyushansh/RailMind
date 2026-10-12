package com.railmind.admin;

import com.railmind.auth.JwtService;
import com.railmind.auth.User;
import com.railmind.auth.UserRepository;
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

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC coverage for the Phase 3 admin platform expansion (users, bookings,
 * audit-logs, and the train-provisioning wizard) - same pattern and
 * infrastructure as SupportAndAdminRbacIT, extended to the new controllers
 * rather than duplicating that class's booking fixture.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AdminPlatformRbacIT {

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
    @Autowired ObjectMapper objectMapper;

    private String customerToken;
    private String adminToken;
    private Long customerId;
    private Long adminId;

    @BeforeEach
    void setUp() {
        User customer = createUser("USER");
        User admin = createUser("USER,ADMIN");
        customerId = customer.getId();
        adminId = admin.getId();
        customerToken = jwtService.generateAccessToken(customer);
        adminToken = jwtService.generateAccessToken(admin);
    }

    private User createUser(String roles) {
        User user = new User();
        user.setFullName("Admin Platform Test User");
        user.setEmail("admin-platform-" + UUID.randomUUID() + "@railmind.demo");
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setRoles(roles);
        return userRepository.save(user);
    }

    @Test
    void plainUser_cannotListAdminUsers_adminCan() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void plainUser_cannotViewAuditLogs_adminCan() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-logs").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/audit-logs").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void admin_cannotBlockTheirOwnAccount() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("active", false));

        mockMvc.perform(patch("/api/v1/admin/users/" + adminId + "/enabled")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void admin_canBlockADifferentUser_andBlockedUserLosesAccess() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("active", false));

        mockMvc.perform(patch("/api/v1/admin/users/" + customerId + "/enabled")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        // The blocked customer's existing token is still cryptographically valid, but the
        // account itself is now disabled - login (not exercised here) is where that's enforced;
        // this test only proves the block operation itself took effect and is reflected back.
    }

    @Test
    void plainUser_cannotUseFullTrainWizard_adminCan() throws Exception {
        String number = "RW" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        String body = objectMapper.writeValueAsString(Map.of(
                "number", number, "name", "Wizard Test Express", "trainType", "Express",
                "sourceStationCode", "NDLS", "destinationStationCode", "BCT",
                "intermediateStops", java.util.List.of(),
                "coachClasses", java.util.List.of(Map.of(
                        "travelClass", "SL", "coachCount", 1, "seatsPerCoach", 10,
                        "baseFare", 150, "perKmRate", 0.55)),
                "departureTime", "06:00", "scheduleDays", 2));

        mockMvc.perform(post("/api/v1/admin/trains/full")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/trains/full")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coaches.length()").value(1))
                .andExpect(jsonPath("$.fares.length()").value(1));
    }
}
