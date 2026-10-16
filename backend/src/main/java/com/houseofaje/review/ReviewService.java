package com.houseofaje.review;

import com.houseofaje.booking.Booking;
import com.houseofaje.booking.BookingNotFoundException;
import com.houseofaje.booking.BookingRepository;
import com.houseofaje.booking.BookingStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Reviews are gated on a COMPLETED booking: the reviewer must be that booking's
 * customer and may only review it once. Moderation hides/unhides, never deletes.
 */
@Service
public class ReviewService {

    private final ReviewRepository reviews;
    private final BookingRepository bookings;

    public ReviewService(ReviewRepository reviews, BookingRepository bookings) {
        this.reviews = reviews;
        this.bookings = bookings;
    }

    public Review create(CreateReviewRequest request) {
        if (request.rating() < 1 || request.rating() > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }
        Booking booking = bookings.findById(request.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(request.bookingId()));
        if (!booking.customerId().equals(request.customerId())) {
            throw new IllegalArgumentException("Booking does not belong to this customer");
        }
        if (booking.status() != BookingStatus.COMPLETED) {
            throw new BookingNotCompletedException(booking.id(), booking.status());
        }

        Review review = new Review(UUID.randomUUID(), booking.id(), booking.customerId(),
                request.rating(), request.comment(), ReviewStatus.PUBLISHED, Instant.now());
        try {
            reviews.insert(review);
        } catch (DataIntegrityViolationException e) {
            throw new ReviewAlreadyExistsException(booking.id());
        }
        return review;
    }

    public List<PublishedReview> publishedByService(UUID serviceId, int limit) {
        return reviews.findPublishedByService(serviceId, limit);
    }

    public ReviewSummary summary(int limit) {
        return new ReviewSummary(
                reviews.findPublishedLatest(limit),
                reviews.averageRating(),
                reviews.countPublished());
    }

    public List<Review> list(ReviewStatus status, int limit) {
        return reviews.findForModeration(status, limit);
    }

    public Review setStatus(UUID id, ReviewStatus status) {
        if (!reviews.updateStatus(id, status)) {
            throw new ReviewNotFoundException(id);
        }
        return reviews.findById(id).orElseThrow(() -> new ReviewNotFoundException(id));
    }
}
