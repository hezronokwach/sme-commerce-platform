import { Component, OnInit, inject, signal } from '@angular/core';
import { Api } from '../api/api';
import { staffList } from '../api/fn/staff/staff-list';
import { StaffResponse } from '../api/models/staff-response';
import { formatDateTime } from '../orders/order-labels';
import { messageFrom } from '../shared/error-message';
import { textOrEmpty } from '../shared/text-or-empty';

/** One row of the staff table. */
interface StaffRow {
  id: string;
  fullName: string;
  email: string;
  role: string;
  status: string;
  lastLogin: string;
}

/**
 * Settings at /settings, owners only: ownerGuard keeps staff off this page, and the backend's
 * @PreAuthorize("hasRole('OWNER')") on /api/admin/staff refuses them even if they call it directly.
 *
 * For now it lists the people who can log in to this shop. Inviting and disabling staff need
 * backend endpoints that don't exist yet; they come with their own issue.
 */
@Component({
  selector: 'app-settings-page',
  templateUrl: './settings-page.html',
  styleUrl: './settings-page.css',
})
export class SettingsPage implements OnInit {
  private readonly api = inject(Api);

  protected readonly rows = signal<StaffRow[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      const staff = await this.api.invoke(staffList);
      this.rows.set(buildStaffRows(staff));
    } catch (error) {
      this.errorMessage.set(messageFrom(error, 'The staff list could not be loaded. Please reload the page.'));
    } finally {
      this.loading.set(false);
    }
  }
}

export function buildStaffRows(staff: StaffResponse[]): StaffRow[] {
  const rows: StaffRow[] = [];
  for (const person of staff) {
    let lastLogin = 'Never';
    if (person.lastLoginAt !== undefined && person.lastLoginAt !== null) {
      lastLogin = formatDateTime(person.lastLoginAt);
    }
    rows.push({
      id: textOrEmpty(person.id),
      fullName: textOrEmpty(person.fullName),
      email: textOrEmpty(person.email),
      role: roleLabel(person.role),
      status: staffStatusLabel(person.status),
      lastLogin: lastLogin,
    });
  }
  return rows;
}

function roleLabel(role: string | undefined): string {
  if (role === 'OWNER') {
    return 'Owner';
  }
  if (role === 'STAFF') {
    return 'Staff';
  }
  return textOrEmpty(role);
}

function staffStatusLabel(status: string | undefined): string {
  if (status === 'ACTIVE') {
    return 'Active';
  }
  if (status === 'INVITED') {
    return 'Invited (not joined yet)';
  }
  if (status === 'DISABLED') {
    return 'Disabled';
  }
  return textOrEmpty(status);
}
