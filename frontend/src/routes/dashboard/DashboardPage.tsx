import { Suspense, lazy } from 'react'
import { Link } from 'react-router-dom'
import { Landmark, Receipt } from 'lucide-react'
import { ActivityList } from './ActivityList'
import { EmptyState } from '@/components/EmptyState'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { useAccounts, useBalance, useTransfers } from '@/lib/api/queries'
import { formatMoney } from '@/lib/money'

/**
 * Recharts is by far the heaviest thing this app imports, and exactly one card
 * on one screen uses it. Loading it lazily keeps it out of the entry chunk, so
 * signing in does not pay for a chart nobody has looked at yet.
 *
 * The named export is mapped to a default because `lazy` requires one; the
 * component itself stays a named export like every other in this codebase.
 */
const BalanceChart = lazy(() =>
  import('./BalanceChart').then((module) => ({ default: module.BalanceChart })),
)

export function DashboardPage() {
  const accounts = useAccounts()
  const account = accounts.data?.[0]
  const balance = useBalance(account?.id)
  const transfers = useTransfers(0, 8)

  if (accounts.isPending) {
    return <DashboardSkeleton />
  }

  if (!account) {
    return (
      <section className="space-y-6">
        <PageHeading />
        <EmptyState
          icon={<Landmark className="size-6" />}
          title="No account yet"
          description="Open an account to fund it and start moving money."
          action={
            <Link
              to="/transfers"
              className="inline-flex h-9 items-center justify-center rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
            >
              Open an account
            </Link>
          }
        />
      </section>
    )
  }

  const balanceMinorUnits = balance.data?.derivedBalanceMinorUnits ?? account.balanceMinorUnits

  return (
    <section className="space-y-6">
      <PageHeading />

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-1">
          <CardHeader>
            <CardTitle className="text-sm font-medium text-muted-foreground">
              Available balance
            </CardTitle>
          </CardHeader>
          <CardContent>
            {balance.isPending ? (
              <Skeleton data-testid="balance-skeleton" className="h-9 w-40" />
            ) : (
              <p className="text-3xl font-semibold tabular-nums tracking-tight">
                {formatMoney(balanceMinorUnits, account.currency)}
              </p>
            )}
            {balance.data && !balance.data.consistent ? (
              <p role="alert" className="mt-2 text-xs font-medium text-destructive">
                This balance disagrees with the ledger and is being investigated.
              </p>
            ) : null}
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="text-sm font-medium text-muted-foreground">
              Balance over time
            </CardTitle>
          </CardHeader>
          <CardContent>
            {transfers.isPending ? (
              <Skeleton data-testid="chart-skeleton" className="h-56 w-full" />
            ) : (
              /* Same skeleton for the chunk arriving as for the data arriving:
                 to the person waiting they are the same wait. */
              <Suspense fallback={<Skeleton data-testid="chart-skeleton" className="h-56 w-full" />}>
                <BalanceChart
                  transfers={transfers.data?.content ?? []}
                  currentBalanceMinorUnits={balanceMinorUnits}
                  currency={account.currency}
                />
              </Suspense>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader className="flex-row items-center justify-between">
          <CardTitle className="text-sm font-medium text-muted-foreground">
            Recent activity
          </CardTitle>
          <Link to="/transfers" className="text-sm underline underline-offset-4">
            View all
          </Link>
        </CardHeader>
        <CardContent>
          {transfers.isPending ? (
            <ActivitySkeleton />
          ) : transfers.data && transfers.data.content.length > 0 ? (
            <ActivityList transfers={transfers.data.content} />
          ) : (
            <EmptyState
              icon={<Receipt className="size-6" />}
              title="No activity yet"
              description="Money you send or receive will appear here."
            />
          )}
        </CardContent>
      </Card>
    </section>
  )
}

function PageHeading() {
  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
      <p className="text-sm text-muted-foreground">Your balance and recent movement.</p>
    </div>
  )
}

function ActivitySkeleton() {
  return (
    <ul data-testid="activity-skeleton" className="divide-y">
      {[0, 1, 2, 3].map((row) => (
        <li key={row} className="flex items-center gap-3 py-3">
          <Skeleton className="size-8 shrink-0 rounded-full" />
          <div className="flex-1 space-y-1.5">
            <Skeleton className="h-3.5 w-40" />
            <Skeleton className="h-3 w-24" />
          </div>
          <Skeleton className="h-4 w-16" />
        </li>
      ))}
    </ul>
  )
}

function DashboardSkeleton() {
  return (
    <section data-testid="dashboard-skeleton" className="space-y-6">
      <PageHeading />
      <div className="grid gap-4 lg:grid-cols-3">
        <Skeleton className="h-32 lg:col-span-1" />
        <Skeleton className="h-32 lg:col-span-2" />
      </div>
      <Skeleton className="h-64" />
    </section>
  )
}
