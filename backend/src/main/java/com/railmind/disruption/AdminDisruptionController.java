package com.railmind.disruption;

import com.railmind.audit.AuditService;
import com.railmind.auth.CurrentUser;
import com.railmind.disruption.dto.DisruptionDtos.SimulateEventRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only trigger for the disruption workflow demo: publishes a real
 * Kafka event that TrainEventConsumer picks up asynchronously. This endpoint
 * does NOT update the schedule or notify anyone directly - it only
 * publishes; everything downstream happens through the same Kafka-driven
 * path a real upstream railway-data feed would use.
 *
 * Protected both by the path rule in SecurityConfig (/api/v1/admin/** ->
 * hasRole("ADMIN")) and by an explicit @PreAuthorize here, matching the
 * defense-in-depth pattern used by every other admin controller in this
 * codebase (AdminStationController, AdminTrainController, AnalyticsController).
 */
@RestController
@RequestMapping({"/api/v1/admin/disruptions", "/api/v1/admin/simulations"})
@PreAuthorize("hasRole('ADMIN')")
public class AdminDisruptionController {

    private final TrainEventProducer producer;
    private final CurrentUser currentUser;
    private final AuditService auditService;

    public AdminDisruptionController(TrainEventProducer producer, CurrentUser currentUser, AuditService auditService) {
        this.producer = producer;
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    @PostMapping("/simulate")
    public ResponseEntity<Void> simulate(@Valid @RequestBody SimulateEventRequest request, Authentication auth) {
        Long adminUserId = currentUser.resolveId(auth);
        String newDepartureTime = request.newDepartureTime() != null ? request.newDepartureTime().toString() : null;
        producer.publish(TrainEventMessage.newEvent(
                request.eventType(), request.scheduleId(), request.delayMinutes(),
                request.newPlatform(), newDepartureTime, adminUserId));

        // This simulates a real upstream disruption and triggers a real downstream workflow
        // (rebooking, refunds, notifications) - it belongs in the audit trail the same way any
        // other sensitive admin action does, independent of whatever the Kafka event itself carries.
        auditService.log(adminUserId, "ADMIN_SIMULATED_DISRUPTION", "TrainSchedule", request.scheduleId(),
                "eventType=" + request.eventType() + " delayMinutes=" + request.delayMinutes()
                        + " newPlatform=" + request.newPlatform());

        return ResponseEntity.accepted().build();
    }
}
