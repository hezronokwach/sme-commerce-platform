import { Injectable, inject } from '@angular/core';
import { Api } from '../api/api';
import { photoUploadSign } from '../api/fn/photo-upload/photo-upload-sign';

/** Cloudinary's limit for one image on the free plan. */
export const MAX_PHOTO_BYTES = 10 * 1024 * 1024;

/**
 * Uploads one photo (a product photo or the shop's logo) and returns its https address (ADR 0022).
 *
 * 1. Our backend signs the upload: it allows this shop's folder, for the next hour.
 * 2. The browser sends the file straight to Cloudinary with that signature. The file never passes
 *    through our server.
 *
 * Step 2 uses the browser's own fetch, not Angular's HttpClient, on purpose: the auth interceptor adds
 * our access token to HttpClient requests, and that token must never be sent to another company.
 */
@Injectable({ providedIn: 'root' })
export class PhotoUploader {
  private readonly api = inject(Api);

  /** Throws an Error with a message for people when the file is wrong or the upload fails. */
  async upload(file: File): Promise<string> {
    if (!file.type.startsWith('image/')) {
      throw new Error('"' + file.name + '" is not a photo. Choose a JPG, PNG or WebP image.');
    }
    if (file.size > MAX_PHOTO_BYTES) {
      throw new Error('"' + file.name + '" is larger than 10 MB. Choose a smaller photo.');
    }

    // The backend answers 503 PHOTO_UPLOADS_NOT_SET_UP when the Cloudinary keys are missing; the
    // caller shows that message.
    const signature = await this.api.invoke(photoUploadSign);

    const form = new FormData();
    form.append('file', file);
    form.append('api_key', signature.apiKey ?? '');
    form.append('timestamp', String(signature.timestamp ?? 0));
    form.append('folder', signature.folder ?? '');
    form.append('signature', signature.signature ?? '');

    let answer: Response;
    try {
      answer = await fetch(signature.uploadUrl ?? '', { method: 'POST', body: form });
    } catch {
      throw new Error('The photo could not be uploaded. Check your connection and try again.');
    }
    if (!answer.ok) {
      throw new Error('Cloudinary refused "' + file.name + '". Please try another photo.');
    }
    const body = await answer.json();
    if (typeof body.secure_url !== 'string') {
      throw new Error('Cloudinary did not return the photo\'s address. Please try again.');
    }
    return body.secure_url;
  }
}

/**
 * A smaller copy of a Cloudinary photo, for thumbnails: Cloudinary resizes it on the fly when the
 * address asks for it (c_fill crops to a square, f_auto and q_auto pick a light format and quality).
 * Any other address is returned unchanged.
 */
export function thumbnailOf(url: string, size: number): string {
  const marker = '/image/upload/';
  const at = url.indexOf(marker);
  if (!url.startsWith('https://res.cloudinary.com/') || at === -1) {
    return url;
  }
  const start = url.substring(0, at + marker.length);
  const rest = url.substring(at + marker.length);
  return start + 'c_fill,w_' + size + ',h_' + size + ',f_auto,q_auto/' + rest;
}
