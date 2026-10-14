package com.houseofaje.customer;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerRepository {

    private final JdbcTemplate jdbc;

    public CustomerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Customer> MAPPER = (rs, n) -> new Customer(
            rs.getObject("id", UUID.class),
            rs.getString("first_name"),
            rs.getString("last_name"),
            rs.getString("email"),
            rs.getString("phone"),
            rs.getString("username"),
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    public Optional<Customer> findByPhone(String phone) {
        return jdbc.query("SELECT * FROM customers WHERE phone = ? AND deleted_at IS NULL", MAPPER, phone)
                .stream().findFirst();
    }

    public Optional<Customer> findById(UUID id) {
        return jdbc.query("SELECT * FROM customers WHERE id = ? AND deleted_at IS NULL", MAPPER, id)
                .stream().findFirst();
    }

    /** A registered account's login credential, matched by username, email or phone. */
    public Optional<AccountCredential> findCredential(String identifier) {
        return jdbc.query("""
                SELECT id, password_hash
                FROM customers
                WHERE (username = ? OR email = ? OR phone = ?) AND password_hash IS NOT NULL AND deleted_at IS NULL
                """,
                (rs, n) -> new AccountCredential(rs.getObject("id", UUID.class), rs.getString("password_hash")),
                identifier, identifier, identifier).stream().findFirst();
    }

    public boolean registeredEmailExists(String email) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM customers WHERE email = ? AND password_hash IS NOT NULL AND deleted_at IS NULL",
                Integer.class, email);
        return count != null && count > 0;
    }

    public boolean registeredPhoneExists(String phone) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM customers WHERE phone = ? AND password_hash IS NOT NULL AND deleted_at IS NULL",
                Integer.class, phone);
        return count != null && count > 0;
    }

    public boolean registeredUsernameExists(String username) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM customers WHERE username = ? AND deleted_at IS NULL",
                Integer.class, username);
        return count != null && count > 0;
    }

    public void insert(UUID id, String firstName, String lastName, String email, String phone, String username,
            String passwordHash) {
        jdbc.update("""
                INSERT INTO customers (id, first_name, last_name, email, phone, username, password_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, firstName, lastName, email, phone, username, passwordHash);
    }

    /** Promote an existing guest customer (matched by phone) into a registered account. */
    public void promoteToAccount(UUID id, String firstName, String lastName, String email, String username,
            String passwordHash) {
        jdbc.update("""
                UPDATE customers
                SET first_name = ?, last_name = ?, email = COALESCE(?, email),
                    username = COALESCE(?, username), password_hash = ?
                WHERE id = ?
                """, firstName, lastName, email, username, passwordHash, id);
    }

    public record AccountCredential(UUID id, String passwordHash) {}
}
