import { HttpErrorResponse } from '@angular/common/http';

/**
 * Every backend error has the same JSON shape (ADR 0006):
 *   { "status": 409, "title": "...", "detail": "for people", "code": "FOR_THE_CODE", ... }
 * These two helpers read it, so each page doesn't have to.
 */

/** The "detail" text, written for people. Falls back to the given text if there isn't one. */
export function messageFrom(error: unknown, fallback: string): string {
  if (error instanceof HttpErrorResponse && error.status === 0) {
    return 'The server cannot be reached. Is the backend running?';
  }
  const body = errorBody(error);
  if (body !== null && typeof body['detail'] === 'string') {
    return body['detail'];
  }
  return fallback;
}

/** The "code" (e.g. CONCURRENT_UPDATE), so a page can react to one error in its own way. Null if none. */
export function codeFrom(error: unknown): string | null {
  const body = errorBody(error);
  if (body !== null && typeof body['code'] === 'string') {
    return body['code'];
  }
  return null;
}

/**
 * The error's JSON body as an object, or null. Endpoints that return nothing (a delete, for example)
 * are read as text, so their error body arrives as a string and is parsed here.
 */
function errorBody(error: unknown): Record<string, unknown> | null {
  if (!(error instanceof HttpErrorResponse)) {
    return null;
  }
  let body: unknown = error.error;
  if (typeof body === 'string') {
    try {
      body = JSON.parse(body);
    } catch {
      return null;
    }
  }
  if (body !== null && typeof body === 'object') {
    return body as Record<string, unknown>;
  }
  return null;
}
