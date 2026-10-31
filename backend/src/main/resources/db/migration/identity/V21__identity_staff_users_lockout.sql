-- Login lockout: after too many wrong passwords the account is refused until this time.
-- A new migration instead of editing V20: an applied migration must never change.
ALTER TABLE identity.staff_users ADD COLUMN locked_until timestamptz;
