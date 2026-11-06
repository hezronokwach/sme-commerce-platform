package com.hezron.smecommerce.ordering.internal.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * The {@code ordering.order_number_counters} table: one row per shop holding the next order number.
 * <p>
 * Plain SQL through JdbcTemplate instead of an entity: the whole job is two statements on one row, and
 * the important part (the row lock) is easiest to see written out. JdbcTemplate uses the same database
 * connection and transaction as the JPA repositories, so this all happens inside the order's transaction.
 */
@Repository
public class OrderNumberCounters {

    private final JdbcTemplate jdbc;

    OrderNumberCounters(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Gives the shop its counter, starting at 1, unless it already has one. "ON CONFLICT DO NOTHING":
     * if two first orders arrive together, the second insert is skipped instead of failing.
     */
    public void createIfMissing(UUID tenantId, String prefix) {
        jdbc.update("""
                INSERT INTO ordering.order_number_counters (tenant_id, prefix, next_value)
                VALUES (?, ?, 1)
                ON CONFLICT (tenant_id) DO NOTHING
                """, tenantId, prefix);
    }

    /**
     * Takes the shop's next number and adds 1 to the counter, in ONE statement.
     * <p>
     * The UPDATE locks the shop's counter row until the order's transaction ends. If a second order
     * for the same shop arrives meanwhile, its UPDATE waits for that lock, then sees the new value. So
     * two orders can never get the same number; they just queue for a few milliseconds. (Different
     * shops have different rows and never wait for each other.)
     * <p>
     * RETURNING hands back the row as it is after the update, so the number taken is next_value - 1.
     *
     * @return the prefix and the number taken, e.g. {"MM", 123}
     */
    public TakenNumber takeNext(UUID tenantId) {
        List<TakenNumber> taken = jdbc.query("""
                UPDATE ordering.order_number_counters
                SET next_value = next_value + 1
                WHERE tenant_id = ?
                RETURNING prefix, next_value - 1 AS taken
                """,
                new TakenNumberMapper(), tenantId);
        if (taken.isEmpty()) {
            throw new IllegalStateException("No order number counter for shop " + tenantId);
        }
        return taken.get(0);
    }

    /** A number taken from a shop's counter. */
    public record TakenNumber(String prefix, long number) {
    }

    /** Turns one result row into a TakenNumber. */
    private static final class TakenNumberMapper implements RowMapper<TakenNumber> {

        @Override
        public TakenNumber mapRow(ResultSet row, int rowNumber) throws SQLException {
            return new TakenNumber(row.getString("prefix"), row.getLong("taken"));
        }

    }

}
