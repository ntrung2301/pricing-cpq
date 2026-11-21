import { Button as AntButton, type ButtonProps as AntButtonProps } from 'antd'
import type { ComponentPropsWithRef } from 'react'

const buttonTypes = {
  primary: 'primary',
  outline: 'default',
  accent: 'default',
  quiet: 'text',
  link: 'link',
} satisfies Record<string, AntButtonProps['type']>

export type ButtonProps = Omit<ComponentPropsWithRef<typeof AntButton>, 'type' | 'color' | 'variant' | 'shape' | 'ghost'> & {
  variant?: keyof typeof buttonTypes
}

export function Button({ variant = 'outline', className, ...props }: ButtonProps) {
  const classes = ['ui-button', `ui-button--${variant}`, className].filter(Boolean).join(' ')

  return <AntButton {...props} type={buttonTypes[variant]} className={classes} />
}
