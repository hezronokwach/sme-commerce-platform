package com.hezron.smecommerce.notification.internal.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * No tenant in these queries: a ticket is looked up by its hash before we know the shop. The hash is
 * the secret itself (random 32 bytes), so it can only ever match the one row it was made for, and the
 * row then says which shop and order it is for.
 */
public interface StreamTicketRepository extends JpaRepository<StreamTicketEntity, String> {

    /**
     * Uses the ticket up, in ONE statement: it only changes the row if the ticket is unused and not
     * expired. Two tabs opening the stream with the same ticket at the same moment: Postgres runs the
     * two UPDATEs one after the other, the first changes 1 row, the second finds used_at already set
     * and changes 0. So a ticket works exactly once, with no check-then-act gap in between.
     *
     * <p>
     * The scope is part of the WHERE too: a SHOP ticket can't open an order's stream, and an ORDER ticket
     * can't open the shop feed. A ticket of the wrong kind isn't even used up.
     *
     * @return 1 if the ticket was valid and is now used, 0 otherwise
     */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE notification.stream_tickets
            SET used_at = :now
            WHERE ticket_hash = :ticketHash AND scope = :scope AND used_at IS NULL AND expires_at > :now
            """)
    int useUp(@Param("ticketHash") String ticketHash, @Param("scope") String scope, @Param("now") Instant now);

    /** For the daily clean-up. */
    @Modifying
    @Query(nativeQuery = true, value = "DELETE FROM notification.stream_tickets WHERE expires_at < :before")
    int deleteExpiredBefore(@Param("before") Instant before);

}
