import { formatPrice } from './format-price';

describe('formatPrice', () => {
  it('turns cents into shillings with two decimals', () => {
    expect(formatPrice(5000, 'KES')).toBe('KES 50.00');
  });

  it('keeps single-digit cents with a leading zero', () => {
    expect(formatPrice(5005, 'KES')).toBe('KES 50.05');
  });

  it('shows small amounts', () => {
    expect(formatPrice(5, 'KES')).toBe('KES 0.05');
    expect(formatPrice(0, 'KES')).toBe('KES 0.00');
  });

  it('adds thousands separators', () => {
    expect(formatPrice(150000000, 'KES')).toBe('KES 1,500,000.00');
  });

  it('is empty when there is no price', () => {
    expect(formatPrice(undefined, 'KES')).toBe('');
  });
});
