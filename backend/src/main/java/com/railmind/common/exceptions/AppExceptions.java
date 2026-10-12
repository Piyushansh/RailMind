package com.railmind.common.exceptions;

/**
 * Domain exceptions, grouped in one file for brevity. Each maps to a specific
 * HTTP status in GlobalExceptionHandler.
 */
public class AppExceptions {

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) { super(message); }
    }

    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) { super(message); }
    }

    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) { super(message); }
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) { super(message); }
    }

    public static class ForbiddenException extends RuntimeException {
        public ForbiddenException(String message) { super(message); }
    }

    public static class SeatUnavailableException extends RuntimeException {
        public SeatUnavailableException(String message) { super(message); }
    }
}
