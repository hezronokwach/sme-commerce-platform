package com.hezron.smecommerce.notification.internal.application;

import com.hezron.smecommerce.shared.BaseException;
import org.springframework.http.HttpStatus;

/** The stream was opened with a ticket that is unknown, already used or expired. Returns 401. */
public class InvalidStreamTicketException extends BaseException {

    public InvalidStreamTicketException() {
        super(HttpStatus.UNAUTHORIZED, "STREAM_TICKET_INVALID", "Ticket not valid",
                "This live-status ticket is unknown, already used or expired. Ask for a new one.");
    }

}
