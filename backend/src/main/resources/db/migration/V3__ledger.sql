CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE RESTRICT,
    account_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    cached_balance BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT accounts_user_required_for_user_accounts
        CHECK (account_type <> 'USER' OR user_id IS NOT NULL),
    CONSTRAINT accounts_system_has_no_owner
        CHECK (account_type <> 'TREASURY' OR user_id IS NULL)
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);

-- One account per owner per currency, and exactly one treasury account ever.
CREATE UNIQUE INDEX idx_accounts_owner_currency ON accounts(user_id, currency)
    WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX idx_accounts_single_treasury ON accounts((account_type))
    WHERE account_type = 'TREASURY';

-- transfer_id has no foreign key yet: the transfers table arrives in V4, which
-- adds the constraint. Entries are written with a transfer id from the start so
-- the column never has to be backfilled.
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id UUID NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    direction VARCHAR(6) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_entries_account_id ON ledger_entries(account_id);
CREATE INDEX idx_ledger_entries_transfer_id ON ledger_entries(transfer_id);

-- The append-only rule is enforced by the database rather than by convention.
-- Application code cannot rewrite history even by accident, and a compromised
-- service account cannot quietly restate a balance. TRUNCATE is intentionally
-- still permitted: it fires no row-level trigger and is how tests reset state.
CREATE OR REPLACE FUNCTION reject_ledger_entry_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_entries_immutable
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION reject_ledger_entry_mutation();

-- The treasury is the counterparty for money entering or leaving the platform,
-- so a deposit is a real two-sided movement rather than a single-sided write.
-- Its balance runs negative by design: that is the value the platform owes its
-- users, and the whole system nets to zero.
INSERT INTO accounts (id, user_id, account_type, currency, cached_balance, version, status)
VALUES ('00000000-0000-0000-0000-000000000001', NULL, 'TREASURY', 'USD', 0, 0, 'ACTIVE');
