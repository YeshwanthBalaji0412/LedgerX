import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'

/**
 * The frame both auth screens share. Deliberately quiet: a sign-in page that
 * shouts is a sign-in page you distrust.
 */
export function AuthShell({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string
  subtitle: string
  children: ReactNode
  footer: ReactNode
}) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center bg-background px-4 py-12">
      <div className="w-full max-w-sm space-y-8">
        <div className="space-y-2">
          <Link to="/" className="text-sm font-semibold tracking-tight">
            LedgerX
          </Link>
          <div className="space-y-1">
            <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
            <p className="text-sm text-muted-foreground">{subtitle}</p>
          </div>
        </div>

        {children}

        <p className="text-sm text-muted-foreground">{footer}</p>
      </div>
    </div>
  )
}
