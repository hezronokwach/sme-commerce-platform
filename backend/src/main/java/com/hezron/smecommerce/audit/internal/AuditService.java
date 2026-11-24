package com.hezron.smecommerce.audit.internal;

import com.hezron.smecommerce.audit.AuditApi;
import com.hezron.smecommerce.shared.TenantId;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

/** Writes audit.audit_log. The actor comes from the checked token, never from what a caller claims. */
@Service
class AuditService implements AuditApi {

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    AuditService(JdbcTemplate jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void record(TenantId tenantId, String action, String entityType, UUID entityId,
            Map<String, Object> details) {
        UUID actorId = currentStaffId();
        String actorType = "SYSTEM";
        if (actorId != null) {
            actorType = "STAFF";
        }
        jdbc.update("""
                INSERT INTO audit.audit_log
                    (id, tenant_id, actor_type, actor_id, action, entity_type, entity_id, details, ip_address)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """, UUID.randomUUID(), tenantId.value(), actorType, actorId, action, entityType, entityId,
                jsonMapper.writeValueAsString(details), currentIpAddress());
    }

    private static UUID currentStaffId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        return null;
    }

    private static String currentIpAddress() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servlet) {
            HttpServletRequest request = servlet.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }

}
