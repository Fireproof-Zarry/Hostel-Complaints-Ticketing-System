import { useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { GoogleOAuthProvider } from '@react-oauth/google'

import Login from './pages/Login'
import StudentHome from './pages/StudentHome'
import AdminHome from './pages/AdminHome'
import Profile from './pages/Profile'
import {
  apiFetch,
  endSession,
  getToken,
  getTokenExpiry,
  onSessionEnded,
  SessionEndedError,
  TOKEN_KEY,
} from './auth/session'

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '')

function NotFound() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-slate-100 px-6 text-center text-slate-900">
      <div>
        <p className="text-sm font-semibold uppercase tracking-widest text-indigo-600">404 error</p>
        <h1 className="mt-3 text-4xl font-bold">Page not found</h1>
        <p className="mt-3 text-slate-600">The page you requested doesn’t exist.</p>
        <a
          href="/"
          className="mt-6 inline-flex rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-indigo-700"
        >
          Return to sign in
        </a>
      </div>
    </main>
  )
}

// requiredRole may be a single role or a list of allowed roles
function RoleRoute({ requiredRole, children }) {
  // A string key keeps the effect stable when an inline array is passed
  const allowedRoles = [requiredRole].flat().join(',')
  const token = getToken()
  const [access, setAccess] = useState(() => (
    token ? { status: 'checking' } : { status: 'unauthenticated' }
  ))
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    if (!token) return undefined

    const controller = new AbortController()
    // apiFetch ends the session (and SessionWatcher redirects) for expired or rejected tokens
    apiFetch(`${apiBaseUrl}/auth/me`, { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok) {
          throw new Error(`Could not verify your account (${response.status}).`)
        }

        const user = await response.json()
        setAccess({
          status: allowedRoles.split(',').includes(user.role) ? 'allowed' : 'notFound',
        })
      })
      .catch((error) => {
        if (error instanceof SessionEndedError) {
          setAccess({ status: 'unauthenticated' })
        } else if (error.name !== 'AbortError') {
          setAccess({ status: 'error', message: error.message })
        }
      })

    return () => controller.abort()
  }, [allowedRoles, retry, token])

  if (access.status === 'unauthenticated') {
    return <Navigate to="/login" replace />
  }
  if (access.status === 'notFound') {
    return <NotFound />
  }
  if (access.status === 'error') {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-100 px-6 text-center text-slate-900">
        <div>
          <h1 className="text-2xl font-bold">Unable to verify access</h1>
          <p className="mt-2 text-slate-600">{access.message}</p>
          <button
            type="button"
            onClick={() => {
              setAccess({ status: 'checking' })
              setRetry((current) => current + 1)
            }}
            className="mt-5 rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-indigo-700"
          >
            Try again
          </button>
        </div>
      </main>
    )
  }
  if (access.status !== 'allowed') {
    return (
      <main className="flex min-h-screen items-center justify-center bg-slate-100 text-slate-600" role="status">
        Verifying account…
      </main>
    )
  }

  return children
}

/**
 * Sends the user to sign in when the session ends: when any API call reports
 * an expired/rejected token, when the token's hour runs out on an open page,
 * or when they log out in another tab.
 */
function SessionWatcher() {
  const navigate = useNavigate()
  const location = useLocation()
  // Bumped when another tab stores a new token, so the expiry timer follows it
  const [tokenVersion, setTokenVersion] = useState(0)

  useEffect(() => onSessionEnded(() => navigate('/login', { replace: true })), [navigate])

  useEffect(() => {
    if (location.pathname === '/login') return undefined
    const token = getToken()
    const expiry = getTokenExpiry(token)
    if (expiry === null) return undefined
    // Same one-minute margin as isTokenExpired; setTimeout caps at ~24.8 days
    const delay = Math.min(Math.max(expiry - 60 * 1000 - Date.now(), 0), 2 ** 31 - 1)
    // Ends the session only if this token is still the stored one
    const timer = setTimeout(() => endSession('expired', token), delay)
    return () => clearTimeout(timer)
  }, [location.pathname, tokenVersion])

  useEffect(() => {
    const handleStorage = (event) => {
      if (event.key !== TOKEN_KEY) return
      if (event.newValue) {
        setTokenVersion((version) => version + 1)
      } else if (window.location.pathname !== '/login') {
        // Logged out in another tab: this tab's session is gone too
        navigate('/login', { replace: true })
      }
    }
    window.addEventListener('storage', handleStorage)
    return () => window.removeEventListener('storage', handleStorage)
  }, [navigate])

  return null
}

export default function App() {
  return (
    <GoogleOAuthProvider clientId={import.meta.env.VITE_GOOGLE_CLIENT_ID}>
      <BrowserRouter>
        <SessionWatcher />
        <Routes>
          <Route path="/" element={<Navigate to="/login" replace />} />
          <Route path="/login" element={<Login />} />
          <Route
            path="/student"
            element={
              <RoleRoute requiredRole="STUDENT">
                <StudentHome />
              </RoleRoute>
            }
          />
          <Route
            path="/admin"
            element={
              <RoleRoute requiredRole={['ADMIN', 'STAFF']}>
                <AdminHome />
              </RoleRoute>
            }
          />
          <Route
            path="/profile"
            element={
              <RoleRoute requiredRole="STUDENT">
                <Profile />
              </RoleRoute>
            }
          />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </BrowserRouter>
    </GoogleOAuthProvider>
  )
}