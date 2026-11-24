import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, model, signal } from '@angular/core';
import { MAX_PRODUCT_PHOTOS } from './product-photos';
import { PhotoUploader, thumbnailOf } from '../shared/photo-upload';
import { messageFrom } from '../shared/error-message';

/**
 * A product's photos in the product form: add (from the phone or computer), remove, and choose the
 * main one. Used as <app-photo-picker [(photos)]="photos" />; the form saves the list with the product.
 */
@Component({
  selector: 'app-photo-picker',
  templateUrl: './photo-picker.html',
  styleUrl: './photo-picker.css',
})
export class PhotoPicker {
  private readonly uploader = inject(PhotoUploader);

  /** The photo addresses, the main photo first. Two-way: the form sees every change. */
  readonly photos = model<string[]>([]);

  protected readonly max = MAX_PRODUCT_PHOTOS;
  protected readonly uploading = signal(0);
  protected readonly errorMessage = signal<string | null>(null);

  protected thumbnail(url: string): string {
    return thumbnailOf(url, 192);
  }

  protected canAddMore(): boolean {
    return this.photos().length + this.uploading() < this.max;
  }

  /** Uploads the chosen files one by one, as many as still fit. */
  protected async filesChosen(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = input.files;
    if (files === null) {
      return;
    }
    this.errorMessage.set(null);

    const chosen: File[] = [];
    for (let i = 0; i < files.length; i++) {
      chosen.push(files[i]);
    }
    // Clear the input, so choosing the same file again still counts as a change.
    input.value = '';

    for (const file of chosen) {
      if (!this.canAddMore()) {
        this.errorMessage.set('A product can have at most ' + this.max + ' photos.');
        break;
      }
      this.uploading.set(this.uploading() + 1);
      try {
        const url = await this.uploader.upload(file);
        this.photos.set([...this.photos(), url]);
      } catch (error) {
        this.errorMessage.set(this.messageFor(error));
      } finally {
        this.uploading.set(this.uploading() - 1);
      }
    }
  }

  protected remove(index: number): void {
    const next: string[] = [];
    const current = this.photos();
    for (let i = 0; i < current.length; i++) {
      if (i !== index) {
        next.push(current[i]);
      }
    }
    this.photos.set(next);
  }

  /** Moves a photo to the front: the first photo is the one customers see in the product list. */
  protected makeMain(index: number): void {
    const current = this.photos();
    const next: string[] = [current[index]];
    for (let i = 0; i < current.length; i++) {
      if (i !== index) {
        next.push(current[i]);
      }
    }
    this.photos.set(next);
  }

  /** One placeholder per photo still uploading. */
  protected uploadingSlots(): number[] {
    const slots: number[] = [];
    for (let i = 0; i < this.uploading(); i++) {
      slots.push(i);
    }
    return slots;
  }

  /** The backend's message (for example "not set up"), or the uploader's own. */
  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      return messageFrom(error, 'The photo could not be uploaded. Please try again.');
    }
    if (error instanceof Error) {
      return error.message;
    }
    return 'The photo could not be uploaded. Please try again.';
  }
}
