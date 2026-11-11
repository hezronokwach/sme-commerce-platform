import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from './client'

// A first test, so `npm test` has something to run and the generated client is checked end to end:
// the typed call must build the right URL from the path and its parameters.
describe('api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('fills the shop slug into the products path and reads the JSON', async () => {
    const calls: string[] = []
    const fakeFetch = async (request: Request) => {
      calls.push(request.url)
      return new Response(JSON.stringify([{ name: 'Sukuma bundle', priceMinor: 5000 }]), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    }
    vi.stubGlobal('fetch', fakeFetch)

    const { data } = await api.GET('/api/store/{slug}/products', {
      params: { path: { slug: 'mamamboga' } },
    })

    expect(calls).toHaveLength(1)
    expect(calls[0]).toContain('/api/store/mamamboga/products')
    expect(data?.[0].name).toBe('Sukuma bundle')
  })
})
