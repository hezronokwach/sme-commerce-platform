import { Component, OnInit, inject, signal } from '@angular/core';
import { Api } from '../api/api';
import { paymentMethodsList } from '../api/fn/payment-methods/payment-methods-list';
import { paymentMethodsSet } from '../api/fn/payment-methods/payment-methods-set';
import { PaymentMethodResponse } from '../api/models/payment-method-response';
import { messageFrom } from '../shared/error-message';
import { textOrEmpty } from '../shared/text-or-empty';

/** One row: a way of paying, whether the shop offers it, and whether the platform can take it yet. */
export interface PaymentMethodRow {
  provider: string;
  name: string;
  enabled: boolean;
  available: boolean;
}

/**
 * "Payment methods" in Settings (owners only, like the whole page): which ways of paying the shop
 * offers its customers. Switching here is all it takes to move the shop from M-Pesa to card: checkout
 * shows the new buttons on its next load, and nothing else in the system changes.
 *
 * The backend keeps at least one method on; if this would turn off the last one, it says so and the
 * list stays as it was.
 */
@Component({
  selector: 'app-payment-methods-section',
  templateUrl: './payment-methods-section.html',
  styleUrl: './settings-page.css',
})
export class PaymentMethodsSection implements OnInit {
  private readonly api = inject(Api);

  protected readonly rows = signal<PaymentMethodRow[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      const methods = await this.api.invoke(paymentMethodsList);
      this.rows.set(buildPaymentMethodRows(methods));
    } catch (error) {
      this.errorMessage.set(messageFrom(error, 'The payment methods could not be loaded. Please reload the page.'));
    } finally {
      this.loading.set(false);
    }
  }

  /** The checkbox was clicked: ask the backend, then show what it says is now true. */
  protected async toggle(row: PaymentMethodRow, checkbox: HTMLInputElement): Promise<void> {
    const wanted = checkbox.checked;
    this.saving.set(true);
    this.errorMessage.set(null);
    try {
      const methods = await this.api.invoke(paymentMethodsSet, {
        provider: row.provider,
        body: { enabled: wanted },
      });
      this.rows.set(buildPaymentMethodRows(methods));
    } catch (error) {
      // Put the checkbox back: nothing changed on the server.
      checkbox.checked = row.enabled;
      this.errorMessage.set(messageFrom(error, 'That change could not be saved. Please try again.'));
    } finally {
      this.saving.set(false);
    }
  }
}

export function buildPaymentMethodRows(methods: PaymentMethodResponse[]): PaymentMethodRow[] {
  const rows: PaymentMethodRow[] = [];
  for (const method of methods) {
    rows.push({
      provider: textOrEmpty(method.provider),
      name: textOrEmpty(method.displayName),
      enabled: method.enabled === true,
      available: method.available === true,
    });
  }
  return rows;
}
