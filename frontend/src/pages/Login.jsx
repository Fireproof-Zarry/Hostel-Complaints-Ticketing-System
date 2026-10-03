import { GoogleLogin } from '@react-oauth/google'
import { useNavigate } from 'react-router-dom'

export default function Login() {
  const navigate = useNavigate()

  const handleSuccess = async (credentialResponse) => {
    const idToken = credentialResponse.credential

    // Store the Google ID token
    localStorage.setItem('idToken', idToken)

    try {
      const response = await fetch('http://localhost:8080/auth/me', {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${idToken}`,
        },
      })

      if (!response.ok) {
        throw new Error(`Authentication failed: ${response.status}`)
      }

      const user = await response.json()

      console.log('Logged-in user:', user)

      // Redirect based on role
      if (user.role === 'ADMIN') {
        navigate('/admin')
      } else {
        navigate('/student')
      }
    } catch (error) {
      console.error('Login error:', error)
    }
  }

  const handleError = () => {
    console.log('Google login failed')
  }

  return (
    <div>
      <h1>Hostel Complaints System</h1>

      <GoogleLogin
        onSuccess={handleSuccess}
        onError={handleError}
      />
    </div>
  )
}