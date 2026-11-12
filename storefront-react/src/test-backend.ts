import { vi } from 'vitest'

// A pretend backend for page tests. Each test says which path answers what; any other path gets 404.
// Every request is recorded (method, path, body), so a test can check what the page sent, and what it
// did NOT ask for.

type FakeAnswer = { status: number; body: unknown }

export type RecordedRequest = {
  method: string
  path: string
  body: unknown
  headers: Headers
}

export type FakeBackend = {
  askedFor: string[]
  requests: RecordedRequest[]
  answer: (pathAndQuery: string, status: number, body: unknown) => FakeBackend
  answerPost: (pathAndQuery: string, status: number, body: unknown) => FakeBackend
  install: () => void
}

export function fakeBackend(): FakeBackend {
  const askedFor: string[] = []
  const requests: RecordedRequest[] = []
  const answers = new Map<string, FakeAnswer>()

  async function fakeFetch(request: Request): Promise<Response> {
    const url = new URL(request.url)
    const pathAndQuery = url.pathname + url.search
    askedFor.push(pathAndQuery)

    let body: unknown = null
    const text = await request.text()
    if (text) {
      body = JSON.parse(text)
    }
    requests.push({ method: request.method, path: pathAndQuery, body, headers: request.headers })

    let found = answers.get(request.method + ' ' + pathAndQuery)
    if (!found) {
      found = { status: 404, body: { code: 'NOT_FOUND' } }
    }
    let responseBody: string | null = null
    if (found.body !== null && found.body !== undefined) {
      responseBody = JSON.stringify(found.body)
    }
    return new Response(responseBody, {
      status: found.status,
      headers: { 'Content-Type': 'application/json' },
    })
  }

  const backend: FakeBackend = {
    askedFor: askedFor,
    requests: requests,
    answer(pathAndQuery, status, body) {
      answers.set('GET ' + pathAndQuery, { status, body })
      return backend
    },
    answerPost(pathAndQuery, status, body) {
      answers.set('POST ' + pathAndQuery, { status, body })
      return backend
    },
    install() {
      vi.stubGlobal('fetch', fakeFetch)
    },
  }
  return backend
}
