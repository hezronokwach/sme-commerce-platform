import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Api } from '../api/api';
import { adminOrderGet } from '../api/fn/admin-order/admin-order-get';
import { OrderDetailResponse } from '../api/models/order-detail-response';
import { formatPrice } from '../products/format-price';
import { codeFrom, messageFrom } from '../shared/error-message';
import { actorLabel, formatDateTime, paymentMethodLabel, statusLabel } from './order-labels';
import { textOrEmpty } from '../shared/text-or-empty';

/** Everything the page shows, already turned into text. */
interface OrderView {
  orderNumber: string;
  status: string;
  statusLabel: string;
  customerName: string;
  customerPhone: string;
  paymentMethod: string;
  total: string;
  placedAt: string;
  /** Only while waiting for payment: when the held stock is released. Empty otherwise. */
  payBy: string;
  lines: LineView[];
  history: HistoryView[];
}

interface LineView {
  productName: string;
  quantity: number;
  unitPrice: string;
  lineTotal: string;
}

interface HistoryView {
  /** "Created" for the first entry, otherwise "Created → Waiting for payment". */
  change: string;
  by: string;
  reason: string;
  at: string;
}

/** One order at /orders/<id>: who ordered, what, and every status change so far. */
@Component({
  selector: 'app-order-detail-page',
  imports: [RouterLink],
  templateUrl: './order-detail-page.html',
  styleUrl: './order-detail-page.css',
})
export class OrderDetailPage implements OnInit {
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);

  protected readonly order = signal<OrderView | null>(null);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    const orderId = this.route.snapshot.paramMap.get('orderId');
    if (orderId === null) {
      this.errorMessage.set('No order chosen.');
      this.loading.set(false);
      return;
    }

    try {
      const answer = await this.api.invoke(adminOrderGet, { orderId: orderId });
      this.order.set(buildOrderView(answer));
    } catch (error) {
      if (codeFrom(error) === 'ORDER_NOT_FOUND') {
        // Also what another shop's order looks like: the backend answers 404, never "not yours".
        this.errorMessage.set('This order does not exist.');
      } else {
        this.errorMessage.set(messageFrom(error, 'The order could not be loaded. Please reload the page.'));
      }
    } finally {
      this.loading.set(false);
    }
  }
}

export function buildOrderView(answer: OrderDetailResponse): OrderView {
  const lines: LineView[] = [];
  if (answer.lines !== undefined) {
    for (const line of answer.lines) {
      let quantity = 0;
      if (line.quantity !== undefined) {
        quantity = line.quantity;
      }
      lines.push({
        productName: textOrEmpty(line.productName),
        quantity: quantity,
        unitPrice: formatPrice(line.unitPriceMinor, answer.currency),
        lineTotal: formatPrice(line.lineTotalMinor, answer.currency),
      });
    }
  }

  const history: HistoryView[] = [];
  if (answer.history !== undefined) {
    for (const entry of answer.history) {
      let change = statusLabel(entry.toStatus);
      if (entry.fromStatus !== undefined && entry.fromStatus !== null) {
        change = statusLabel(entry.fromStatus) + ' → ' + statusLabel(entry.toStatus);
      }
      history.push({
        change: change,
        by: actorLabel(entry.actorType),
        reason: textOrEmpty(entry.reason),
        at: formatDateTime(entry.at),
      });
    }
  }

  let payBy = '';
  if (answer.status === 'PENDING_PAYMENT') {
    payBy = formatDateTime(answer.expiresAt);
  }

  return {
    orderNumber: textOrEmpty(answer.orderNumber),
    status: textOrEmpty(answer.status),
    statusLabel: statusLabel(answer.status),
    customerName: textOrEmpty(answer.customerName),
    customerPhone: textOrEmpty(answer.customerPhone),
    paymentMethod: paymentMethodLabel(answer.paymentMethod),
    total: formatPrice(answer.totalMinor, answer.currency),
    placedAt: formatDateTime(answer.createdAt),
    payBy: payBy,
    lines: lines,
    history: history,
  };
}
