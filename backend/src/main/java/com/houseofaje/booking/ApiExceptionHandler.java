package com.houseofaje.booking;

import com.houseofaje.catalog.ServiceCategoryNotFoundException;
import com.houseofaje.catalog.ServiceNotFoundException;
import com.houseofaje.payment.InvalidWebhookSignatureException;
import com.houseofaje.review.BookingNotCompletedException;
import com.houseofaje.review.ReviewAlreadyExistsException;
import com.houseofaje.review.ReviewNotFoundException;
import com.houseofaje.staff.StaffNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidWebhookSignatureException.class)
    ResponseEntity<ApiError> unauthorized(InvalidWebhookSignatureException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiError> badCredentials(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler({SlotUnavailableException.class, InvalidStateTransitionException.class,
            BookingNotCompletedException.class, ReviewAlreadyExistsException.class})
    ResponseEntity<ApiError> conflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler({BookingNotFoundException.class, ReviewNotFoundException.class,
            ServiceCategoryNotFoundException.class, ServiceNotFoundException.class,
            StaffNotFoundException.class})
    ResponseEntity<ApiError> notFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("Request conflicts with existing data"));
    }

    record ApiError(String message) {}
}
