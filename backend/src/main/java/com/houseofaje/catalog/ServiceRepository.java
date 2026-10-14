package com.houseofaje.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ServiceRepository {

    private final JdbcTemplate jdbc;

    public ServiceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<AdminService> ADMIN_MAPPER = (rs, n) -> new AdminService(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("slug"),
            rs.getString("description"),
            rs.getInt("duration_minutes"),
            rs.getBigDecimal("price"),
            rs.getInt("buffer_minutes"),
            rs.getBoolean("active"),
            rs.getObject("category_id", UUID.class),
            rs.getString("category_name"));

    public List<Service> findActive() {
        return jdbc.query("""
                SELECT s.id, s.name, s.slug, s.description, s.duration_minutes, s.price,
                       s.category_id, c.name AS category_name
                FROM services s
                LEFT JOIN service_categories c ON c.id = s.category_id
                WHERE s.active = TRUE
                ORDER BY s.name
                """,
                (rs, n) -> new Service(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getString("slug"),
                        rs.getString("description"),
                        rs.getInt("duration_minutes"),
                        rs.getBigDecimal("price"),
                        rs.getObject("category_id", UUID.class),
                        rs.getString("category_name")));
    }

    public List<AdminService> findAll() {
        return jdbc.query("""
                SELECT s.id, s.name, s.slug, s.description, s.duration_minutes, s.price,
                       s.buffer_minutes, s.active, s.category_id, c.name AS category_name
                FROM services s
                LEFT JOIN service_categories c ON c.id = s.category_id
                ORDER BY s.name
                """, ADMIN_MAPPER);
    }

    public Optional<AdminService> findById(UUID id) {
        return jdbc.query("""
                SELECT s.id, s.name, s.slug, s.description, s.duration_minutes, s.price,
                       s.buffer_minutes, s.active, s.category_id, c.name AS category_name
                FROM services s
                LEFT JOIN service_categories c ON c.id = s.category_id
                WHERE s.id = ?
                """, ADMIN_MAPPER, id).stream().findFirst();
    }

    public boolean slugExists(String slug) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM services WHERE slug = ?", Integer.class, slug);
        return count != null && count > 0;
    }

    public boolean slugExists(String slug, UUID exceptId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM services WHERE slug = ? AND id <> ?", Integer.class, slug, exceptId);
        return count != null && count > 0;
    }

    public void insert(UUID id, String name, String slug, String description,
                       int durationMinutes, BigDecimal price, int bufferMinutes, boolean active,
                       UUID categoryId) {
        jdbc.update("""
                INSERT INTO services (id, name, slug, description, duration_minutes, price, buffer_minutes, active, category_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, name, slug, description, durationMinutes, price, bufferMinutes, active, categoryId);
    }

    public boolean update(UUID id, String name, String slug, String description,
                          int durationMinutes, BigDecimal price, int bufferMinutes, UUID categoryId) {
        return jdbc.update("""
                UPDATE services
                SET name = ?, slug = ?, description = ?, duration_minutes = ?, price = ?, buffer_minutes = ?, category_id = ?
                WHERE id = ?
                """, name, slug, description, durationMinutes, price, bufferMinutes, categoryId, id) == 1;
    }

    public boolean setActive(UUID id, boolean active) {
        return jdbc.update("UPDATE services SET active = ? WHERE id = ?", active, id) == 1;
    }
}
