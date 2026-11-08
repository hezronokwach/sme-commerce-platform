import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideApiConfiguration } from '../api/api-configuration';
import { SettingsPage, buildStaffRows } from './settings-page';

describe('buildStaffRows', () => {
  it('shows roles and statuses in words, and "Never" for someone who has not logged in', () => {
    const rows = buildStaffRows([
      { id: 's1', fullName: 'Owner', email: 'owner@example.com', role: 'OWNER', status: 'ACTIVE',
        lastLoginAt: '2026-11-08T07:05:00Z' },
      { id: 's2', fullName: 'Wanjiku', email: 'wanjiku@example.com', role: 'STAFF', status: 'INVITED' },
    ]);

    expect(rows[0].role).toBe('Owner');
    expect(rows[0].lastLogin).toContain('10:05');
    expect(rows[1].status).toBe('Invited (not joined yet)');
    expect(rows[1].lastLogin).toBe('Never');
  });
});

describe('SettingsPage', () => {
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SettingsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideApiConfiguration('')],
    });
    backend = TestBed.inject(HttpTestingController);
  });

  it('lists the staff', async () => {
    const fixture = TestBed.createComponent(SettingsPage);
    fixture.detectChanges();

    backend.expectOne('/api/admin/staff').flush([
      { id: 's1', fullName: 'Owner', email: 'owner@example.com', role: 'OWNER', status: 'ACTIVE' },
    ]);
    await letPromisesRun();
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelectorAll('tbody tr').length).toBe(1);
    expect(page.textContent).toContain('owner@example.com');
  });

  function letPromisesRun(): Promise<void> {
    return new Promise(function (resolve) {
      setTimeout(resolve, 0);
    });
  }
});
