package com.railmind.booking;

import com.railmind.auth.CurrentUser;
import com.railmind.booking.dto.SavedPassengerDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/passengers")
public class SavedPassengerController {

    private final SavedPassengerService service;
    private final CurrentUser currentUser;

    public SavedPassengerController(SavedPassengerService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<SavedPassengerDto> list(Authentication auth) {
        return service.list(currentUser.resolveId(auth));
    }

    @PostMapping
    public ResponseEntity<SavedPassengerDto> create(@Valid @RequestBody CreateSavedPassengerRequest request, Authentication auth) {
        return ResponseEntity.ok(service.create(currentUser.resolveId(auth), request));
    }

    @PutMapping("/{id}")
    public SavedPassengerDto update(@PathVariable Long id, @Valid @RequestBody UpdateSavedPassengerRequest request,
                                     Authentication auth) {
        return service.update(currentUser.resolveId(auth), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        service.delete(currentUser.resolveId(auth), id);
        return ResponseEntity.noContent().build();
    }
}
