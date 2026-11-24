import { HttpErrorResponse } from '@angular/common/http';
import { codeFrom, messageFrom } from './error-message';

describe('messageFrom and codeFrom', () => {
  it('read the problem JSON the backend sends', () => {
    const error = new HttpErrorResponse({ status: 409, error: { code: 'CATEGORY_IN_USE', detail: 'Move its products first.' } });

    expect(messageFrom(error, 'fallback')).toBe('Move its products first.');
    expect(codeFrom(error)).toBe('CATEGORY_IN_USE');
  });

  it('also read it when it arrives as text, as it does for endpoints that return nothing', () => {
    const error = new HttpErrorResponse({ status: 409, error: '{"code":"CATEGORY_IN_USE","detail":"Move its products first."}' });

    expect(messageFrom(error, 'fallback')).toBe('Move its products first.');
    expect(codeFrom(error)).toBe('CATEGORY_IN_USE');
  });

  it('fall back when there is no readable body', () => {
    const error = new HttpErrorResponse({ status: 500, error: 'Internal error' });

    expect(messageFrom(error, 'fallback')).toBe('fallback');
    expect(codeFrom(error)).toBeNull();
  });
});
