-- Issue "Admin live order feed": a stream ticket is now for one of two things.
--   ORDER: one order's payment status (customers and staff, issue #17), as before.
--   SHOP:  every order of one shop, for the admin's live orders list. It names no order.
--
-- V70 is already applied everywhere, so it is never edited: this migration changes the table instead.

ALTER TABLE notification.stream_tickets
    ADD COLUMN scope varchar(10) NOT NULL DEFAULT 'ORDER';

ALTER TABLE notification.stream_tickets
    ALTER COLUMN order_id DROP NOT NULL;

-- An ORDER ticket always names its order; a SHOP ticket never does.
ALTER TABLE notification.stream_tickets
    ADD CONSTRAINT ck_stream_tickets_scope CHECK (
        (scope = 'ORDER' AND order_id IS NOT NULL) OR (scope = 'SHOP' AND order_id IS NULL)
    );
