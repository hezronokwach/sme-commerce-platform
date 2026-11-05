import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';
import { LoginPage } from './login/login-page';
import { ProductsPage } from './products/products-page';

/** Which page each address shows. The first matching entry wins. */
export const routes: Routes = [
  { path: 'login', component: LoginPage },
  // canActivate: the guard decides first; logged-out users are sent to /login instead.
  { path: 'products', component: ProductsPage, canActivate: [authGuard] },
  // The bare address opens the products (and so the login page, when logged out).
  { path: '', redirectTo: 'products', pathMatch: 'full' },
  // Any unknown address does the same.
  { path: '**', redirectTo: 'products' },
];
