import { Component, OnInit, inject, signal } from '@angular/core';
import { environment } from '../../environments/environment';
import { Api } from '../api/api';
import { shopSettingsGet } from '../api/fn/shop-settings/shop-settings-get';

/** The storefront address of a shop: the storefront site, then /s/ and the shop's web name. */
export function shopAddress(storefrontUrl: string, slug: string): string {
  return storefrontUrl + '/s/' + slug;
}

/** A WhatsApp link that opens a new message with the text filled in, for the owner to send. */
export function whatsAppShareLink(shopName: string, address: string): string {
  const text = 'Shop at ' + shopName + ': ' + address;
  return 'https://wa.me/?text=' + encodeURIComponent(text);
}

/**
 * "Your shop's link": the address customers open, with buttons to copy it, share it on WhatsApp, or
 * open the shop. Loads the shop's details itself, so any page can show it.
 */
@Component({
  selector: 'app-shop-link',
  templateUrl: './shop-link.html',
  styleUrl: './shop-link.css',
})
export class ShopLink implements OnInit {
  private readonly api = inject(Api);

  protected readonly address = signal<string | null>(null);
  protected readonly shareLink = signal('');
  protected readonly copied = signal(false);

  async ngOnInit(): Promise<void> {
    try {
      const shop = await this.api.invoke(shopSettingsGet);
      if (shop.slug !== undefined) {
        const address = shopAddress(environment.storefrontUrl, shop.slug);
        this.address.set(address);
        this.shareLink.set(whatsAppShareLink(shop.name ?? shop.slug, address));
      }
    } catch {
      // Without the details the box simply isn't shown; the rest of the page still works.
    }
  }

  protected async copy(): Promise<void> {
    const address = this.address();
    if (address === null) {
      return;
    }
    try {
      await navigator.clipboard.writeText(address);
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    } catch {
      // Some browsers block the clipboard; the address is still on screen to copy by hand.
    }
  }
}
