-- V128: Upgrade plan pricing to sustainable market rates, add Enterprise plan, and update entitlements to match prepaid wallet architecture

-- 1. Update Existing Plan Details & Base Prices
UPDATE plans
SET description = 'Essential tools for solopreneurs & small teams starting out with CRM and communication',
    updated_at = NOW()
WHERE code = 'FREE' AND version = 1;

UPDATE plans
SET base_price = 1499.000000,
    description = 'Essential CRM, multi-channel messaging & automations for growing local businesses',
    updated_at = NOW()
WHERE code = 'STARTER' AND version = 1;

UPDATE plans
SET base_price = 3499.000000,
    description = 'High-volume communications, team collaboration, and automation for scaling businesses',
    updated_at = NOW()
WHERE code = 'GROWTH' AND version = 1;

-- 2. Insert ENTERPRISE Plan if not exists
INSERT INTO plans (
    id, code, name, description, status, visibility, billing_cycle, base_price, currency, version, created_at, updated_at
)
VALUES (
    369031861200000000,
    'ENTERPRISE',
    'Enterprise',
    'Dedicated infrastructure, maximum limits, API integrations, and 24/7 priority support',
    'PUBLISHED',
    'PUBLIC',
    'MONTHLY',
    8999.000000,
    'INR',
    1,
    NOW(),
    NOW()
)
ON CONFLICT (code, version) DO UPDATE
SET base_price = 8999.000000,
    description = 'Dedicated infrastructure, maximum limits, API integrations, and 24/7 priority support',
    updated_at = NOW();

-- 3. Update / Insert Plan Prices for Monthly and Yearly billing
-- Update STARTER monthly price
UPDATE plan_prices
SET amount = 1499.000000, updated_at = NOW()
WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1)
  AND billing_cycle = 'MONTHLY';

-- Insert STARTER yearly price if not exists
INSERT INTO plan_prices (id, plan_id, billing_cycle, amount, currency, effective_from, status, created_at, updated_at)
SELECT 369031861088817200, id, 'YEARLY', 14990.000000, 'INR', NOW(), 'ACTIVE', NOW(), NOW()
FROM plans WHERE code = 'STARTER' AND version = 1
ON CONFLICT (id) DO NOTHING;

-- Update GROWTH monthly price
UPDATE plan_prices
SET amount = 3499.000000, updated_at = NOW()
WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1)
  AND billing_cycle = 'MONTHLY';

-- Insert GROWTH yearly price if not exists
INSERT INTO plan_prices (id, plan_id, billing_cycle, amount, currency, effective_from, status, created_at, updated_at)
SELECT 369031861130760200, id, 'YEARLY', 34990.000000, 'INR', NOW(), 'ACTIVE', NOW(), NOW()
FROM plans WHERE code = 'GROWTH' AND version = 1
ON CONFLICT (id) DO NOTHING;

-- Insert ENTERPRISE monthly price
INSERT INTO plan_prices (id, plan_id, billing_cycle, amount, currency, effective_from, status, created_at, updated_at)
SELECT 369031861200000100, id, 'MONTHLY', 8999.000000, 'INR', NOW(), 'ACTIVE', NOW(), NOW()
FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (id) DO NOTHING;

-- Insert ENTERPRISE yearly price
INSERT INTO plan_prices (id, plan_id, billing_cycle, amount, currency, effective_from, status, created_at, updated_at)
SELECT 369031861200000200, id, 'YEARLY', 89990.000000, 'INR', NOW(), 'ACTIVE', NOW(), NOW()
FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (id) DO NOTHING;

