import axios from 'axios'
import { toast } from 'sonner'

const client = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
})

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  if (config.method === 'get' && localStorage.getItem('familyView') === 'family') {
    const sep = config.url.includes('?') ? '&' : '?'
    config.url += `${sep}view=f`
  }
  return config
})

client.interceptors.response.use(
  (res) => res,
  (err) => {
    const status = err.response?.status
    const errorData = err.response?.data || {}
    const url = err.config?.url || ''

    // Don't toast for 401/403 (handled below) or for known endpoints that may legitimately 404
    const silentEndpoints = ['/auth/login', '/auth/register', '/auth/refresh']
    const isSilent = silentEndpoints.some(e => url.includes(e)) || status === 401 || status === 403

    if (status === 401 || status === 403) {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('user')
      window.location.href = '/login'
    } else if (status >= 500) {
      toast.error('Server error', {
        description: errorData?.message || 'Something went wrong. Please try again.',
      })
    } else if (status === 429) {
      toast.warning('Too many requests', {
        description: errorData?.message || 'Please wait and try again.',
      })
    } else if (status === 404 && !isSilent) {
      // Only toast 404s for mutations (POST/PUT/DELETE), not GET
      const method = err.config?.method?.toUpperCase()
      if (method && method !== 'GET') {
        toast.error('Not found', {
          description: errorData?.message || 'The resource was not found.',
        })
      }
    } else if (status === 400 && !isSilent) {
      toast.error('Invalid request', {
        description: errorData?.message || errorData?.errors
          ? Object.values(errorData.errors || {}).join(', ')
          : 'Please check your input.',
      })
    }

    return Promise.reject(errorData?.message ? errorData : err)
  }
)

export default client
