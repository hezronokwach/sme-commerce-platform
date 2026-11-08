import { parseShillings, shillingsText } from './parse-shillings';

describe('parseShillings', () => {
  it('reads whole shillings', () => {
    expect(parseShillings('50')).toBe(5000);
    expect(parseShillings('0')).toBe(0);
  });

  it('reads one or two decimals', () => {
    expect(parseShillings('50.5')).toBe(5050);
    expect(parseShillings('50.05')).toBe(5005);
  });

  it('has no floating-point rounding error', () => {
    // Number('0.29') * 100 is 28.999999999999996; reading the cents as a whole number avoids that.
    expect(parseShillings('0.29')).toBe(29);
    expect(parseShillings('1.13')).toBe(113);
  });

  it('allows thousands separators and spaces around', () => {
    expect(parseShillings(' 1,200.75 ')).toBe(120075);
  });

  it('rejects what is not a price', () => {
    expect(parseShillings('')).toBeNull();
    expect(parseShillings('abc')).toBeNull();
    expect(parseShillings('-5')).toBeNull();
    expect(parseShillings('5.123')).toBeNull();
    expect(parseShillings('5.')).toBeNull();
    expect(parseShillings('.5')).toBeNull();
    expect(parseShillings('1.2.3')).toBeNull();
  });
});

describe('shillingsText', () => {
  it('writes minor units back for the input box', () => {
    expect(shillingsText(5050)).toBe('50.50');
    expect(shillingsText(5)).toBe('0.05');
    expect(shillingsText(120075)).toBe('1200.75');
  });
});
