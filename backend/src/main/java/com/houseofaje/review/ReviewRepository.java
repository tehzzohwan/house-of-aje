package com.houseofaje.review;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewRepository {

    private final JdbcTemplate jdbc;

    public ReviewRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Review> MAPPER = (rs, n) -> new Review(
            rs.getObject("id", UUID.class),
            rs.getObject("booking_id", UUID.class),
            rs.getObject("customer_id", UUID.class),
            rs.getInt("rating"),
            rs.getString("comment"),
            ReviewStatus.valueOf(rs.getString("status")),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    public void insert(Review review) {
        jdbc.update("""
                INSERT INTO reviews (id, booking_id, customer_id, rating, comment, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                review.id(), review.bookingId(), review.customerId(), review.rating(), review.comment(),
                review.status().name());
    }

    public Optional<Review> findById(UUID id) {
        return jdbc.query("SELECT * FROM reviews WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<PublishedReview> findPublishedByService(UUID serviceId, int limit) {
        return jdbc.query("""
                SELECT r.id, c.first_name || ' ' || c.last_name AS customer_name, r.rating, r.comment, r.created_at
                FROM reviews r
                JOIN bookings b ON b.id = r.booking_id
                JOIN customers c ON c.id = r.customer_id
                WHERE b.service_id = ? AND r.status = 'PUBLISHED'
                ORDER BY r.created_at DESC
                LIMIT ?
                """,
                (rs, n) -> new PublishedReview(
                        rs.getObject("id", UUID.class),
                        rs.getString("customer_name"),
                        rs.getInt("rating"),
                        rs.getString("comment"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()),
                serviceId, limit);
    }

    public List<Review> findForModeration(ReviewStatus status, int limit) {
        if (status == null) {
            return jdbc.query("SELECT * FROM reviews ORDER BY created_at DESC LIMIT ?", MAPPER, limit);
        }
        return jdbc.query("SELECT * FROM reviews WHERE status = ? ORDER BY created_at DESC LIMIT ?",
                MAPPER, status.name(), limit);
    }

    public boolean updateStatus(UUID id, ReviewStatus status) {
        return jdbc.update("UPDATE reviews SET status = ? WHERE id = ?", status.name(), id) == 1;
    }
}
