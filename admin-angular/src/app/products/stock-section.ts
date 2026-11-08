import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../api/api';
import { stockAdjust } from '../api/fn/stock/stock-adjust';
import { stockGet } from '../api/fn/stock/stock-get';
import { StockResponse } from '../api/models/stock-response';
import { codeFrom, messageFrom } from '../shared/error-message';

type StockReason = 'RESTOCK' | 'SALE' | 'ADJUSTMENT' | 'RETURN';

/**
 * The stock part of the product page: how many are in the shop, how many are held for unpaid
 * orders, how many can still be sold, and a form to change the count.
 *
 * Used inside the product page as <app-stock-section [productId]="..." />.
 */
@Component({
  selector: 'app-stock-section',
  imports: [FormsModule],
  templateUrl: './stock-section.html',
  styleUrl: './stock-section.css',
})
export class StockSection implements OnInit {
  private readonly api = inject(Api);

  /** Passed in by the product page. input.required: Angular complains if the page forgets it. */
  readonly productId = input.required<string>();

  protected readonly stock = signal<StockResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly loadMessage = signal<string | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  // The adjust form, bound with [(ngModel)].
  protected changeText = '';
  protected reason: StockReason = 'RESTOCK';
  protected note = '';

  async ngOnInit(): Promise<void> {
    await this.load();
  }

  protected async load(): Promise<void> {
    this.loading.set(true);
    this.loadMessage.set(null);
    try {
      this.stock.set(await this.api.invoke(stockGet, { productId: this.productId() }));
    } catch (error) {
      if (codeFrom(error) === 'STOCK_ITEM_NOT_FOUND') {
        // Inventory creates the stock row when it hears ProductCreatedEvent, a moment after the
        // product is saved (the event is handled after the product's transaction commits).
        this.loadMessage.set('Stock is still being set up for this new product.');
      } else {
        this.loadMessage.set(messageFrom(error, 'The stock could not be loaded.'));
      }
    } finally {
      this.loading.set(false);
    }
  }

  protected async adjust(): Promise<void> {
    this.errorMessage.set(null);

    const change = parseWholeNumber(this.changeText);
    if (change === null || change === 0) {
      this.errorMessage.set('Enter how many to add (e.g. 20) or remove (e.g. -3).');
      return;
    }

    this.saving.set(true);
    try {
      let note: string | undefined = undefined;
      if (this.note.trim() !== '') {
        note = this.note.trim();
      }
      const updated = await this.api.invoke(stockAdjust, {
        productId: this.productId(),
        body: { change: change, reason: this.reason, note: note },
      });
      this.stock.set(updated);
      this.changeText = '';
      this.note = '';
    } catch (error) {
      // E.g. WOULD_BREAK_RESERVATIONS: removing more than is free would take stock held for orders.
      this.errorMessage.set(messageFrom(error, 'The stock could not be changed. Please try again.'));
    } finally {
      this.saving.set(false);
    }
  }
}

/** "20" -> 20, "-3" -> -3, " 7 " -> 7; anything else (2.5, abc, empty) -> null. */
export function parseWholeNumber(text: string): number | null {
  const trimmed = text.trim();
  if (trimmed === '') {
    return null;
  }
  const value = Number(trimmed);
  if (!Number.isInteger(value)) {
    return null;
  }
  return value;
}