-- 4. Upsert Updated Entitlements for FREE Plan
INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319001, id, 'MAX_WORKSPACE_USERS', '1', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '1', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319002, id, 'CONTACT_LIMIT', '500', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '500', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319003, id, 'SEGMENT_LIMIT', '5', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '5', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319004, id, 'BROADCAST_GROUP_LIMIT', '5', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '5', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319005, id, 'EMAIL_ACCOUNTS', '1', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '1', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319006, id, 'WHATSAPP_ACCOUNTS', '1', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '1', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319007, id, 'WHATSAPP_NUMBERS', '1', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '1', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319008, id, 'SMS_ACCOUNTS', '1', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '1', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319009, id, 'SMS_SENDER_IDS', '1', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '1', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319010, id, 'BROADCAST_BATCH_LIMIT', '100', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '100', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319011, id, 'BROADCASTS_PER_DAY', '5', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '5', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319012, id, 'BROADCASTS_PER_MONTH', '50', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '50', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319013, id, 'BROADCAST_RECIPIENTS_PER_DAY', '250', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '250', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319014, id, 'BROADCAST_RECIPIENTS_PER_MONTH', '2500', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '2500', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319015, id, 'SCHEDULED_BROADCASTS_ENABLED', 'false', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = 'false', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319016, id, 'EMAIL_TEMPLATE_LIMIT', '10', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '10', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319017, id, 'WHATSAPP_TEMPLATE_LIMIT', '10', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '10', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319018, id, 'SMS_TEMPLATE_LIMIT', '10', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '10', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319019, id, 'STORAGE_GB', '1', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '1', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319020, id, 'AUTO_REPLY_ENABLED', 'false', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = 'false', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319021, id, 'USER_SIGNATURE_ENABLED', 'true', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = 'true', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319022, id, 'API_ACCESS_ENABLED', 'false', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = 'false', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319023, id, 'MANAGED_EMAIL_ENABLED', 'true', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = 'true', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319024, id, 'MANAGED_EMAIL_DOMAINS', '1', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '1', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319025, id, 'MANAGED_EMAIL_SENDERS', '1', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '1', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319026, id, 'MANAGED_EMAIL_MESSAGES_MONTHLY', '250', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '250', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319027, id, 'MANAGED_SMS_SEGMENTS_MONTHLY', '0', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '0', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319028, id, 'CONTACT_CUSTOM_FIELD_LIMIT', '10', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '10', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319029, id, 'CONTACT_LIST_LIMIT', '5', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '5', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861013319030, id, 'CONTACT_IMPORT_ROWS_LIMIT', '500', NOW(), NOW() FROM plans WHERE code = 'FREE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '500', updated_at = NOW();


-- 5. Upsert Updated Entitlements for STARTER Plan
UPDATE plan_entitlements SET entitlement_value = '3', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'MAX_WORKSPACE_USERS';
UPDATE plan_entitlements SET entitlement_value = '5000', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'CONTACT_LIMIT';
UPDATE plan_entitlements SET entitlement_value = '25', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'SEGMENT_LIMIT';
UPDATE plan_entitlements SET entitlement_value = '25', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'BROADCAST_GROUP_LIMIT';
UPDATE plan_entitlements SET entitlement_value = '2', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'EMAIL_ACCOUNTS';
UPDATE plan_entitlements SET entitlement_value = '2', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'WHATSAPP_ACCOUNTS';
UPDATE plan_entitlements SET entitlement_value = '2', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'WHATSAPP_NUMBERS';
UPDATE plan_entitlements SET entitlement_value = '2', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'SMS_ACCOUNTS';
UPDATE plan_entitlements SET entitlement_value = '2', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'SMS_SENDER_IDS';
UPDATE plan_entitlements SET entitlement_value = '1000', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'BROADCAST_BATCH_LIMIT';
UPDATE plan_entitlements SET entitlement_value = '25', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'BROADCASTS_PER_DAY';
UPDATE plan_entitlements SET entitlement_value = '500', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'BROADCASTS_PER_MONTH';
UPDATE plan_entitlements SET entitlement_value = '5000', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'BROADCAST_RECIPIENTS_PER_DAY';
UPDATE plan_entitlements SET entitlement_value = '50000', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'BROADCAST_RECIPIENTS_PER_MONTH';
UPDATE plan_entitlements SET entitlement_value = 'true', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'SCHEDULED_BROADCASTS_ENABLED';
UPDATE plan_entitlements SET entitlement_value = '50', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'EMAIL_TEMPLATE_LIMIT';
UPDATE plan_entitlements SET entitlement_value = '50', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'WHATSAPP_TEMPLATE_LIMIT';
UPDATE plan_entitlements SET entitlement_value = '50', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'SMS_TEMPLATE_LIMIT';
UPDATE plan_entitlements SET entitlement_value = '5', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'STORAGE_GB';
UPDATE plan_entitlements SET entitlement_value = 'true', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'AUTO_REPLY_ENABLED';
UPDATE plan_entitlements SET entitlement_value = 'true', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'USER_SIGNATURE_ENABLED';
UPDATE plan_entitlements SET entitlement_value = 'false', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'API_ACCESS_ENABLED';
UPDATE plan_entitlements SET entitlement_value = 'true', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'MANAGED_EMAIL_ENABLED';
UPDATE plan_entitlements SET entitlement_value = '3', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'MANAGED_EMAIL_DOMAINS';
UPDATE plan_entitlements SET entitlement_value = '5', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'MANAGED_EMAIL_SENDERS';
UPDATE plan_entitlements SET entitlement_value = '3000', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'MANAGED_EMAIL_MESSAGES_MONTHLY';
UPDATE plan_entitlements SET entitlement_value = '150', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'MANAGED_SMS_SEGMENTS_MONTHLY';
UPDATE plan_entitlements SET entitlement_value = '50', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'CONTACT_CUSTOM_FIELD_LIMIT';
UPDATE plan_entitlements SET entitlement_value = '50', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'CONTACT_LIST_LIMIT';
UPDATE plan_entitlements SET entitlement_value = '10000', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'STARTER' AND version = 1) AND entitlement_key = 'CONTACT_IMPORT_ROWS_LIMIT';


