import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { GoogleOAuthProvider } from '@react-oauth/google'

import Login from './pages/Login'
import StudentHome from './pages/StudentHome'
import AdminHome from './pages/AdminHome'

export default function App() {
  console.log("Google Client ID:", import.meta.env.VITE_GOOGLE_CLIENT_ID)

  return (
    <GoogleOAuthProvider clientId={import.meta.env.VITE_GOOGLE_CLIENT_ID}>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Navigate to="/login" replace />} />
          <Route path="/login" element={<Login />} />
          <Route path="/student" element={<StudentHome />} />
          <Route path="/admin" element={<AdminHome />} />
        </Routes>
      </BrowserRouter>
    </GoogleOAuthProvider>
  )
}