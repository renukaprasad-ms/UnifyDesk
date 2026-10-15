-- V127: Unified Prepaid Communications Wallet, Audit Ledger, and Company Tax/Billing Profile

CREATE TABLE IF NOT EXISTS company_wallets (
    company_id BIGINT PRIMARY KEY REFERENCES companies(id) ON DELETE CASCADE,
    balance NUMERIC(19, 4) NOT NULL DEFAULT 0.0000,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    auto_recharge_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    auto_recharge_threshold NUMERIC(19, 4) DEFAULT 500.0000,
    auto_recharge_amount NUMERIC(19, 4) DEFAULT 2000.0000,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    type VARCHAR(32) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    balance_after NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    reference_id VARCHAR(120),
    description VARCHAR(255) NOT NULL,
    actor_user_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wallet_tx_company_time ON wallet_transactions(company_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_wallet_tx_ref ON wallet_transactions(reference_id);

CREATE TABLE IF NOT EXISTS company_billing_profiles (
    company_id BIGINT PRIMARY KEY REFERENCES companies(id) ON DELETE CASCADE,
    legal_business_name VARCHAR(180),
    tax_id_or_gstin VARCHAR(50),
    billing_email VARCHAR(254),
    billing_phone VARCHAR(32),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(80) NOT NULL DEFAULT 'India',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Ensure default wallet row exists for all active companies
INSERT INTO company_wallets (company_id, balance, currency, auto_recharge_enabled, version, created_at, updated_at)
SELECT id, 0.0000, 'INR', false, 0, NOW(), NOW()
FROM companies
ON CONFLICT (company_id) DO NOTHING;