-- 6. Upsert Updated Entitlements for GROWTH Plan
UPDATE plan_entitlements SET entitlement_value = '8', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'MAX_WORKSPACE_USERS';
UPDATE plan_entitlements SET entitlement_value = '25000', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'CONTACT_LIMIT';
UPDATE plan_entitlements SET entitlement_value = '100', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'SEGMENT_LIMIT';
UPDATE plan_entitlements SET entitlement_value = '100', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'BROADCAST_GROUP_LIMIT';
UPDATE plan_entitlements SET entitlement_value = '5', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'EMAIL_ACCOUNTS';
UPDATE plan_entitlements SET entitlement_value = '5', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'WHATSAPP_ACCOUNTS';
UPDATE plan_entitlements SET entitlement_value = '5', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'WHATSAPP_NUMBERS';
UPDATE plan_entitlements SET entitlement_value = '5', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'SMS_ACCOUNTS';
UPDATE plan_entitlements SET entitlement_value = '5', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'SMS_SENDER_IDS';
UPDATE plan_entitlements SET entitlement_value = '5000', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'BROADCAST_BATCH_LIMIT';
UPDATE plan_entitlements SET entitlement_value = '100', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'BROADCASTS_PER_DAY';
UPDATE plan_entitlements SET entitlement_value = '2000', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'BROADCASTS_PER_MONTH';
UPDATE plan_entitlements SET entitlement_value = '25000', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'BROADCAST_RECIPIENTS_PER_DAY';
UPDATE plan_entitlements SET entitlement_value = '250000', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'BROADCAST_RECIPIENTS_PER_MONTH';
UPDATE plan_entitlements SET entitlement_value = 'true', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'SCHEDULED_BROADCASTS_ENABLED';
UPDATE plan_entitlements SET entitlement_value = '200', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'EMAIL_TEMPLATE_LIMIT';
UPDATE plan_entitlements SET entitlement_value = '200', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'WHATSAPP_TEMPLATE_LIMIT';
UPDATE plan_entitlements SET entitlement_value = '200', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'SMS_TEMPLATE_LIMIT';
UPDATE plan_entitlements SET entitlement_value = '25', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'STORAGE_GB';
UPDATE plan_entitlements SET entitlement_value = 'true', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'AUTO_REPLY_ENABLED';
UPDATE plan_entitlements SET entitlement_value = 'true', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'USER_SIGNATURE_ENABLED';
UPDATE plan_entitlements SET entitlement_value = 'true', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'API_ACCESS_ENABLED';
UPDATE plan_entitlements SET entitlement_value = 'true', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'MANAGED_EMAIL_ENABLED';
UPDATE plan_entitlements SET entitlement_value = '10', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'MANAGED_EMAIL_DOMAINS';
UPDATE plan_entitlements SET entitlement_value = '15', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'MANAGED_EMAIL_SENDERS';
UPDATE plan_entitlements SET entitlement_value = '15000', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'MANAGED_EMAIL_MESSAGES_MONTHLY';
UPDATE plan_entitlements SET entitlement_value = '750', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'MANAGED_SMS_SEGMENTS_MONTHLY';
UPDATE plan_entitlements SET entitlement_value = '100', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'CONTACT_CUSTOM_FIELD_LIMIT';
UPDATE plan_entitlements SET entitlement_value = '200', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'CONTACT_LIST_LIMIT';
UPDATE plan_entitlements SET entitlement_value = '50000', updated_at = NOW() WHERE plan_id = (SELECT id FROM plans WHERE code = 'GROWTH' AND version = 1) AND entitlement_key = 'CONTACT_IMPORT_ROWS_LIMIT';


