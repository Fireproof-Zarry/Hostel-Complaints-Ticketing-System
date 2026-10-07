import { useEffect, useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { GoogleOAuthProvider } from '@react-oauth/google'

import Login from './pages/Login'
import StudentHome from './pages/StudentHome'
import AdminHome from './pages/AdminHome'

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

function RoleRoute({ requiredRole, children }) {
  const token = localStorage.getItem('idToken')
  const [access, setAccess] = useState(() => (
    token ? { status: 'checking' } : { status: 'unauthenticated' }
  ))
  const [retry, setRetry] = useState(0)

  useEffect(() => {
    if (!token) return undefined

    const controller = new AbortController()
    fetch(`${apiBaseUrl}/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: controller.signal,
    })
      .then(async (response) => {
        if (response.status === 401) {
          localStorage.removeItem('idToken')
          setAccess({ status: 'unauthenticated' })
          return
        }
        if (!response.ok) {
          throw new Error(`Could not verify your account (${response.status}).`)
        }

        const user = await response.json()
        setAccess({
          status: user.role === requiredRole ? 'allowed' : 'notFound',
        })
      })
      .catch((error) => {
        if (error.name !== 'AbortError') {
          setAccess({ status: 'error', message: error.message })
        }
      })

    return () => controller.abort()
  }, [requiredRole, retry, token])

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

export default function App() {
  return (
    <GoogleOAuthProvider clientId={import.meta.env.VITE_GOOGLE_CLIENT_ID}>
      <BrowserRouter>
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
              <RoleRoute requiredRole="ADMIN">
                <AdminHome />
              </RoleRoute>
            }
          />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </BrowserRouter>
    </GoogleOAuthProvider>
  )
}