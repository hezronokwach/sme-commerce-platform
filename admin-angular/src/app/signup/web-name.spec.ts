import { isValidWebName, webNameFrom } from './web-name';

describe('webNameFrom', () => {
  it('lowercases and joins words with single hyphens', () => {
    expect(webNameFrom("Kamau's Hardware & Paint")).toBe('kamau-s-hardware-paint');
  });

  it('never starts or ends with a hyphen', () => {
    expect(webNameFrom('  -- Mama Mboga! ')).toBe('mama-mboga');
  });

  it('keeps digits and cuts long names to 50 characters', () => {
    expect(webNameFrom('Duka 254')).toBe('duka-254');
    expect(webNameFrom('a'.repeat(60)).length).toBe(50);
  });

  it('leaves nothing for a name with no letters or digits', () => {
    expect(webNameFrom('!!!')).toBe('');
  });
});

describe('isValidWebName', () => {
  it('accepts what the backend accepts', () => {
    expect(isValidWebName('kamau-hardware')).toBe(true);
    expect(isValidWebName('abc')).toBe(true);
  });

  it('refuses short names, capitals and spaces', () => {
    expect(isValidWebName('ab')).toBe(false);
    expect(isValidWebName('Kamau')).toBe(false);
    expect(isValidWebName('kamau hardware')).toBe(false);
  });
});
