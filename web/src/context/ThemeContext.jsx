import { createContext, useContext, useState, useEffect, useCallback } from 'react'
import { useAuth } from './AuthContext'
import client from '../api/client'

const ThemeContext = createContext()

const FALLBACK_THEMES = {
  dark: {
    bg: '#0f172a', 'bg-card': '#1e293b', 'bg-card-hover': '#1e293b',
    border: '#334155', text: '#f1f5f9', 'text-muted': '#94a3b8',
    'text-secondary': '#64748b', primary: '#3b82f6', 'primary-hover': '#2563eb',
    green: '#22c55e', red: '#ef4444', amber: '#f59e0b',
    'sidebar-bg': '#1e293b', 'sidebar-border': '#334155',
    'hover-bg': 'rgba(255,255,255,0.05)', 'input-bg': '#334155', 'input-border': '#475569',
  },
  light: {
    bg: '#f1f5f9', 'bg-card': '#ffffff', 'bg-card-hover': '#f8fafc',
    border: '#e2e8f0', text: '#0f172a', 'text-muted': '#64748b',
    'text-secondary': '#475569', primary: '#3b82f6', 'primary-hover': '#2563eb',
    green: '#16a34a', red: '#dc2626', amber: '#d97706',
    'sidebar-bg': '#ffffff', 'sidebar-border': '#e2e8f0',
    'hover-bg': 'rgba(0,0,0,0.04)', 'input-bg': '#f1f5f9', 'input-border': '#cbd5e1',
  },
}

function applyColors(colors) {
  const root = document.documentElement
  Object.entries(colors).forEach(([key, val]) => {
    root.style.setProperty(`--${key}`, val)
  })
  const s = document.getElementById('theme-init')
  if (s) s.textContent = `html, body { background-color: ${colors.bg}; color: ${colors.text}; }`
}

export function ThemeProvider({ children }) {
  const { user } = useAuth()
  const [themes, setThemes] = useState([])
  const [currentThemeId, setCurrentThemeId] = useState(null)
  const [fallbackKey, setFallbackKey] = useState(() => localStorage.getItem('themeFallback') || 'dark')

  const setThemeById = useCallback(async (themeId) => {
    try {
      const theme = themes.find((t) => t.id === themeId)
      if (!theme) return
      const colors = JSON.parse(theme.colorsJson)
      if (!colors || typeof colors !== 'object') return
      applyColors(colors)
      setCurrentThemeId(themeId)
      localStorage.setItem('themeId', themeId)
      localStorage.setItem('themeColors', theme.colorsJson)
      await client.put('/users/theme', { themeId }).catch(() => {})
    } catch (e) {
      console.error('setThemeById error:', e)
    }
  }, [themes])

  const setFallback = useCallback((key) => {
    setCurrentThemeId(null)
    setFallbackKey(key)
    document.documentElement.setAttribute('data-theme', key)
    applyColors(FALLBACK_THEMES[key])
    localStorage.removeItem('themeId')
    localStorage.removeItem('themeColors')
    localStorage.setItem('themeFallback', key)
    client.put('/users/theme', { themeId: '' }).catch(() => {})
  }, [])

  const toggleTheme = useCallback(() => {
    if (currentThemeId) {
      const newKey = fallbackKey === 'dark' ? 'light' : 'dark'
      setCurrentThemeId(null)
      setFallbackKey(newKey)
      document.documentElement.setAttribute('data-theme', newKey)
      applyColors(FALLBACK_THEMES[newKey])
      localStorage.removeItem('themeId')
      localStorage.removeItem('themeColors')
      localStorage.setItem('themeFallback', newKey)
      client.put('/users/theme', { themeId: '' }).catch(() => {})
    } else {
      const newKey = fallbackKey === 'dark' ? 'light' : 'dark'
      setFallbackKey(newKey)
      document.documentElement.setAttribute('data-theme', newKey)
      applyColors(FALLBACK_THEMES[newKey])
      localStorage.setItem('themeFallback', newKey)
    }
  }, [currentThemeId, fallbackKey])

  useEffect(() => {
    if (!user) {
      setThemes([])
      return
    }
    client.get('/themes').then((res) => {
      const list = res.data
      setThemes(list)
      const savedId = localStorage.getItem('themeId')
      if (savedId) {
        const match = list.find((t) => t.id === savedId)
        if (match) setCurrentThemeId(savedId)
      }
    }).catch(() => {})
  }, [user])

  const currentTheme = currentThemeId
    ? themes.find((t) => t.id === currentThemeId)
    : null

  return (
    <ThemeContext.Provider value={{
      themes, currentThemeId, currentTheme, setThemeById,
      fallbackKey, setFallback, toggleTheme,
      isCustom: !!currentThemeId,
      theme: currentTheme ? currentTheme.name : fallbackKey,
    }}>
      {children}
    </ThemeContext.Provider>
  )
}

export const useTheme = () => useContext(ThemeContext)
