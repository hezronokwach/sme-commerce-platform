import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * The root component: an empty frame. The router puts the page for the current address
 * (login, products, ...) into the <router-outlet /> in app.html.
 */
@Component({
  imports: [RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {}
