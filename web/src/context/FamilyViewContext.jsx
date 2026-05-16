import { createContext, useContext, useState, useEffect, useCallback } from 'react'
import client from '../api/client'

const FamilyViewContext = createContext()

export function FamilyViewProvider({ children }) {
  const [view, setView] = useState(() => localStorage.getItem('familyView') || 'self')
  const [pendingCount, setPendingCount] = useState(0)
  const [families, setFamilies] = useState([])
  const [refreshKey, setRefreshKey] = useState(0)

  const setViewAndPersist = (v) => {
    setView(v)
    localStorage.setItem('familyView', v)
    setRefreshKey((k) => k + 1)
  }

  const viewParam = view === 'family' ? '?view=f' : ''

  const fetchPending = useCallback(async () => {
    try {
      const { data } = await client.get('/families/invitations/pending')
      setPendingCount(data?.length || 0)
    } catch { setPendingCount(0) }
  }, [])

  const fetchFamilies = useCallback(async () => {
    try {
      const { data } = await client.get('/families')
      setFamilies(data || [])
    } catch { setFamilies([]) }
  }, [])

  useEffect(() => { fetchPending(); fetchFamilies() }, [fetchPending, fetchFamilies])

  return (
    <FamilyViewContext.Provider value={{ view, setView: setViewAndPersist, viewParam, refreshKey, pendingCount, fetchPending, families, fetchFamilies }}>
      {children}
    </FamilyViewContext.Provider>
  )
}

export const useFamilyView = () => useContext(FamilyViewContext)
