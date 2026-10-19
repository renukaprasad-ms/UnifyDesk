create unique index uk_ad_accounts_primary_provider
  on ad_accounts (company_id, provider)
  where primary_account = true;
