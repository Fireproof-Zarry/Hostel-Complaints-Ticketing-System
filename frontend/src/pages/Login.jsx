import { useState } from 'react';
import { GoogleLogin } from '@react-oauth/google';
import { useNavigate } from 'react-router-dom';
import { AlertCircle, ArrowRight, Clock3, ShieldCheck, Wrench } from 'lucide-react';
import { clearToken, getSessionNotice, saveToken, SESSION_MESSAGES } from '../auth/session';
import './Login.css';

const apiBaseUrl = (import.meta.env.VITE_API_URL || '/api').replace(/\/$/, '');

const LOGIN_TYPE_KEY = 'hostelfix.loginType';

const LOGIN_TABS = {
  STUDENT: {
    label: 'Student Login',
    copy: 'Students must sign in with their @smail.iitm.ac.in account. Other email addresses are not accepted.',
    // Only a hint for Google's account picker; the server enforces the domain
    hostedDomain: 'smail.iitm.ac.in',
  },
  ADMIN: {
    label: 'Staff Login',
    copy: 'For hostel staff and administrators. Use the email address an administrator added for you.',
  },
};

// Automatic sign-in after a session ends reuses the tab chosen last on this browser
function loadLoginType() {
  try {
    return localStorage.getItem(LOGIN_TYPE_KEY) === 'ADMIN' ? 'ADMIN' : 'STUDENT';
  } catch {
    return 'STUDENT';
  }
}

function saveLoginType(loginType) {
  try {
    localStorage.setItem(LOGIN_TYPE_KEY, loginType);
  } catch {
    // Only a convenience
  }
}

async function loginError(response) {
  try {
    const body = await response.json();
    if (body.message) return body.message;
  } catch {
    // No readable reason
  }
  return 'Unable to sign in right now. Please try again later.';
}

/** Student Login and Staff Login tabs; the server decides who gets in. */
export default function Login() {
  const navigate = useNavigate();
  const [loginType, setLoginType] = useState(loadLoginType);
  const [error, setError] = useState('');
  const [isSigningIn, setIsSigningIn] = useState(false);
  // Set when the previous session ended on its own (expired / rejected token)
  const [sessionNotice] = useState(getSessionNotice);

  const selectLoginType = (nextType) => {
    setLoginType(nextType);
    saveLoginType(nextType);
    setError('');
  };

  const handleSuccess = async (credentialResponse) => {
    if (!credentialResponse.credential) {
      setError('Google did not return a sign-in credential. Please try again.');
      return;
    }

    setError('');
    setIsSigningIn(true);

    try {
      const response = await fetch(`${apiBaseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ credential: credentialResponse.credential, loginType }),
      });

      if (!response.ok) {
        throw new Error(await loginError(response));
      }

      const user = await response.json();
      if (!['ADMIN', 'STAFF', 'STUDENT'].includes(user.role)) {
        throw new Error('Your account does not have access to this portal.');
      }

      saveToken(credentialResponse.credential);
      // Hostel staff share the admin dashboard
      navigate(user.role === 'STUDENT' ? '/student' : '/admin', { replace: true });
    } catch (signInError) {
      clearToken();
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
              <span>Sign in with your Google account.</span>
            </span>
            <ArrowRight className="login-feature-arrow" aria-hidden="true" />
          </div>
        </section>

        <section className="login-panel" aria-labelledby="login-heading">
          <div className="login-panel-content">
            <span className="login-panel-mark"><Wrench aria-hidden="true" /></span>
            <p className="login-panel-kicker">WELCOME TO HOSTELFIX</p>
            <h2 id="login-heading" className="login-panel-title">Sign in to your account</h2>

            <div className="login-tabs" role="tablist" aria-label="Choose how to sign in">
              {Object.entries(LOGIN_TABS).map(([type, tab]) => (
                <button
                  key={type}
                  type="button"
                  role="tab"
                  aria-selected={loginType === type}
                  className={`login-tab${loginType === type ? ' is-active' : ''}`}
                  disabled={isSigningIn}
                  onClick={() => selectLoginType(type)}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            <p className="login-panel-copy">{LOGIN_TABS[loginType].copy}</p>

            {sessionNotice && !error && (
              <div className="login-notice" role="status">
                <Clock3 aria-hidden="true" />
                <span>{SESSION_MESSAGES[sessionNotice] ?? SESSION_MESSAGES.rejected}</span>
              </div>
            )}

            {error && (
              <div className="login-error" role="alert">
                <AlertCircle aria-hidden="true" />
                <span>{error}</span>
              </div>
            )}

            <div className={`google-login-wrap${isSigningIn ? ' is-loading' : ''}`}>
              {/* The key re-creates the button, so Google picks up each tab's settings */}
              <GoogleLogin
                key={loginType}
                hosted_domain={LOGIN_TABS[loginType].hostedDomain}
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
                // After an automatic session end, let Google sign returning users
                // straight back in; never after an explicit "Log out"
                useOneTap={Boolean(sessionNotice)}
                auto_select={Boolean(sessionNotice)}
              />
            </div>

            {isSigningIn && <p className="login-progress" role="status">Verifying your account…</p>}

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
