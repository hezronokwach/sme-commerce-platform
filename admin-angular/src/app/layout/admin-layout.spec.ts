import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { AdminLayout } from './admin-layout';

describe('AdminLayout', () => {
  let role: string | null;

  beforeEach(async () => {
    role = null;
    const fakeAuth = {
      getRole: function (): string | null {
        return role;
      },
      isOwner: function (): boolean {
        return role === 'OWNER';
      },
      logout: async function (): Promise<void> {},
    };
    await TestBed.configureTestingModule({
      imports: [AdminLayout],
      providers: [provideRouter([]), { provide: AuthService, useValue: fakeAuth }],
    }).compileComponents();
  });

  it('shows Settings in the menu for an owner', async () => {
    role = 'OWNER';

    const links = await menuLinks();

    expect(links).toEqual(['Products', 'Orders', 'Settings']);
  });

  it('hides Settings from staff', async () => {
    role = 'STAFF';

    const links = await menuLinks();

    expect(links).toEqual(['Products', 'Orders']);
  });

  async function menuLinks(): Promise<string[]> {
    const fixture = TestBed.createComponent(AdminLayout);
    await fixture.whenStable();
    const page = fixture.nativeElement as HTMLElement;
    const texts: string[] = [];
    const anchors = page.querySelectorAll('nav a');
    for (let i = 0; i < anchors.length; i++) {
      texts.push((anchors[i].textContent || '').trim());
    }
    return texts;
  }
});
