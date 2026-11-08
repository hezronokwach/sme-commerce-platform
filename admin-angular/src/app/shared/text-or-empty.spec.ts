import { textOrEmpty } from './text-or-empty';

describe('textOrEmpty', () => {
  it('keeps text', () => {
    expect(textOrEmpty('Sukuma')).toBe('Sukuma');
  });

  it('turns a missing field and a JSON null into empty text', () => {
    expect(textOrEmpty(undefined)).toBe('');
    // The backend sends "categoryId": null for a product without a category.
    expect(textOrEmpty(null)).toBe('');
  });
});
