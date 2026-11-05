# 0006: One error shape everywhere, with a correlation id

Status: Accepted (2026-11-05)

## Context

Errors came from three places with three shapes: our `BaseException` subclasses (problem JSON with a
`code`), Spring MVC (validation, broken JSON, wrong method), and Spring Security (401/403 with an empty
body). The admin and storefront need one shape to handle, and a failed request must be findable in the
logs (Guide 1.4).

## Decision

- **One handler:** `GlobalErrorHandler` (`@RestControllerAdvice` extending Spring's
  `ResponseEntityExceptionHandler`) next to the application class. Every body passes through its
  `createResponseEntity`, which adds what RFC 9457 doesn't have.
- **The shape:** RFC 9457 fields `title`, `status`, `detail` (the human message), `instance`, plus
  `code` (stable, for programs), `correlationId` and `timestamp`. Validation adds
  `errors: [{field, message}]`. `type` is omitted while it is the default `about:blank`.
- **Codes:** our exceptions bring their own; `VALIDATION_FAILED`, `CONCURRENT_UPDATE` (optimistic lock),
  `UNAUTHENTICATED`, `FORBIDDEN`, `INTERNAL_ERROR`; Spring's own errors use the status name
  (`BAD_REQUEST`, `METHOD_NOT_ALLOWED`, ...).
- **500s** return a generic message; the exception goes only to the log.
- **Security errors** reach the same handler: identity's `SecurityConfig` passes 401/403 to Spring
  MVC's `HandlerExceptionResolver`, keeping the standard `WWW-Authenticate: Bearer` header.
- **Correlation id:** `CorrelationIdFilter` runs first, reuses a safe `X-Correlation-Id` from the caller
  (1–64 letters, digits, `.`, `_`, `-`) or makes a UUID, puts it in the MDC (every log line, via
  `logging.pattern.correlation`), the response header and every error body.
- `spring.mvc.problemdetails.enabled` is not used: our handler replaces Boot's.

## Consequences

- A new exception only needs to extend `BaseException` with a code; it gets the full shape for free.
- Errors raised outside Spring MVC and Spring Security (for example inside a servlet filter) still use
  Boot's default `/error` body. None exist today.
- The frontends can show `detail` and branch on `code`; support asks for the `correlationId`.
