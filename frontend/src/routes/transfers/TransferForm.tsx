import { useRef, useState } from 'react'
import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { Field } from '@/components/Field'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ApiError } from '@/lib/api/client'
import { useCreateTransfer } from '@/lib/api/useCreateTransfer'
import { applyServerErrors } from '@/lib/forms/serverErrors'
import { parseAmountToMinorUnits } from '@/lib/parseAmount'
import { FormAlert } from '@/routes/auth/FormAlert'
import { transferSchema, type TransferValues } from './transferSchema'

const FIELDS = ['destinationAccountId', 'amount'] as const

export function TransferForm({
  sourceAccountId,
  currency,
}: {
  sourceAccountId: string
  currency: string
}) {
  const createTransfer = useCreateTransfer(currency)

  /**
   * The key belongs to the intent, not the attempt.
   *
   * It is minted when a submission first leaves the form and held until that
   * submission succeeds or its inputs change. Pressing "try again" therefore
   * replays the identical request under the identical key, which the server
   * answers with the original outcome instead of moving money a second time.
   */
  const idempotencyKey = useRef<string | null>(null)
  const keyedValues = useRef<string | null>(null)
  const [canRetry, setCanRetry] = useState(false)

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<TransferValues>({
    resolver: zodResolver(transferSchema),
    defaultValues: { destinationAccountId: '', amount: '' },
  })

  function keyFor(values: TransferValues) {
    const fingerprint = `${values.destinationAccountId}|${values.amount}`
    if (idempotencyKey.current === null || keyedValues.current !== fingerprint) {
      // Different inputs are a different request, and reusing the key would be
      // refused by the server as a conflict rather than treated as a retry.
      idempotencyKey.current = crypto.randomUUID()
      keyedValues.current = fingerprint
    }
    return idempotencyKey.current
  }

  const onSubmit = handleSubmit(async (values) => {
    const amountMinorUnits = parseAmountToMinorUnits(values.amount)
    if (amountMinorUnits === null) return

    try {
      await createTransfer.mutateAsync({
        sourceAccountId,
        destinationAccountId: values.destinationAccountId,
        amountMinorUnits,
        idempotencyKey: keyFor(values),
      })

      // Spent: the next transfer is a new intent and needs its own key.
      idempotencyKey.current = null
      keyedValues.current = null
      setCanRetry(false)
      reset()
      toast.success('Transfer sent')
    } catch (error) {
      setCanRetry(true)
      applyServerErrors(error, setError, FIELDS)
      toast.error(error instanceof ApiError ? error.message : 'Could not send the transfer')
    }
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm font-medium text-muted-foreground">Send money</CardTitle>
      </CardHeader>
      <CardContent>
        <form onSubmit={onSubmit} noValidate className="space-y-4">
          <FormAlert message={errors.root?.message} />

          <Field
            label="Recipient account id"
            placeholder="00000000-0000-0000-0000-000000000000"
            autoComplete="off"
            error={errors.destinationAccountId?.message}
            {...register('destinationAccountId')}
          />
          <Field
            label={`Amount (${currency})`}
            inputMode="decimal"
            placeholder="125.50"
            error={errors.amount?.message}
            {...register('amount')}
          />

          <div className="flex items-center gap-2">
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Sending…' : canRetry ? 'Try again' : 'Send transfer'}
            </Button>
            {canRetry ? (
              <p className="text-xs text-muted-foreground">
                Retrying reuses the same idempotency key, so it cannot send twice.
              </p>
            ) : null}
          </div>
        </form>
      </CardContent>
    </Card>
  )
}
