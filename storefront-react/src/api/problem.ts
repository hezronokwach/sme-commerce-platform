// Every backend error has the same shape (ADR 0006, RFC 9457 "problem details"):
//   {"status": 409, "code": "INSUFFICIENT_STOCK", "title": "...", "detail": "...", ...extra fields}
// The page decides what to do from code, never from the words in detail or title.
//
// The API description doesn't list error bodies, so openapi-fetch can't type them; this reads one safely.

export type Problem = {
  code: string
  detail: string
  productId?: string
  available?: number
}

export function readProblem(body: unknown): Problem {
  const problem: Problem = { code: 'UNKNOWN', detail: '' }
  if (typeof body !== 'object' || body === null) {
    return problem
  }
  const fields = body as Record<string, unknown>
  if (typeof fields.code === 'string') {
    problem.code = fields.code
  }
  if (typeof fields.detail === 'string') {
    problem.detail = fields.detail
  }
  if (typeof fields.productId === 'string') {
    problem.productId = fields.productId
  }
  if (typeof fields.available === 'number') {
    problem.available = fields.available
  }
  return problem
}
