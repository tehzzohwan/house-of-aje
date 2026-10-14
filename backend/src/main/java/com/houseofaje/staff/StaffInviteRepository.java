package com.houseofaje.staff;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class StaffInviteRepository {

    private final JdbcTemplate jdbc;

    public StaffInviteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<StaffInvite> MAPPER = (rs, n) -> new StaffInvite(
            rs.getObject("id", UUID.class),
            rs.getObject("staff_id", UUID.class),
            rs.getString("token"),
            rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
            rs.getObject("used_at", OffsetDateTime.class) == null
                    ? null : rs.getObject("used_at", OffsetDateTime.class).toInstant(),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    public void insert(UUID id, UUID staffId, String token, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO staff_invites (id, staff_id, token, expires_at)
                VALUES (?, ?, ?, ?)
                """, id, staffId, token, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
    }

    public Optional<StaffInvite> findValidByToken(String token) {
        return jdbc.query(
                "SELECT * FROM staff_invites WHERE token = ? AND used_at IS NULL AND expires_at > now()",
                MAPPER, token).stream().findFirst();
    }

    /** A still-unused, unexpired invite for a staff member, if one exists. */
    public Optional<StaffInvite> findValidByStaff(UUID staffId) {
        return jdbc.query(
                "SELECT * FROM staff_invites WHERE staff_id = ? AND used_at IS NULL AND expires_at > now()",
                MAPPER, staffId).stream().findFirst();
    }

    public boolean markUsed(UUID id, Instant usedAt) {
        return jdbc.update("UPDATE staff_invites SET used_at = ? WHERE id = ? AND used_at IS NULL",
                OffsetDateTime.ofInstant(usedAt, ZoneOffset.UTC), id) == 1;
    }
}
