import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';
import { ownerGuard } from './auth/owner.guard';
import { AdminLayout } from './layout/admin-layout';
import { LoginPage } from './login/login-page';
import { ProductFormPage } from './products/product-form-page';
import { ProductsPage } from './products/products-page';
import { SettingsPage } from './settings/settings-page';

/** Which page each address shows. The first matching entry wins. */
export const routes: Routes = [
  // The login page has no menu, so it sits outside the layout.
  { path: 'login', component: LoginPage },
  {
    // Every logged-in page sits inside AdminLayout (the menu). authGuard here protects all the
    // children at once: logged-out users are sent to /login.
    path: '',
    component: AdminLayout,
    canActivate: [authGuard],
    children: [
      { path: 'products', component: ProductsPage },
      // 'new' must come before ':productId', or the router would treat "new" as a product id.
      { path: 'products/new', component: ProductFormPage },
      { path: 'products/:productId', component: ProductFormPage },
      // Both guards run: authGuard (from the parent) first, then ownerGuard.
      { path: 'settings', component: SettingsPage, canActivate: [ownerGuard] },
      // The bare address opens the products.
      { path: '', redirectTo: 'products', pathMatch: 'full' },
    ],
  },
  // Any unknown address goes to the products (and so to the login page, when logged out).
  { path: '**', redirectTo: 'products' },
];
