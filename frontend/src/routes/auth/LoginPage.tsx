import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '@/auth/AuthProvider'
import { Field } from '@/components/Field'
import { FullPageLoader } from '@/components/FullPageLoader'
import { Button } from '@/components/ui/button'
import { applyServerErrors } from '@/lib/forms/serverErrors'
import { AuthShell } from './AuthShell'
import { FormAlert } from './FormAlert'
import { loginSchema, type LoginValues } from './authSchemas'

const FIELDS = ['email', 'password'] as const

export function LoginPage() {
  const { status, signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })

  // The same three-state handling the guards use: while the session is
  // resolving we do not yet know whether to show a form or send them onward.
  if (status === 'resolving') {
    return <FullPageLoader label="Restoring your session" />
  }
  if (status === 'authenticated') {
    const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname
    return <Navigate to={from ?? '/'} replace />
  }

  const onSubmit = handleSubmit(async (values) => {
    try {
      await signIn(values.email, values.password)
      const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname
      void navigate(from ?? '/', { replace: true })
    } catch (error) {
      applyServerErrors(error, setError, FIELDS)
    }
  })

  return (
    <AuthShell
      title="Sign in"
      subtitle="Access your ledger, transfers and statements."
      footer={
        <>
          No account yet?{' '}
          <Link to="/register" className="font-medium text-foreground underline underline-offset-4">
            Create one
          </Link>
        </>
      }
    >
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        <FormAlert message={errors.root?.message} />

        <Field
          label="Email"
          type="email"
          autoComplete="email"
          autoFocus
          error={errors.email?.message}
          {...register('email')}
        />
        <Field
          label="Password"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password')}
        />

        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? 'Signing in…' : 'Sign in'}
        </Button>
      </form>
    </AuthShell>
  )
}
