import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../api/api';
import { shopSettingsGet } from '../api/fn/shop-settings/shop-settings-get';
import { shopSettingsUpdate } from '../api/fn/shop-settings/shop-settings-update';
import { ShopSettingsResponse } from '../api/models/shop-settings-response';
import { messageFrom } from '../shared/error-message';
import { PhotoUploader, thumbnailOf } from '../shared/photo-upload';
import { textOrEmpty } from '../shared/text-or-empty';

/** The colours a shop starts with (the backend's defaults), for "Use the default colours". */
export const DEFAULT_PRIMARY = '#1F1B17';
export const DEFAULT_ACCENT = '#B4532A';

/** True for a colour the backend accepts: # and six hex digits. */
export function isColour(text: string): boolean {
  return /^#[0-9A-Fa-f]{6}$/.test(text);
}

/**
 * "Shop" in Settings: the name, tagline, logo and colours customers see. The web name is shown but
 * can't be changed, because links the owner already shared would stop working.
 */
@Component({
  selector: 'app-shop-section',
  imports: [FormsModule],
  templateUrl: './shop-section.html',
  styleUrl: './shop-section.css',
})
export class ShopSection implements OnInit {
  private readonly api = inject(Api);
  private readonly uploader = inject(PhotoUploader);

  protected name = '';
  protected tagline = '';
  protected logoUrl = '';
  protected primaryColor = DEFAULT_PRIMARY;
  protected accentColor = DEFAULT_ACCENT;
  protected readonly slug = signal('');

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly uploadingLogo = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly savedMessage = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      this.fill(await this.api.invoke(shopSettingsGet));
    } catch (error) {
      this.errorMessage.set(messageFrom(error, "The shop's details could not be loaded. Please reload the page."));
    } finally {
      this.loading.set(false);
    }
  }

  protected logoPreview(): string {
    return thumbnailOf(this.logoUrl, 160);
  }

  protected async logoChosen(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    if (input.files === null || input.files.length === 0) {
      return;
    }
    const file = input.files[0];
    input.value = '';
    this.errorMessage.set(null);
    this.uploadingLogo.set(true);
    try {
      this.logoUrl = await this.uploader.upload(file);
    } catch (error) {
      if (error instanceof HttpErrorResponse) {
        this.errorMessage.set(messageFrom(error, 'The logo could not be uploaded. Please try again.'));
      } else if (error instanceof Error) {
        this.errorMessage.set(error.message);
      }
    } finally {
      this.uploadingLogo.set(false);
    }
  }

  protected removeLogo(): void {
    this.logoUrl = '';
  }

  protected useDefaultColours(): void {
    this.primaryColor = DEFAULT_PRIMARY;
    this.accentColor = DEFAULT_ACCENT;
  }

  protected async save(): Promise<void> {
    this.errorMessage.set(null);
    this.savedMessage.set(null);
    if (this.name.trim() === '') {
      this.errorMessage.set('Enter the shop name.');
      return;
    }
    if (!isColour(this.primaryColor) || !isColour(this.accentColor)) {
      this.errorMessage.set('Colours look like #1F1B17: a # and six letters or digits.');
      return;
    }

    this.saving.set(true);
    try {
      const saved = await this.api.invoke(shopSettingsUpdate, {
        body: {
          name: this.name.trim(),
          tagline: this.tagline.trim(),
          logoUrl: this.logoUrl,
          primaryColor: this.primaryColor,
          accentColor: this.accentColor,
        },
      });
      this.fill(saved);
      this.savedMessage.set('Saved. Customers see the change the next time they open the shop.');
    } catch (error) {
      this.errorMessage.set(messageFrom(error, "The shop's details could not be saved. Please try again."));
    } finally {
      this.saving.set(false);
    }
  }

  private fill(shop: ShopSettingsResponse): void {
    this.name = textOrEmpty(shop.name);
    this.tagline = textOrEmpty(shop.tagline);
    this.logoUrl = textOrEmpty(shop.logoUrl);
    this.slug.set(textOrEmpty(shop.slug));
    if (shop.primaryColor !== undefined) {
      this.primaryColor = shop.primaryColor;
    }
    if (shop.accentColor !== undefined) {
      this.accentColor = shop.accentColor;
    }
  }
}
