// Adds DOM checks such as expect(element).toBeInTheDocument() to Vitest's expect.
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// Remove what each test rendered, so the next test starts with an empty page.
// Also put back the real fetch, if a test swapped in a pretend one, and forget saved carts.
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  window.localStorage.clear()
})
