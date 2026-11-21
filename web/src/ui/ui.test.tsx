import { createRef } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, it } from 'vitest'
import { Button, Field, Input, StatusPill } from './index'

it('preserves native link behavior and forwards the button ref', () => {
  const ref = createRef<HTMLAnchorElement>()
  render(<Button variant="primary" href="#catalog" ref={ref}>New quote</Button>)

  const link = screen.getByRole('link', { name: 'New quote' })
  expect(link).toHaveAttribute('href', '#catalog')
  expect(ref.current).toBe(link)
})

it('associates a field label and error with its input', async () => {
  render(
    <Field label="Quantity" hint="Enter units" error="Quantity is required">
      {(control) => <Input {...control} />}
    </Field>,
  )
  const input = screen.getByRole('textbox', { name: 'Quantity' })

  await userEvent.click(screen.getByText('Quantity'))
  expect(input).toHaveFocus()
  expect(input).toHaveAccessibleDescription('Enter units Quantity is required')
  expect(input).toHaveAttribute('aria-invalid', 'true')
})

it('renders status as readable text with a tone', () => {
  render(<StatusPill tone="success">Approved</StatusPill>)
  expect(screen.getByText('Approved')).toHaveClass('ui-status-pill--success')
})