-- 7. Insert All Entitlements for ENTERPRISE Plan
INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000001, id, 'MAX_WORKSPACE_USERS', '25', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '25', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000002, id, 'CONTACT_LIMIT', '100000', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '100000', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000003, id, 'SEGMENT_LIMIT', '500', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '500', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000004, id, 'BROADCAST_GROUP_LIMIT', '500', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '500', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000005, id, 'EMAIL_ACCOUNTS', '10', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '10', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000006, id, 'WHATSAPP_ACCOUNTS', '10', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '10', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000007, id, 'WHATSAPP_NUMBERS', '10', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '10', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000008, id, 'SMS_ACCOUNTS', '10', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '10', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000009, id, 'SMS_SENDER_IDS', '10', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '10', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000010, id, 'BROADCAST_BATCH_LIMIT', '20000', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '20000', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000011, id, 'BROADCASTS_PER_DAY', '500', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '500', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000012, id, 'BROADCASTS_PER_MONTH', '10000', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '10000', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000013, id, 'BROADCAST_RECIPIENTS_PER_DAY', '100000', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '100000', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000014, id, 'BROADCAST_RECIPIENTS_PER_MONTH', '1000000', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '1000000', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000015, id, 'SCHEDULED_BROADCASTS_ENABLED', 'true', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = 'true', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000016, id, 'EMAIL_TEMPLATE_LIMIT', '1000', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '1000', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000017, id, 'WHATSAPP_TEMPLATE_LIMIT', '1000', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '1000', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000018, id, 'SMS_TEMPLATE_LIMIT', '1000', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '1000', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000019, id, 'STORAGE_GB', '100', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '100', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000020, id, 'AUTO_REPLY_ENABLED', 'true', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = 'true', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000021, id, 'USER_SIGNATURE_ENABLED', 'true', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = 'true', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000022, id, 'API_ACCESS_ENABLED', 'true', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = 'true', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000023, id, 'MANAGED_EMAIL_ENABLED', 'true', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = 'true', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000024, id, 'MANAGED_EMAIL_DOMAINS', '25', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '25', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000025, id, 'MANAGED_EMAIL_SENDERS', '50', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '50', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000026, id, 'MANAGED_EMAIL_MESSAGES_MONTHLY', '50000', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '50000', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000027, id, 'MANAGED_SMS_SEGMENTS_MONTHLY', '3000', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '3000', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000028, id, 'CONTACT_CUSTOM_FIELD_LIMIT', '500', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '500', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000029, id, 'CONTACT_LIST_LIMIT', '1000', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '1000', updated_at = NOW();

INSERT INTO plan_entitlements (id, plan_id, entitlement_key, entitlement_value, created_at, updated_at)
SELECT 369031861200000030, id, 'CONTACT_IMPORT_ROWS_LIMIT', '250000', NOW(), NOW() FROM plans WHERE code = 'ENTERPRISE' AND version = 1
ON CONFLICT (plan_id, entitlement_key) DO UPDATE SET entitlement_value = '250000', updated_at = NOW();
