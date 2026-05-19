import { createContext, useContext, useState, useEffect } from 'react'

const FeatureFlagContext = createContext({})

export function FeatureFlagProvider({ children }) {
  const [flags, setFlags] = useState({})
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    fetch('/api/v1/features')
      .then(res => res.json())
      .then(data => { setFlags(data); setLoaded(true) })
      .catch(() => setLoaded(true))
  }, [])

  return (
    <FeatureFlagContext.Provider value={{ flags, loaded }}>
      {children}
    </FeatureFlagContext.Provider>
  )
}

export function useFeatureFlags() {
  return useContext(FeatureFlagContext)
}

export function useFeature(name) {
  const { flags } = useContext(FeatureFlagContext)
  return flags[name] === true
}
