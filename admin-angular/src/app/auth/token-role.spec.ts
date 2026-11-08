import { roleFromToken } from './token-role';

describe('roleFromToken', () => {
  it('reads the role from the token payload', () => {
    const token = fakeToken({ sub: 'u1', tid: 't1', role: 'OWNER' });

    expect(roleFromToken(token)).toBe('OWNER');
  });

  it('reads a payload that uses base64url characters', () => {
    // "~~~" makes base64 produce "+" or "/", which base64url writes as "-" and "_".
    const token = fakeToken({ role: 'STAFF', name: '~~~???' });

    expect(roleFromToken(token)).toBe('STAFF');
  });

  it('answers null for something that is not a token', () => {
    expect(roleFromToken('not-a-token')).toBeNull();
    expect(roleFromToken('a.b.c')).toBeNull();
  });

  it('answers null when the token has no role', () => {
    expect(roleFromToken(fakeToken({ sub: 'u1' }))).toBeNull();
  });

  /** header.payload.signature, encoded like the backend does. The signature isn't checked here. */
  function fakeToken(payload: object): string {
    const base64 = btoa(JSON.stringify(payload));
    const base64Url = base64.replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
    return 'eyJhbGciOiJIUzI1NiJ9.' + base64Url + '.signature';
  }
});
