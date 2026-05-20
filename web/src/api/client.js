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

    if (status === 401 || status === 403) {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('user')
      window.location.href = '/login'
    } else if (status >= 500) {
      // Server errors — always toast (components rarely handle these)
      toast.error('Server error', {
        description: errorData?.message || 'Something went wrong. Please try again.',
      })
    } else if (status === 429) {
      // Rate limiting — always toast unless on auth pages (login handles its own countdown)
      const silentEndpoints = ['/auth/login', '/auth/register']
      if (!silentEndpoints.some(e => url.includes(e))) {
        toast.warning('Too many requests', {
          description: errorData?.message || 'Please wait and try again.',
        })
      }
    }
    // 400/404 errors are NOT toasted here — components handle their own error messages
    // to avoid duplicate toasts. Components call toast.error() in their catch blocks.

    return Promise.reject(errorData?.message ? errorData : err)
  }
)

export default client
