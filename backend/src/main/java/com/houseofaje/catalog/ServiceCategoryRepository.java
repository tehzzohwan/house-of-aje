package com.houseofaje.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ServiceCategoryRepository {

    private final JdbcTemplate jdbc;

    public ServiceCategoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<ServiceCategory> MAPPER = (rs, n) -> new ServiceCategory(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            rs.getString("slug"),
            rs.getBoolean("active"));

    public List<ServiceCategory> findAll() {
        return jdbc.query("""
                SELECT id, name, slug, active
                FROM service_categories
                ORDER BY name
                """, MAPPER);
    }

    public Optional<ServiceCategory> findById(UUID id) {
        return jdbc.query("""
                SELECT id, name, slug, active
                FROM service_categories
                WHERE id = ?
                """, MAPPER, id).stream().findFirst();
    }

    public boolean slugExists(String slug) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM service_categories WHERE slug = ?", Integer.class, slug);
        return count != null && count > 0;
    }

    public boolean slugExists(String slug, UUID exceptId) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM service_categories WHERE slug = ? AND id <> ?", Integer.class, slug, exceptId);
        return count != null && count > 0;
    }

    public void insert(UUID id, String name, String slug, boolean active) {
        jdbc.update("""
                INSERT INTO service_categories (id, name, slug, active)
                VALUES (?, ?, ?, ?)
                """, id, name, slug, active);
    }

    public boolean update(UUID id, String name, String slug) {
        return jdbc.update("UPDATE service_categories SET name = ?, slug = ? WHERE id = ?", name, slug, id) == 1;
    }

    public boolean setActive(UUID id, boolean active) {
        return jdbc.update("UPDATE service_categories SET active = ? WHERE id = ?", active, id) == 1;
    }
}
