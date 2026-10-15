-- Ensure all existing companies have an active Communications Wallet initialized
insert into company_wallets (
    company_id,
    balance,
    currency,
    auto_recharge_enabled,
    auto_recharge_threshold,
    auto_recharge_amount,
    version,
    created_at,
    updated_at
)
select
    c.id,
    0.0000,
    'INR',
    false,
    500.0000,
    2000.0000,
    0,
    now(),
    now()
from companies c
left join company_wallets w on w.company_id = c.id
where w.company_id is null;
