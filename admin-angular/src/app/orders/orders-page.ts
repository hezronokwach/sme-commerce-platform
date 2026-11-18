import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { Api } from '../api/api';
import { ApiConfiguration } from '../api/api-configuration';
import { streamTicketForShopFeed } from '../api/fn/stream-ticket/stream-ticket-for-shop-feed';
import { adminOrderList } from '../api/fn/admin-order/admin-order-list';
import { OrderPageResponse } from '../api/models/order-page-response';
import { formatPrice } from '../products/format-price';
import { messageFrom } from '../shared/error-message';
import { ORDER_STATUSES, OrderStatus, formatDateTime, statusLabel } from './order-labels';
import { textOrEmpty } from '../shared/text-or-empty';
import { FeedMessage, FeedState, OrderFeed } from './order-feed';

const PAGE_SIZE = 20;

/** How long a just-placed order stays highlighted in the list. */
const HIGHLIGHT_MILLIS = 6000;

/** One table row: what the list shows for an order. */
interface OrderListRow {
  id: string;
  orderNumber: string;
  customerName: string;
  status: string;
  statusLabel: string;
  total: string;
  placedAt: string;
}

/**
 * The shop's orders at /orders, newest first, 20 per page, optionally only one status.
 *
 * The filter and the page are kept in the address (/orders?status=PAID&page=2). So when you open an
 * order and press the browser's Back button, you land on the same page with the same filter, and
 * you can send someone a link to "all paid orders".
 *
 * The list is live (ADR 0012): while the page is open it listens to the shop's order feed, and when an
 * order is placed or changes status it quietly loads the list again and highlights the new order.
 */
@Component({
  selector: 'app-orders-page',
  imports: [FormsModule, RouterLink],
  templateUrl: './orders-page.html',
  styleUrl: './orders-page.css',
})
export class OrdersPage implements OnInit {
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly apiConfiguration = inject(ApiConfiguration);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly statuses = ORDER_STATUSES;
  protected readonly statusLabel = statusLabel;

  /** Bound to the filter dropdown. '' means every status. */
  protected statusFilter = '';
  /** Counting from 0, like the API. The screen shows page + 1. */
  protected readonly page = signal(0);
  protected readonly totalPages = signal(1);
  protected readonly totalItems = signal(0);

