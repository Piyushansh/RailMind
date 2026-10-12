package com.railmind.booking;

import com.railmind.auth.CurrentUser;
import com.railmind.booking.dto.BookingDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final CurrentUser currentUser;

    public BookingController(BookingService bookingService, CurrentUser currentUser) {
        this.bookingService = bookingService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<BookingDto> create(@Valid @RequestBody CreateBookingRequest request, Authentication auth) {
        Long userId = currentUser.resolveId(auth);
        BookingDto created = bookingService.createBooking(userId, request);
        return ResponseEntity
                .created(java.net.URI.create("/api/v1/bookings/" + created.bookingId()))
                .body(created);
    }

    @GetMapping("/{id}")
    public BookingDto get(@PathVariable Long id, Authentication auth) {
        return bookingService.getBooking(id, currentUser.resolveId(auth));
    }

    @GetMapping
    public List<BookingDto> myBookings(Authentication auth) {
        return bookingService.getUserBookings(currentUser.resolveId(auth));
    }
}
