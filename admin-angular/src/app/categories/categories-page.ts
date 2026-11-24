import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../api/api';
import { categoryCreate } from '../api/fn/category/category-create';
import { categoryDelete } from '../api/fn/category/category-delete';
import { categoryList } from '../api/fn/category/category-list';
import { categoryUpdate } from '../api/fn/category/category-update';
import { CategoryResponse } from '../api/models/category-response';
import { messageFrom } from '../shared/error-message';
import { textOrEmpty } from '../shared/text-or-empty';

/** One category as this page shows it. */
export interface CategoryRow {
  id: string;
  name: string;
  slug: string;
  sortOrder: number;
}

/** The backend's list, as rows, in the order it sent them (the storefront menu's order). */
export function toRows(categories: CategoryResponse[]): CategoryRow[] {
  const rows: CategoryRow[] = [];
  for (const category of categories) {
    rows.push({
      id: textOrEmpty(category.id),
      name: textOrEmpty(category.name),
      slug: textOrEmpty(category.slug),
      sortOrder: category.sortOrder ?? 0,
    });
  }
  return rows;
}

/**
 * Categories at /categories: the groups customers can filter the shop by. Owners and staff may add,
 * rename, reorder and delete them. A category with products can't be deleted (the backend says so).
 */
@Component({
  selector: 'app-categories-page',
  imports: [FormsModule],
  templateUrl: './categories-page.html',
  styleUrl: './categories-page.css',
})
export class CategoriesPage implements OnInit {
  private readonly api = inject(Api);

  protected readonly rows = signal<CategoryRow[]>([]);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  /** The "Add a category" box. */
  protected newName = '';

  /** The row being renamed, and the name typed so far. */
  protected readonly renamingId = signal<string | null>(null);
  protected renameText = '';

  async ngOnInit(): Promise<void> {
    await this.reload();
  }

  protected async add(): Promise<void> {
    const name = this.newName.trim();
    if (name === '') {
      return;
    }
    // New categories go to the end of the menu.
    let sortOrder = 0;
    for (const row of this.rows()) {
      if (row.sortOrder >= sortOrder) {
        sortOrder = row.sortOrder + 1;
      }
    }
    const done = await this.run(() => this.api.invoke(categoryCreate, { body: { name: name, sortOrder: sortOrder } }));
    if (done) {
      this.newName = '';
    }
  }

  protected startRename(row: CategoryRow): void {
    this.renamingId.set(row.id);
    this.renameText = row.name;
    // Put the cursor in the new box (once Angular has drawn it), with the old name selected.
    setTimeout(() => {
      const box = document.getElementById('rename-' + row.id) as HTMLInputElement | null;
      if (box !== null) {
        box.focus();
        box.select();
      }
    });
  }

  protected cancelRename(): void {
    this.renamingId.set(null);
  }

  protected async saveRename(row: CategoryRow): Promise<void> {
    const name = this.renameText.trim();
    if (name === '' || name === row.name) {
      this.renamingId.set(null);
      return;
    }
    // The web name (slug) stays the same, so nothing that points at it changes.
    const done = await this.run(() =>
      this.api.invoke(categoryUpdate, {
        categoryId: row.id,
        body: { name: name, slug: row.slug, sortOrder: row.sortOrder },
      }),
    );
    if (done) {
      this.renamingId.set(null);
    }
  }

  /** Moves a category one place up (-1) or down (+1) in the storefront menu. */
  protected async move(index: number, step: number): Promise<void> {
    const rows = this.rows();
    const other = index + step;
    if (other < 0 || other >= rows.length) {
      return;
    }
    // Every row gets its position as its order, with the two rows swapped. This also fixes rows that
    // shared the same number before.
    const ordered: CategoryRow[] = [...rows];
    ordered[index] = rows[other];
    ordered[other] = rows[index];

    await this.run(async () => {
      for (let position = 0; position < ordered.length; position++) {
        const row = ordered[position];
        if (row.sortOrder !== position) {
          await this.api.invoke(categoryUpdate, {
            categoryId: row.id,
            body: { name: row.name, slug: row.slug, sortOrder: position },
          });
        }
      }
    });
  }

  protected async remove(row: CategoryRow): Promise<void> {
    if (!confirm('Delete the category "' + row.name + '"?')) {
      return;
    }
    await this.run(() => this.api.invoke(categoryDelete, { categoryId: row.id }));
  }

  /** Runs one change, then reloads the list. Returns false (and shows why) if the change failed. */
  private async run(change: () => Promise<unknown>): Promise<boolean> {
    this.errorMessage.set(null);
    this.busy.set(true);
    let ok = true;
    try {
      await change();
    } catch (error) {
      this.errorMessage.set(messageFrom(error, 'That change could not be saved. Please try again.'));
      ok = false;
    }
    await this.reload();
    this.busy.set(false);
    return ok;
  }

  private async reload(): Promise<void> {
    try {
      this.rows.set(toRows(await this.api.invoke(categoryList)));
    } catch (error) {
      this.errorMessage.set(messageFrom(error, 'The categories could not be loaded. Please reload the page.'));
    } finally {
      this.loading.set(false);
    }
  }
}