  protected readonly rows = signal<OrderListRow[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  /** The live feed: connecting, live, or reconnecting after a dropped connection. */
  protected readonly feedState = signal<FeedState>('connecting');
  /** Ids of orders placed while the page was open, highlighted for a few seconds. */
  protected readonly highlighted = signal<string[]>([]);

  /**
   * The address is the one place the filter and page live. Every change to it, whether from our
   * buttons, the Back button or a pasted link, ends up here and loads that list.
   *
   * Why subscribe instead of reading route.snapshot once? When only the "?..." part of the address
   * changes (Back from ?status=PAID to plain /orders), Angular keeps this same component and does not
   * run ngOnInit again. queryParamMap is a stream that tells us about each change. Angular ends the
   * stream itself when the page closes, so there is nothing to clean up.
   */
  ngOnInit(): void {
    this.route.queryParamMap.subscribe((query: ParamMap) => {
      this.readAddress(query);
      this.load(true);
    });
    this.startFeed();
  }

  /** Starts listening to the shop's live order feed, and stops when the page closes. */
  private startFeed(): void {
    const page = this;
    const feed = new OrderFeed({
      getStreamUrl: async function () {
        const ticket = await page.api.invoke(streamTicketForShopFeed, {});
        return textOrEmpty(ticket.streamUrl);
      },
      rootUrl: this.apiConfiguration.rootUrl,
      onMessage: function (message: FeedMessage) {
        page.orderNews(message);
      },
      onStateChange: function (state: FeedState) {
        page.feedState.set(state);
      },
    });
    feed.start();
    this.destroyRef.onDestroy(function () {
      feed.stop();
    });
  }

  /**
   * Something happened to one of the shop's orders. The list is loaded again (quietly, with no
   * "Loading…"), so its filter, pages and names stay exactly what the server says. A new order is
   * highlighted for a few seconds.
   */
  private orderNews(message: FeedMessage): void {
    if (message.type === 'connected') {
      return;
    }
    if (message.type === 'order-placed' && message.orderId !== null) {
      this.highlight(message.orderId);
    }
    this.load(false);
  }

  private highlight(orderId: string): void {
    this.highlighted.update(function (ids) {
      return ids.concat([orderId]);
    });
    const page = this;
    setTimeout(function () {
      page.highlighted.update(function (ids) {
        return ids.filter(function (id) {
          return id !== orderId;
        });
      });
    }, HIGHLIGHT_MILLIS);
  }

  protected isHighlighted(orderId: string): boolean {
    return this.highlighted().includes(orderId);
  }

  /** The dropdown changed: start again at the first page. */
  protected filterChanged(): void {
    this.goTo(this.statusFilter, 0);
  }

  protected previousPage(): void {
    if (this.page() > 0) {
      this.goTo(this.statusFilter, this.page() - 1);
    }
  }

  protected nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.goTo(this.statusFilter, this.page() + 1);
    }
  }

  /** Only changes the address; the subscription in ngOnInit then loads the list. */
  private goTo(status: string, page: number): void {
    const queryParams: { status?: string; page?: number } = {};
    if (status !== '') {
      queryParams.status = status;
    }
    if (page > 0) {
      queryParams.page = page + 1;
    }
    this.router.navigate([], { relativeTo: this.route, queryParams: queryParams });
  }

  /** ?status=PAID&page=2 becomes statusFilter 'PAID' and page 1. Anything unreadable means the default. */
  private readAddress(query: ParamMap): void {
    this.statusFilter = '';
    const status = query.get('status');
    if (status !== null && isOrderStatus(status)) {
      this.statusFilter = status;
    }

    this.page.set(0);
    const pageText = query.get('page');
    if (pageText !== null) {
      const pageNumber = Number(pageText);
      if (Number.isInteger(pageNumber) && pageNumber >= 1) {
        this.page.set(pageNumber - 1);
      }
    }
  }

  /** @param showLoading false for the live feed's quiet reloads: the table stays on screen meanwhile. */
  private async load(showLoading: boolean): Promise<void> {
    if (showLoading) {
      this.loading.set(true);
    }
    this.errorMessage.set(null);
    try {
      let status: OrderStatus | undefined = undefined;
      if (isOrderStatus(this.statusFilter)) {
        status = this.statusFilter;
      }
      const answer = await this.api.invoke(adminOrderList, { status: status, page: this.page(), size: PAGE_SIZE });
      this.rows.set(buildOrderRows(answer));
      this.totalItems.set(numberOrZero(answer.totalItems));
      this.totalPages.set(countPages(numberOrZero(answer.totalItems), PAGE_SIZE));
    } catch (error) {
      this.errorMessage.set(messageFrom(error, 'The orders could not be loaded. Please reload the page.'));
    } finally {
      this.loading.set(false);
    }
  }
}

export function buildOrderRows(answer: OrderPageResponse): OrderListRow[] {
  const rows: OrderListRow[] = [];
  if (answer.items === undefined) {
    return rows;
  }
  for (const order of answer.items) {
    rows.push({
      id: textOrEmpty(order.id),
      orderNumber: textOrEmpty(order.orderNumber),
      customerName: textOrEmpty(order.customerName),
      status: textOrEmpty(order.status),
      statusLabel: statusLabel(order.status),
      total: formatPrice(order.totalMinor, order.currency),
      placedAt: formatDateTime(order.createdAt),
    });
  }
  return rows;
}

/** 0 orders is still one (empty) page; 41 orders at 20 a page is 3 pages. */
export function countPages(totalItems: number, pageSize: number): number {
  if (totalItems === 0) {
    return 1;
  }
  return Math.ceil(totalItems / pageSize);
}

function isOrderStatus(text: string): text is OrderStatus {
  for (const status of ORDER_STATUSES) {
    if (status === text) {
      return true;
    }
  }
  return false;
}

function numberOrZero(value: number | undefined): number {
  if (value === undefined) {
    return 0;
  }
  return value;
}
