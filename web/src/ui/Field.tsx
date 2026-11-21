import { useId, type CSSProperties, type ReactNode } from 'react'

interface FieldControlProps {
  id: string
  'aria-describedby'?: string
  'aria-invalid'?: true
  status?: 'error'
}

interface FieldProps {
  label: string
  hint?: string
  error?: string
  style?: CSSProperties
  children: (props: FieldControlProps) => ReactNode
}

export function Field({ label, hint, error, style, children }: FieldProps) {
  const id = useId()
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const description = [hintId, errorId].filter(Boolean).join(' ') || undefined

  return (
    <div className="ui-field" style={style}>
      <label htmlFor={id}>{label}</label>
      {children({
        id,
        'aria-describedby': description,
        'aria-invalid': error ? true : undefined,
        status: error ? 'error' : undefined,
      })}
      {hintId && <span id={hintId} className="ui-field-hint">{hint}</span>}
      {errorId && <span id={errorId} className="ui-field-error">{error}</span>}
    </div>
  )
}
