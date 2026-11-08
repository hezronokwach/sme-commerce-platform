import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../api/api';
import { categoryList } from '../api/fn/category/category-list';
import { productArchive } from '../api/fn/product/product-archive';
import { productCreate } from '../api/fn/product/product-create';
import { productGet } from '../api/fn/product/product-get';
import { productUpdate } from '../api/fn/product/product-update';
import { CategoryResponse } from '../api/models/category-response';
import { ProductResponse } from '../api/models/product-response';
import { codeFrom, messageFrom } from '../shared/error-message';
import { parseShillings, shillingsText } from './parse-shillings';
import { StockSection } from './stock-section';
import { textOrEmpty } from '../shared/text-or-empty';

type ProductStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';

/**
 * Adds a product (/products/new) or edits one (/products/<id>). When editing, the stock section is
 * shown under the form.
 */
@Component({
  selector: 'app-product-form-page',
  imports: [FormsModule, RouterLink, StockSection],
  templateUrl: './product-form-page.html',
  styleUrl: './product-form-page.css',
})
export class ProductFormPage implements OnInit {
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  /** Null when adding a new product. */
  protected productId: string | null = null;

  // The form fields, bound with [(ngModel)]. The price is kept as the text the user typed.
  protected name = '';
  protected priceText = '';
  protected categoryId = '';
  protected status: ProductStatus = 'DRAFT';
  protected description = '';

  /**
   * The version the product had when we loaded it. Sent back on save: if someone else saved in the
   * meantime the backend's version is higher, so it refuses ours (CONCURRENT_UPDATE) instead of
   * quietly overwriting their change.
   */
  private version = 0;

  /** The status stored on the server (the select may already show a new one). */
  protected readonly savedStatus = signal<string>('');
  protected readonly categories = signal<CategoryResponse[]>([]);
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly savedMessage = signal<string | null>(null);
  /** True after a CONCURRENT_UPDATE: the page offers to load the other person's version. */
  protected readonly conflict = signal(false);

  async ngOnInit(): Promise<void> {
    // The ":productId" part of the address, e.g. /products/3f2a... (/products/new has no such part).
    this.productId = this.route.snapshot.paramMap.get('productId');
    await this.load();
  }

  protected isNew(): boolean {
    return this.productId === null;
  }

  /** Loads the categories and, when editing, the product. Also used by "Load their version". */
  protected async load(): Promise<void> {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.savedMessage.set(null);
    this.conflict.set(false);
    try {
      this.categories.set(await this.api.invoke(categoryList));
      if (this.productId !== null) {
        const product = await this.api.invoke(productGet, { productId: this.productId });
        this.fillForm(product);
      }
    } catch (error) {
      this.errorMessage.set(messageFrom(error, 'The product could not be loaded. Please reload the page.'));
    } finally {
      this.loading.set(false);
    }
  }

  protected async save(): Promise<void> {
    this.errorMessage.set(null);
    this.savedMessage.set(null);
    this.conflict.set(false);

    if (this.name.trim() === '') {
      this.errorMessage.set('Enter a name.');
      return;
    }
    const priceMinor = parseShillings(this.priceText);
    if (priceMinor === null) {
      this.errorMessage.set('Enter the price in shillings, for example 50 or 49.50.');
      return;
    }

    this.saving.set(true);
    try {
      if (this.productId === null) {
        const created = await this.api.invoke(productCreate, {
          body: {
            name: this.name.trim(),
            priceMinor: priceMinor,
            categoryId: this.chosenCategory(),
            description: this.chosenDescription(),
            status: this.status,
          },
        });
        // Open the new product's own page, where its stock can be added.
        await this.router.navigateByUrl('/products/' + created.id);
      } else {
        const updated = await this.api.invoke(productUpdate, {
          productId: this.productId,
          body: {
            name: this.name.trim(),
            priceMinor: priceMinor,
            categoryId: this.chosenCategory(),
            description: this.chosenDescription(),
            status: this.status,
            version: this.version,
          },
        });
        // The answer carries the new version, ready for the next save.
        this.fillForm(updated);
        this.savedMessage.set('Saved.');
      }
    } catch (error) {
      if (codeFrom(error) === 'CONCURRENT_UPDATE') {
        this.conflict.set(true);
      } else {
        this.errorMessage.set(messageFrom(error, 'The product could not be saved. Please try again.'));
      }
    } finally {
      this.saving.set(false);
    }
  }

  /**
   * Archiving hides the product from the storefront but keeps it, because past orders still point
   * at it. That is why the backend has no real "delete" for products.
   */
  protected async archive(): Promise<void> {
    if (this.productId === null) {
      return;
    }
    const sure = confirm('Archive "' + this.name + '"? Customers will no longer see it.');
    if (!sure) {
      return;
    }
    this.errorMessage.set(null);
    try {
      await this.api.invoke(productArchive, { productId: this.productId });
      await this.router.navigateByUrl('/products');
    } catch (error) {
      this.errorMessage.set(messageFrom(error, 'The product could not be archived. Please try again.'));
    }
  }

  private fillForm(product: ProductResponse): void {
    this.name = textOrEmpty(product.name);
    this.priceText = '';
    if (product.priceMinor !== undefined) {
      this.priceText = shillingsText(product.priceMinor);
    }
    this.categoryId = textOrEmpty(product.categoryId);
    this.description = textOrEmpty(product.description);
    this.status = toStatus(product.status);
    this.savedStatus.set(this.status);
    if (product.version !== undefined) {
      this.version = product.version;
    }
  }

  /** The select's "No category" option has the value ''; the API wants no value at all instead. */
  private chosenCategory(): string | undefined {
    if (this.categoryId === '') {
      return undefined;
    }
    return this.categoryId;
  }

  private chosenDescription(): string | undefined {
    const trimmed = this.description.trim();
    if (trimmed === '') {
      return undefined;
    }
    return trimmed;
  }
}

function toStatus(value: string | undefined): ProductStatus {
  if (value === 'ACTIVE') {
    return 'ACTIVE';
  }
  if (value === 'ARCHIVED') {
    return 'ARCHIVED';
  }
  return 'DRAFT';
}
