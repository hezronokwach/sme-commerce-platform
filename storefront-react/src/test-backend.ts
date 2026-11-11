import { vi } from 'vitest'

// A pretend backend for page tests. Each test says which path answers what; any other path gets 404.
// Every request's path is recorded, so a test can also check what the page did NOT ask for.

type FakeAnswer = { status: number; body: unknown }

export type FakeBackend = {
  askedFor: string[]
  answer: (pathAndQuery: string, status: number, body: unknown) => FakeBackend
  install: () => void
}

export function fakeBackend(): FakeBackend {
  const askedFor: string[] = []
  const answers = new Map<string, FakeAnswer>()

  async function fakeFetch(request: Request): Promise<Response> {
    const url = new URL(request.url)
    const pathAndQuery = url.pathname + url.search
    askedFor.push(pathAndQuery)

    let found = answers.get(pathAndQuery)
    if (!found) {
      found = { status: 404, body: { code: 'NOT_FOUND' } }
    }
    return new Response(JSON.stringify(found.body), {
      status: found.status,
      headers: { 'Content-Type': 'application/json' },
    })
  }

  const backend: FakeBackend = {
    askedFor: askedFor,
    answer(pathAndQuery, status, body) {
      answers.set(pathAndQuery, { status, body })
      return backend
    },
    install() {
      vi.stubGlobal('fetch', fakeFetch)
    },
  }
  return backend
}
