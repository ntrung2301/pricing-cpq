import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { AuthProvider } from 'react-oidc-context'
import { MeasureCanvasApp } from './pricing-v2/MeasureCanvasApp.tsx'
import { measureCanvasOidcConfig } from './pricing-v2/measureCanvasAuthConfig.ts'

// S3-T8 (docs/decisions.md D-049): a separate Vite multi-page entry, not a
// route inside src/main.tsx/App.tsx — those are the Sprint 1/2 MUI screens
// another session is migrating in parallel (sprint-3.md's instructions:
// "S3-T8 chỉ cần build mới theo Ant Design, không cần tự migrate lại các
// màn cũ"); this keeps the two entirely independent so neither can merge-
// conflict with the other's in-flight work on App.tsx/main.tsx.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider {...measureCanvasOidcConfig}>
      <MeasureCanvasApp />
    </AuthProvider>
  </StrictMode>,
)
