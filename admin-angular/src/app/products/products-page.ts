import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Api } from '../api/api';
import { categoryList } from '../api/fn/category/category-list';
import { productList } from '../api/fn/product/product-list';
import { CategoryResponse } from '../api/models/category-response';
import { ProductResponse } from '../api/models/product-response';
import { AuthService } from '../auth/auth.service';
import { formatPrice } from './format-price';

/** One table row: what the page shows for a product. */
interface ProductRow {
  id: string;
  name: string;
  price: string;
  categoryName: string;
  status: string;
}

/**
 * The product list at /products (only reachable when logged in, see authGuard).
 * Loads the shop's products and categories, then shows one row per product.
 */
@Component({
  selector: 'app-products-page',
  templateUrl: './products-page.html',
  styleUrl: './products-page.css',
})
export class ProductsPage implements OnInit {
  private readonly api = inject(Api);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly rows = signal<ProductRow[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  /** Angular calls this once, when the page opens. */
  async ngOnInit(): Promise<void> {
    try {
      // The shop comes from the token: the backend only returns this shop's products and categories.
      const products = await this.api.invoke(productList);
      const categories = await this.api.invoke(categoryList);
      this.rows.set(buildRows(products, categories));
    } catch {
      this.errorMessage.set('The products could not be loaded. Please reload the page.');
    } finally {
      this.loading.set(false);
    }
  }

  async logOut(): Promise<void> {
    await this.auth.logout();
    await this.router.navigateByUrl('/login');
  }
}

/** Joins each product with its category's name (products only carry the category's id). */
export function buildRows(products: ProductResponse[], categories: CategoryResponse[]): ProductRow[] {
  const categoryNames = new Map<string, string>();
  for (const category of categories) {
    if (category.id !== undefined && category.name !== undefined) {
      categoryNames.set(category.id, category.name);
    }
  }

  const rows: ProductRow[] = [];
  for (const product of products) {
    let categoryName = '—';
    if (product.categoryId !== undefined) {
      const name = categoryNames.get(product.categoryId);
      if (name !== undefined) {
        categoryName = name;
      }
    }

    rows.push({
      id: textOrEmpty(product.id),
      name: textOrEmpty(product.name),
      price: formatPrice(product.priceMinor, product.currency),
      categoryName: categoryName,
      status: textOrEmpty(product.status),
    });
  }
  return rows;
}

/** The generated models mark every field optional, so a missing one is shown as empty text. */
function textOrEmpty(value: string | undefined): string {
  if (value === undefined) {
    return '';
  }
  return value;
}
