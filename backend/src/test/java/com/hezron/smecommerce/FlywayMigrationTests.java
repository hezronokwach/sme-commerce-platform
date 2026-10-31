package com.hezron.smecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Day 3 "done when": a clean database migrates from nothing on startup, and a second run changes nothing.
 * The Testcontainers database starts empty, so the context startup is the first run.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class FlywayMigrationTests {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void cleanDatabaseMigratesOnStartup() {
        List<String> applied = Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .map(Object::toString)
                .toList();

        assertThat(applied).contains("1", "10", "20");
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void secondRunChangesNothing() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void createsOneSchemaPerModule() {
        List<String> schemas = jdbc.queryForList(
                "SELECT schema_name FROM information_schema.schemata", String.class);

        assertThat(schemas).contains("tenant", "identity", "catalog", "inventory", "ordering",
                "payments", "notification", "assistant", "audit");
    }

    @Test
    void createsTenantAndIdentityTablesInTheirOwnSchemas() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_schema || '.' || table_name
                FROM information_schema.tables
                WHERE table_schema IN ('tenant', 'identity')
                """, String.class);

        assertThat(tables).containsExactlyInAnyOrder(
                "tenant.tenants", "tenant.tenant_branding", "tenant.tenant_payment_providers",
                "identity.staff_users", "identity.customers", "identity.refresh_tokens");
    }

}
