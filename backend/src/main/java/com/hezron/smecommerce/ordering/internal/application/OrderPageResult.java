package com.hezron.smecommerce.ordering.internal.application;

import java.util.List;

/**
 * One page of the order list.
 *
 * @param page       which page this is, counting from 0
 * @param size       how many orders a full page has
 * @param totalItems how many orders match in all pages together
 */
public record OrderPageResult(List<OrderSummaryResult> items, int page, int size, long totalItems) {
}
