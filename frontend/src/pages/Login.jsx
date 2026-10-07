import { useState } from 'react';
import { GoogleLogin } from '@react-oauth/google';
import { useNavigate } from 'react-router-dom';
import { AlertCircle, ArrowRight, ShieldCheck, Wrench } from 'lucide-react';
import './Login.css';

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');

export default function Login() {
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [isSigningIn, setIsSigningIn] = useState(false);

  const handleSuccess = async (credentialResponse) => {
    if (!credentialResponse.credential) {
      setError('Google did not return a sign-in credential. Please try again.');
      return;
    }

    setError('');
    setIsSigningIn(true);

    try {
      const response = await fetch(`${apiBaseUrl}/auth/me`, {
        headers: {
          Authorization: `Bearer ${credentialResponse.credential}`,
        },
      });

      if (!response.ok) {
        const message = await response.text();
        if (response.status === 403) {
          throw new Error(message || 'Please use your student email address to sign in.');
        }
        if (response.status === 401) {
          throw new Error(
            'The backend rejected Google’s sign-in token. Check the backend logs for a token validation error, then restart the backend.'
          );
        }
        throw new Error('Unable to sign in right now. Please try again later.');
      }

      const user = await response.json();
      if (user.role !== 'ADMIN' && user.role !== 'STUDENT') {
        throw new Error('Your account does not have access to this portal.');
      }

      localStorage.setItem('idToken', credentialResponse.credential);
      navigate(user.role === 'ADMIN' ? '/admin' : '/student', { replace: true });
    } catch (signInError) {
      localStorage.removeItem('idToken');
      setError(signInError instanceof Error ? signInError.message : 'Sign-in failed. Please try again.');
    } finally {
      setIsSigningIn(false);
    }
  };

  return (
    <main className="login-page">
      <div className="login-layout">
        <section className="login-intro" aria-label="HostelFix introduction">
          <div className="login-brand">
            <span className="login-brand-icon"><Wrench aria-hidden="true" /></span>
            <span>
              <span className="login-brand-name">HostelFix</span>
              <span className="login-brand-caption">Student Portal</span>
            </span>
          </div>

          <div className="login-welcome">
            <span className="login-eyebrow">HOSTEL SUPPORT, MADE SIMPLE</span>
            <h1 className="login-title">A better stay starts with being heard.</h1>
            <p className="login-description">
              Report hostel issues, follow their progress, and help make your campus a better place to live.
            </p>
          </div>

          <div className="login-feature">
            <span className="login-feature-icon"><ShieldCheck aria-hidden="true" /></span>
            <span>
              <strong>Secure campus access</strong>
              <span>Sign in with your institute email account.</span>
            </span>
            <ArrowRight className="login-feature-arrow" aria-hidden="true" />
          </div>
        </section>

        <section className="login-panel" aria-labelledby="login-heading">
          <div className="login-panel-content">
            <span className="login-panel-mark"><Wrench aria-hidden="true" /></span>
            <p className="login-panel-kicker">WELCOME TO HOSTELFIX</p>
            <h2 id="login-heading" className="login-panel-title">Sign in to your account</h2>
            <p className="login-panel-copy">
              Students can use their institute email. Approved administrators can sign in with their registered account.
            </p>

            {error && (
              <div className="login-error" role="alert">
                <AlertCircle aria-hidden="true" />
                <span>{error}</span>
              </div>
            )}

            <div className={`google-login-wrap${isSigningIn ? ' is-loading' : ''}`}>
              <GoogleLogin
                onSuccess={handleSuccess}
                onError={() => {
                  setIsSigningIn(false);
                  setError('Google sign-in was cancelled or could not be completed. Please try again.');
                }}
                theme="filled_blue"
                shape="pill"
                size="large"
                text="continue_with"
                width="320"
                ux_mode="popup"
                useOneTap={false}
              />
            </div>

            <p className="login-email-note">
              Students: <strong>@smail.iitm.ac.in</strong> · Administrators: approved email accounts only.
            </p>
            {isSigningIn && <p className="login-progress" role="status">Verifying your institute account…</p>}

            <div className="login-divider"><span>SECURE SIGN-IN</span></div>
            <p className="login-privacy">
              Your Google account is used only to verify access to the hostel portal.
            </p>
          </div>
          <p className="login-footer">HostelFix · Student support portal</p>
        </section>
      </div>
    </main>
  );
}
