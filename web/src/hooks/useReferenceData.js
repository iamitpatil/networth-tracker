import { useState, useEffect } from 'react'
import client from '../api/client'

const cache = {} // in-memory cache per category

/**
 * Hook to fetch dropdown options from the reference_data API.
 * Returns { options, loading } where options is [{value, label, metadata}].
 * Results are cached in memory for the session.
 */
export function useReferenceData(category) {
  const [options, setOptions] = useState(cache[category] || [])
  const [loading, setLoading] = useState(!cache[category])

  useEffect(() => {
    if (cache[category]) {
      setOptions(cache[category])
      setLoading(false)
      return
    }
    setLoading(true)
    // Use fetch directly to bypass auth interceptor (public endpoint)
    fetch(`/api/v1/reference-data/${category}`)
      .then(r => r.json())
      .then(data => {
        cache[category] = data
        setOptions(data)
      })
      .catch(() => setOptions([]))
      .finally(() => setLoading(false))
  }, [category])

  return { options, loading }
}

/**
 * Fetch all reference data in one call (for bulk preloading).
 */
export async function preloadReferenceData() {
  try {
    const r = await fetch('/api/v1/reference-data')
    const data = await r.json()
    Object.entries(data).forEach(([cat, items]) => { cache[cat] = items })
    return data
  } catch { return {} }
}

/**
 * Get cached options for a category (synchronous, returns [] if not loaded).
 */
export function getCachedOptions(category) {
  return cache[category] || []
}
