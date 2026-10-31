# 0004: JWT access tokens via Spring Resource Server, HS256, rotating refresh tokens

Status: Accepted (2026-11-01)

## Context

The admin (Angular) and later the storefront (React) call a stateless API. Requests must prove who the
user is and which shop they belong to, without a server-side session. Login must not reveal which
emails have accounts, and a stolen token must stop working quickly.

## Decision

**Access token:** a signed JWT, checked by Spring Security's OAuth2 Resource Server
(`oauth2ResourceServer().jwt()`) with our own `JwtEncoder` / `JwtDecoder` beans in
`identity/internal/security`. No hand-written token filter.

| | Value |
| --- | --- |
| Algorithm | HS256, one secret from env `JWT_SECRET` (at least 32 bytes, checked at startup) |
| Lifetime | 15 minutes |
| Claims | `iss`, `sub` (user id), `tid` (tenant id), `role`, `typ` (STAFF/CUSTOMER), `iat`, `exp`, `jti` |
| Browser | memory only, sent as `Authorization: Bearer ...` |
| Admin API | `/api/admin/**` needs role OWNER or STAFF; a valid token with another role gets 403 |

HS256, not RS256: this app both issues and checks its tokens. A key pair only pays off when other
services must verify tokens without being able to mint them.

**Refresh token:** 32 random bytes, sent in an `HttpOnly; Secure; SameSite=Strict; Path=/api/auth`
cookie, stored only as its SHA-256 hash in `identity.refresh_tokens`. Lifetime 14 days.

- Each login starts a token family. Each refresh marks the current token used (one conditional
  `UPDATE`, safe under concurrency) and issues the next one in the same family.
- Presenting an already used token revokes the whole family: two parties hold it, one likely a thief.
- Logout revokes the family and clears the cookie.

**Login:** one bcrypt check on every path (a dummy hash for unknown emails) and one generic 401 for
unknown email, wrong password and inactive account. Five wrong passwords lock the account for
15 minutes (429), stored in `staff_users.locked_until` (V21).

**CSRF** stays disabled: the API authenticates with a header the browser never adds by itself, and the
only cookie is `SameSite=Strict` and limited to `/api/auth`.

## Consequences

- A disabled user or changed role takes effect at the next refresh, so within 15 minutes, not at once.
  Instant revocation would need a token blocklist (by `jti`), which we don't build now.
- `SameSite=Strict` only works while the admin and the API share a site. Separate Vercel and Render
  domains need a shared custom domain or a configurable `SameSite=None` (deployment issue).
- Rotating the `JWT_SECRET` logs everyone out once: access tokens fail, users log in again.
- Failed-login counting can lose an increment when two attempts land at the same instant; real brute
  force protection is the rate-limiting issue.
