package com.hezron.smecommerce.ordering.internal.domain;

import com.hezron.smecommerce.shared.BaseException;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * An order was asked to move to a status it may not reach from where it is, for example paying a
 * cancelled order or fulfilling an unpaid one. Returns 409.
 */
@Getter
public class IllegalOrderTransitionException extends BaseException {

    private final OrderStatus from;
    private final OrderStatus to;

    public IllegalOrderTransitionException(OrderStatus from, OrderStatus to) {
        super(HttpStatus.CONFLICT, "ILLEGAL_ORDER_TRANSITION", "Order can't change like that",
                "An order that is " + from + " can't become " + to + ".");
        this.from = from;
        this.to = to;
    }

}
