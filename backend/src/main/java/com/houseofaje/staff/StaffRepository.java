package com.houseofaje.staff;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class StaffRepository {

    private final JdbcTemplate jdbc;

    public StaffRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Staff> MAPPER = (rs, n) -> new Staff(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("phone"),
            rs.getString("bio"),
            rs.getString("photo_url"),
            rs.getBoolean("active"));

    public List<Staff> findAll() {
        return jdbc.query("""
                SELECT id, name, email, phone, bio, photo_url, active
                FROM staff
                WHERE deleted_at IS NULL
                ORDER BY name
                """, MAPPER);
    }

    public Optional<Staff> findById(UUID id) {
        return jdbc.query("""
                SELECT id, name, email, phone, bio, photo_url, active
                FROM staff
                WHERE id = ? AND deleted_at IS NULL
                """, MAPPER, id).stream().findFirst();
    }

    /** Active staff who perform the given service, ordered by name. */
    public List<Staff> findPerformingService(UUID serviceId) {
        return jdbc.query("""
                SELECT st.id, st.name, st.email, st.phone, st.bio, st.photo_url, st.active
                FROM staff st
                JOIN staff_services ss ON ss.staff_id = st.id
                WHERE ss.service_id = ? AND st.active = TRUE AND st.deleted_at IS NULL
                ORDER BY st.name
                """, MAPPER, serviceId);
    }

    /** Any active, non-deleted staff row with this email (used for admin uniqueness). */
    public boolean emailExists(String email) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM staff WHERE email = ? AND deleted_at IS NULL", Integer.class, email);
        return count != null && count > 0;
    }

    public boolean emailExists(String email, UUID exceptId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM staff WHERE email = ? AND id <> ? AND deleted_at IS NULL",
                Integer.class, email, exceptId);
        return count != null && count > 0;
    }

    /** Active staff login credentials only: deactivated or deleted staff cannot sign in. */
    public Optional<AccountCredential> findCredential(String email) {
        return jdbc.query("""
                SELECT id, password_hash
                FROM staff
                WHERE email = ? AND password_hash IS NOT NULL AND active = TRUE AND deleted_at IS NULL
                """,
                (rs, n) -> new AccountCredential(rs.getObject("id", UUID.class), rs.getString("password_hash")),
                email).stream().findFirst();
    }

    public void insert(UUID id, String name, String email, String phone, String bio, String photoUrl,
            boolean active, String passwordHash) {
        jdbc.update("""
                INSERT INTO staff (id, name, email, phone, bio, photo_url, active, password_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, name, email, phone, bio, photoUrl, active, passwordHash);
    }

    /** Promote an admin-created staff row into a self-registered account. */
    public boolean promoteToAccount(UUID id, String name, String phone, String passwordHash) {
        return jdbc.update("""
                UPDATE staff
                SET name = COALESCE(?, name), phone = COALESCE(?, phone), password_hash = ?
                WHERE id = ? AND active = TRUE AND deleted_at IS NULL
                """, name, phone, passwordHash, id) == 1;
    }

    public boolean update(UUID id, String name, String email, String phone, String bio, String photoUrl) {
        return jdbc.update("""
                UPDATE staff
                SET name = ?, email = ?, phone = ?, bio = ?, photo_url = ?
                WHERE id = ?
                """, name, email, phone, bio, photoUrl, id) == 1;
    }

    public boolean setActive(UUID id, boolean active) {
        return jdbc.update("UPDATE staff SET active = ? WHERE id = ? AND deleted_at IS NULL", active, id) == 1;
    }

    /** Soft-delete: remove from lists and block login, but keep booking history. */
    public boolean delete(UUID id) {
        return jdbc.update(
                "UPDATE staff SET deleted_at = now(), active = FALSE WHERE id = ? AND deleted_at IS NULL",
                id) == 1;
    }

    public record AccountCredential(UUID id, String passwordHash) {}
}
