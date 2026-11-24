import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideApiConfiguration } from '../api/api-configuration';
import { vi } from 'vitest';
import { CategoriesPage, toRows } from './categories-page';

describe('toRows', () => {
  it('keeps the order and fills in missing numbers', () => {
    const rows = toRows([{ id: 'c1', name: 'Paint', slug: 'paint' }]);

    expect(rows).toEqual([{ id: 'c1', name: 'Paint', slug: 'paint', sortOrder: 0 }]);
  });
});

describe('CategoriesPage', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CategoriesPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApiConfiguration('')],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  it('adds a new category at the end of the menu', async () => {
    const fixture = TestBed.createComponent(CategoriesPage);
    fixture.detectChanges();
    backend.expectOne('/api/admin/categories').flush([
      { id: 'c1', name: 'Paint', slug: 'paint', sortOrder: 0 },
      { id: 'c2', name: 'Tools', slug: 'tools', sortOrder: 1 },
    ]);
    await letPromisesRun();
    fixture.detectChanges();

    const input = fixture.nativeElement.querySelector('#new-category') as HTMLInputElement;
    input.value = 'Locks';
    input.dispatchEvent(new Event('input'));
    (fixture.nativeElement.querySelector('form.add') as HTMLFormElement).dispatchEvent(new Event('submit'));

    const create = backend.expectOne((request) => request.method === 'POST' && request.url === '/api/admin/categories');
    expect(create.request.body).toEqual({ name: 'Locks', sortOrder: 2 });
    create.flush({ id: 'c3', name: 'Locks', slug: 'locks', sortOrder: 2 });
    await letPromisesRun();
    backend.expectOne('/api/admin/categories').flush([]);
    backend.verify();
  });

  it('moves a category up by saving the new positions', async () => {
    const fixture = TestBed.createComponent(CategoriesPage);
    fixture.detectChanges();
    backend.expectOne('/api/admin/categories').flush([
      { id: 'c1', name: 'Paint', slug: 'paint', sortOrder: 0 },
      { id: 'c2', name: 'Tools', slug: 'tools', sortOrder: 1 },
    ]);
    await letPromisesRun();
    fixture.detectChanges();

    const upButton = fixture.nativeElement.querySelector('[aria-label="Move Tools up"]') as HTMLButtonElement;
    upButton.click();
    await letPromisesRun();

    const first = backend.expectOne('/api/admin/categories/c2');
    expect(first.request.body).toEqual({ name: 'Tools', slug: 'tools', sortOrder: 0 });
    first.flush({});
    await letPromisesRun();
    const second = backend.expectOne('/api/admin/categories/c1');
    expect(second.request.body).toEqual({ name: 'Paint', slug: 'paint', sortOrder: 1 });
    second.flush({});
    await letPromisesRun();
    backend.expectOne('/api/admin/categories').flush([]);
  });

  it("says why a category with products can't be deleted", async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fixture = TestBed.createComponent(CategoriesPage);
    fixture.detectChanges();
    backend.expectOne('/api/admin/categories').flush([{ id: 'c1', name: 'Paint', slug: 'paint', sortOrder: 0 }]);
    await letPromisesRun();
    fixture.detectChanges();

    const deleteButton = fixture.nativeElement.querySelector('button.danger') as HTMLButtonElement;
    deleteButton.click();
    backend.expectOne('/api/admin/categories/c1').flush(
      { status: 409, code: 'CATEGORY_IN_USE', detail: 'Move its products to another category first.' },
      { status: 409, statusText: 'Conflict' },
    );
    await letPromisesRun();
    backend.expectOne('/api/admin/categories').flush([{ id: 'c1', name: 'Paint', slug: 'paint', sortOrder: 0 }]);
    await letPromisesRun();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Move its products to another category first.');
  });

  function letPromisesRun(): Promise<void> {
    return new Promise(function (resolve) {
      setTimeout(resolve, 0);
    });
  }
});
