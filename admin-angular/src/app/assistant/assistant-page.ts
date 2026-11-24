import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../api/api';
import { assistantDocumentDelete } from '../api/fn/assistant-document/assistant-document-delete';
import { assistantDocumentList } from '../api/fn/assistant-document/assistant-document-list';
import { assistantDocumentReindexProducts } from '../api/fn/assistant-document/assistant-document-reindex-products';
import { assistantDocumentUpload } from '../api/fn/assistant-document/assistant-document-upload';
import { DocumentResponse } from '../api/models/document-response';
import { AuthService } from '../auth/auth.service';
import { formatDateTime } from '../orders/order-labels';
import { messageFrom } from '../shared/error-message';
import { textOrEmpty } from '../shared/text-or-empty';

/** The backend's limit for one document (AssistantDocumentService). */
export const MAX_DOCUMENT_BYTES = 2 * 1024 * 1024;

/** One uploaded document as this page shows it. */
export interface DocumentRow {
  id: string;
  title: string;
  status: string;
  statusText: string;
  error: string;
  uploaded: string;
}

/** What the owner sees: the uploaded files, and how many products the assistant has learned. */
export interface DocumentLists {
  uploads: DocumentRow[];
  productCount: number;
  stillWorking: boolean;
}

/** Splits the backend's list into the owner's uploads and the documents written from products. */
export function toLists(documents: DocumentResponse[]): DocumentLists {
  const uploads: DocumentRow[] = [];
  let productCount = 0;
  let stillWorking = false;
  for (const document of documents) {
    if (document.status === 'PROCESSING') {
      stillWorking = true;
    }
    if (document.sourceType === 'PRODUCT') {
      productCount++;
    } else {
      uploads.push({
        id: textOrEmpty(document.id),
        title: textOrEmpty(document.title),
        status: textOrEmpty(document.status),
        statusText: statusText(document.status),
        error: textOrEmpty(document.error),
        uploaded: formatDateTime(document.createdAt),
      });
    }
  }
  return { uploads: uploads, productCount: productCount, stillWorking: stillWorking };
}

function statusText(status: string | undefined): string {
  if (status === 'READY') {
    return 'Ready';
  }
  if (status === 'FAILED') {
    return 'Failed';
  }
  return 'Reading…';
}

/**
 * The assistant at /assistant: the shop's own texts it answers from (delivery, returns, opening
 * hours...). Owners upload and delete them; staff can see the list. A new document is "Reading…"
 * while it is split and indexed, then "Ready"; the page checks again every few seconds until then.
 */
@Component({
  selector: 'app-assistant-page',
  imports: [FormsModule],
  templateUrl: './assistant-page.html',
  styleUrl: './assistant-page.css',
})
export class AssistantPage implements OnInit, OnDestroy {
  private readonly api = inject(Api);
  private readonly auth = inject(AuthService);

  protected readonly uploads = signal<DocumentRow[]>([]);
  protected readonly productCount = signal(0);
  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly infoMessage = signal<string | null>(null);

  protected title = '';
  private file: File | null = null;
  private recheck: ReturnType<typeof setTimeout> | null = null;

  async ngOnInit(): Promise<void> {
    await this.reload();
  }

  ngOnDestroy(): void {
    this.stopRechecking();
  }

  protected isOwner(): boolean {
    return this.auth.isOwner();
  }

  protected fileChosen(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file = null;
    if (input.files !== null && input.files.length > 0) {
      this.file = input.files[0];
      // A handy title from the file name, until the owner types their own.
      if (this.title.trim() === '') {
        this.title = titleFromFileName(this.file.name);
      }
    }
  }

  protected async upload(form: HTMLFormElement): Promise<void> {
    this.errorMessage.set(null);
    this.infoMessage.set(null);
    const file = this.file;
    if (file === null) {
      this.errorMessage.set('Choose a .txt or .md file first.');
      return;
    }
    const name = file.name.toLowerCase();
    if (!name.endsWith('.txt') && !name.endsWith('.md')) {
      this.errorMessage.set('Only .txt and .md files can be uploaded.');
      return;
    }
    if (file.size > MAX_DOCUMENT_BYTES) {
      this.errorMessage.set('The file is larger than 2 MB.');
      return;
    }
    if (this.title.trim() === '') {
      this.errorMessage.set('Give the document a title, for example "Delivery and returns".');
      return;
    }

    this.busy.set(true);
    try {
      await this.api.invoke(assistantDocumentUpload, { title: this.title.trim(), body: { file: file } });
      this.title = '';
      this.file = null;
      form.reset();
    } catch (error) {
      this.errorMessage.set(messageFrom(error, 'The document could not be uploaded. Please try again.'));
    } finally {
      this.busy.set(false);
    }
    await this.reload();
  }

  protected async remove(row: DocumentRow): Promise<void> {
    if (!confirm('Delete "' + row.title + '"? The assistant will stop using it.')) {
      return;
    }
    this.errorMessage.set(null);
    try {
      await this.api.invoke(assistantDocumentDelete, { documentId: row.id });
    } catch (error) {
      this.errorMessage.set(messageFrom(error, 'The document could not be deleted. Please try again.'));
    }
    await this.reload();
  }

  /** Teaches the assistant every product on sale now; unchanged products are skipped. */
  protected async learnProducts(): Promise<void> {
    this.errorMessage.set(null);
    this.infoMessage.set(null);
    this.busy.set(true);
    try {
      await this.api.invoke(assistantDocumentReindexProducts);
      this.infoMessage.set('The assistant is reading your products now.');
    } catch (error) {
      this.errorMessage.set(messageFrom(error, 'That did not work. Please try again.'));
    } finally {
      this.busy.set(false);
    }
    await this.reload();
  }

  private async reload(): Promise<void> {
    this.stopRechecking();
    try {
      const lists = toLists(await this.api.invoke(assistantDocumentList));
      this.uploads.set(lists.uploads);
      this.productCount.set(lists.productCount);
      if (lists.stillWorking) {
        this.recheck = setTimeout(() => this.reload(), 3000);
      }
    } catch (error) {
      this.errorMessage.set(messageFrom(error, 'The documents could not be loaded. Please reload the page.'));
    } finally {
      this.loading.set(false);
    }
  }

  private stopRechecking(): void {
    if (this.recheck !== null) {
      clearTimeout(this.recheck);
      this.recheck = null;
    }
  }
}

/** "delivery-and-returns.md" becomes "Delivery and returns". */
export function titleFromFileName(fileName: string): string {
  let base = fileName;
  const dot = base.lastIndexOf('.');
  if (dot > 0) {
    base = base.substring(0, dot);
  }
  let words = '';
  for (const character of base) {
    if (character === '-' || character === '_') {
      words = words + ' ';
    } else {
      words = words + character;
    }
  }
  words = words.trim();
  if (words === '') {
    return '';
  }
  return words.charAt(0).toUpperCase() + words.substring(1);
}
