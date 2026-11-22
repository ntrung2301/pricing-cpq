import { useAuth } from 'react-oidc-context'
import { Alert, Button } from '../ui'

export function LoginScreen() {
  const { activeNavigator, error, signinRedirect } = useAuth()
  const connecting = activeNavigator === 'signinRedirect'

  return (
    <main className="login">
      <div className="brand login-brand"><span className="brand-mark" aria-hidden="true" />CPQ</div>
      <section className="login-form" aria-labelledby="sign-in-title">
        <h1 id="sign-in-title">Sign in to CPQ</h1>
        <p>Use your organization account to continue.</p>
        {error && !connecting && <Alert type="error" showIcon title="We couldn’t sign you in. Please try again." />}
        <Button variant="primary" block loading={connecting} onClick={() => void signinRedirect()}>
          {connecting ? 'Connecting…' : 'Sign in'}
        </Button>
      </section>
      <aside className="login-preview">
        <div>
          <h2>Build a quote.<br />See how it was priced.</h2>
          <div className="pricing-preview">
            <h3>Sample pricing breakdown</h3>
            <dl>
              {[['List price', '100.00'], ['Volume discount', '−10.00'], ['Final unit price', '90.00']].map(([label, value]) => (
                <div key={label}><dt>{label}</dt><dd>{value}</dd></div>
              ))}
            </dl>
          </div>
        </div>
      </aside>
    </main>
  )
}
