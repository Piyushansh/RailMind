package com.railmind.admin;

import com.railmind.audit.AuditService;
import com.railmind.auth.CurrentUser;
import com.railmind.common.exceptions.AppExceptions.ConflictException;
import com.railmind.common.exceptions.AppExceptions.ResourceNotFoundException;
import com.railmind.train.Fare;
import com.railmind.train.Train;
import com.railmind.train.TrainRepositories.FareRepo;
import com.railmind.train.TrainRepositories.TrainRepo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/fares")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFareController {

    private final FareRepo fareRepo;
    private final TrainRepo trainRepo;
    private final AuditService auditService;
    private final CurrentUser currentUser;

    public AdminFareController(FareRepo fareRepo, TrainRepo trainRepo, AuditService auditService, CurrentUser currentUser) {
        this.fareRepo = fareRepo;
        this.trainRepo = trainRepo;
        this.auditService = auditService;
        this.currentUser = currentUser;
    }

    public record FareAdminDto(Long id, String trainNumber, String travelClass, BigDecimal baseFare, BigDecimal perKmRate) {
        static FareAdminDto from(Fare f) {
            return new FareAdminDto(f.getId(), f.getTrain().getNumber(), f.getTravelClass(), f.getBaseFare(), f.getPerKmRate());
        }
    }

    public record FareRequest(
            @Pattern(regexp = "SL|AC3|AC2|AC1") String travelClass,
            @NotNull @Min(0) BigDecimal baseFare,
            @NotNull @Min(0) BigDecimal perKmRate
    ) {}

    @GetMapping
    public List<FareAdminDto> listForTrain(@RequestParam Long trainId) {
        return fareRepo.findByTrainId(trainId).stream().map(FareAdminDto::from).toList();
    }

    @PostMapping
    public FareAdminDto create(@RequestParam Long trainId, @Valid @RequestBody FareRequest request, Authentication auth) {
        Train train = trainRepo.findById(trainId).orElseThrow(() -> new ResourceNotFoundException("Train not found"));
        if (fareRepo.findByTrainIdAndTravelClass(trainId, request.travelClass()).isPresent()) {
            throw new ConflictException("A fare for class " + request.travelClass() + " already exists on this train.");
        }
        Fare fare = new Fare();
        fare.setTrain(train);
        fare.setTravelClass(request.travelClass());
        fare.setBaseFare(request.baseFare());
        fare.setPerKmRate(request.perKmRate());
        fare = fareRepo.save(fare);
        auditService.log(currentUser.resolveId(auth), "ADMIN_ADDED_FARE", "Train", trainId,
                "class=" + request.travelClass() + " base=" + request.baseFare());
        return FareAdminDto.from(fare);
    }

    @PutMapping("/{id}")
    public FareAdminDto update(@PathVariable Long id, @Valid @RequestBody FareRequest request, Authentication auth) {
        Fare fare = fareRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Fare not found"));
        fare.setBaseFare(request.baseFare());
        fare.setPerKmRate(request.perKmRate());
        fare = fareRepo.save(fare);
        auditService.log(currentUser.resolveId(auth), "ADMIN_UPDATED_FARE", "Fare", id,
                "base=" + request.baseFare() + " perKm=" + request.perKmRate());
        return FareAdminDto.from(fare);
    }
}
