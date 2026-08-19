import { NavLink, Outlet } from 'react-router-dom'
import { LogOut } from 'lucide-react'
import { useAuth } from '@/auth/AuthProvider'
import { RouteErrorBoundary } from '@/components/RouteErrorBoundary'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

type NavItem = { to: string; label: string; end?: boolean }

const NAV: NavItem[] = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/transfers', label: 'Transfers' },
  { to: '/statements', label: 'Statements' },
]

const ADMIN_NAV: NavItem[] = [
  { to: '/admin/fraud', label: 'Fraud queue' },
  { to: '/admin/audit', label: 'Audit log' },
]

/** The shell every signed-in screen renders inside. */
export function AppLayout() {
  const { user, signOut } = useAuth()
  const links = user?.role === 'ADMIN' ? [...NAV, ...ADMIN_NAV] : NAV

  return (
    <div className="min-h-dvh bg-background">
      <header className="border-b">
        <div className="mx-auto flex h-14 max-w-6xl items-center gap-6 px-4">
          <span className="font-semibold tracking-tight">LedgerX</span>

          <nav className="flex items-center gap-1 text-sm">
            {links.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                end={link.end}
                className={({ isActive }) =>
                  cn(
                    'rounded-md px-3 py-1.5 transition-colors',
                    isActive
                      ? 'bg-muted font-medium text-foreground'
                      : 'text-muted-foreground hover:text-foreground',
                  )
                }
              >
                {link.label}
              </NavLink>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-3">
            <span className="hidden text-sm text-muted-foreground sm:inline">{user?.email}</span>
            <Button variant="ghost" size="sm" onClick={() => void signOut()}>
              <LogOut aria-hidden className="size-4" />
              Sign out
            </Button>
          </div>
        </div>
      </header>

      {/*
        Inside the shell on purpose. A screen that throws should cost the user
        that screen, not the navigation out of it — the boundary at the root
        would take the header down with it and leave nowhere to click.
      */}
      <main className="mx-auto max-w-6xl px-4 py-8">
        <RouteErrorBoundary>
          <Outlet />
        </RouteErrorBoundary>
      </main>
    </div>
  )
}
