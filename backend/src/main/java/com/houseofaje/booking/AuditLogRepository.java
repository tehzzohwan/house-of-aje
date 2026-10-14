package com.houseofaje.booking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Append-only audit trail for sensitive mutations (cancellations, refunds, …). */
@Repository
public class AuditLogRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditLogRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void log(String entity, String entityId, String action, String actor,
                    Map<String, ?> before, Map<String, ?> after) {
        jdbc.update("INSERT INTO audit_log (entity, entity_id, action, actor, before, after) VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)",
                entity, entityId, action, actor, toJson(before), toJson(after));
    }

    private String toJson(Map<String, ?> value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize audit payload", e);
        }
    }
}
