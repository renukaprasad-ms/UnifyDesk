-- ============================================================================
-- UnifyDesk Enterprise Simulation Script
-- Simulates 100 Companies with:
--   - 5 Users per company (Owner, Manager, 3 Agents) with Password@123
--   - Active Enterprise Subscription & ₹25,000 Wallet Balance
--   - 3 Email Connections & 3 WhatsApp Meta Connections
--   - 1,000 Messages per Channel (6,000 messages per company, 600,000 total)
--   - 10,000 Contacts per Company (1,000,000 contacts total) + Identities
--   - 50 CRM Tasks per Company (5,000 total)
--   - 30 CRM Deals & 20 CRM Accounts per Company
--   - 120 Conversations per Company linked to Contacts
-- ============================================================================

-- Procedure: cleanup_simulated_test_data
CREATE OR REPLACE PROCEDURE cleanup_simulated_test_data()
LANGUAGE plpgsql
AS $$
DECLARE
  v_comp_count int;
  v_user_count int;
  v_contact_count int;
  v_msg_count int;
BEGIN
  RAISE NOTICE 'Starting cleanup of all simulated test data (slugs: sim-%%, users: %%@sim.unifydesk.in)...';

  -- 1. Count what will be cleaned
  SELECT count(*) INTO v_comp_count FROM companies WHERE slug LIKE 'sim-%';
  SELECT count(*) INTO v_user_count FROM users WHERE email LIKE '%@sim.unifydesk.in' OR username LIKE 'sim_%';
  SELECT count(*) INTO v_contact_count FROM contacts WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');
  SELECT count(*) INTO v_msg_count FROM inbox_messages WHERE connection_id IN (
    SELECT id FROM channel_connections WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%')
  );

  RAISE NOTICE 'Targeting for cleanup: % companies, % users, % contacts, % messages',
    v_comp_count, v_user_count, v_contact_count, v_msg_count;

  -- 2. CRM Tasks, Subtasks & Events
  DELETE FROM crm_task_events WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');
  DELETE FROM crm_subtasks WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');
  DELETE FROM crm_task_attachments WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');
  DELETE FROM crm_tasks WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');
  DELETE FROM crm_task_columns WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');

  -- 3. CRM Deals & Events
  DELETE FROM crm_deal_events WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');
  DELETE FROM crm_deals WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');
  DELETE FROM crm_account_contacts WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');
  DELETE FROM crm_accounts WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');
  DELETE FROM crm_pipeline_stages WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');
  DELETE FROM crm_pipelines WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');

  -- 4. Inbox Messages, Conversations, Links & Participants
  DELETE FROM inbox_participant_contact_links WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');
  DELETE FROM inbox_messages WHERE connection_id IN (
    SELECT id FROM channel_connections WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%')
  );
  DELETE FROM inbox_participants WHERE conversation_id IN (
    SELECT id FROM inbox_conversations WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%')
  );
  DELETE FROM inbox_conversations WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');

  -- 5. Channels, WhatsApp & Sender Identities
  DELETE FROM meta_whatsapp_connections WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');
  DELETE FROM sender_identities WHERE connection_id IN (
    SELECT id FROM channel_connections WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%')
  );
  DELETE FROM channel_connections WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');

  -- 6. Contacts & Identities
  DELETE FROM contact_identities WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');
  DELETE FROM contacts WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');

  -- 7. Subscriptions, Wallets, Members & Roles
  DELETE FROM company_subscriptions WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');
  DELETE FROM company_wallets WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');
  DELETE FROM company_custom_roles WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');
  DELETE FROM company_members WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');

  -- 8. Companies
  DELETE FROM companies WHERE slug LIKE 'sim-%';

  -- 9. Users
  DELETE FROM users WHERE email LIKE '%@sim.unifydesk.in' OR username LIKE 'sim_%';

  RAISE NOTICE 'Cleanup completed successfully! All simulated companies and artifacts removed.';
END;
$$;


-- Procedure: simulate_test_dataset
CREATE OR REPLACE PROCEDURE simulate_test_dataset(
  p_companies_count INT DEFAULT 100,
  p_contacts_per_company INT DEFAULT 10000,
  p_messages_per_channel INT DEFAULT 1000,
  p_tasks_per_company INT DEFAULT 50,
  p_deals_per_company INT DEFAULT 30
)
LANGUAGE plpgsql
AS $$
DECLARE
  v_sim_start_time timestamptz := clock_timestamp();
  v_co_start_time timestamptz;
  v_base_time bigint;
  v_company_id bigint;
  v_owner_id bigint;
  v_mgr_id bigint;
  v_agt1_id bigint;
  v_agt2_id bigint;
  v_agt3_id bigint;
  v_sub_id bigint;
  v_pipe_id bigint;
  v_plan_id bigint := 369031861200000000; -- Enterprise
  v_price_id bigint := 369031861200000100;
  v_superadmin_id bigint := 369407586547535872; -- Super Admin
  v_pwd_hash varchar(255) := '$2a$10$O9Cqacbtfc3gxvqWaqM4Pep9WC/LYX1qqUdOQ8Wg70WOIjfjN25Ky'; -- Password@123
  v_company_name text;
  v_slug text;
  v_clean_name text;
  v_domain text;
  v_convs_per_channel int := 20;
  v_msgs_per_conv int;
  i int;

  -- 100 Realistic Company Names across Tech, Logistics, Health, Retail, FinTech, Infra, Edu, etc.
  v_names text[] := ARRAY[
    'Apex Cloud Innovations', 'Nexus Data Labs', 'Hyperion AI Systems', 'QuantumByte Technologies', 'CloudSphere Software',
    'Synthetix AI Labs', 'PulseMetrics Analytics', 'Optima Cyber Security', 'InfiniNet Networks', 'BlueShift Digital',
    'Vanguard Global Freight', 'TransRoute Logistics', 'Falcon Express Couriers', 'CargoWave Shipping', 'PrimeHaul Carriers',
    'AeroLogix Global', 'SwiftFleet Express', 'Horizon Marine Logistics', 'Velocity Supply Chain', 'Pinnacle Dispatch Solutions',
    'Zenith Health Sciences', 'MediCore Diagnostics', 'BioVantage Therapeutics', 'CarePulse Telehealth', 'Novus Lifesciences',
    'OmniCare Medical Systems', 'Aura Vitality Clinics', 'Synapse NeuroTech', 'Helix Genomics Labs', 'Apex Pharma Solutions',
    'Velvet and Vine Apparels', 'UrbanCraft Lifestyle', 'NovaMart Retailers', 'TrendHive Commerce', 'PureBoutique Brands',
    'MetroCart Online', 'LuxeLiving Home Goods', 'Evergreen Organics', 'StyleSphere Fashion', 'TerraGlow Naturals',
    'Equinox Wealth Advisory', 'PaySwift Payments', 'CapitalPeak Financial', 'Credentis Lending', 'SilverStone Asset Management',
    'AuraPay Global', 'FinEdge Securities', 'Nexus Financial Group', 'TrueVest Capital', 'Fortress Credit Corp',
    'Titan Forged Industries', 'AeroPrecision Metals', 'RoboBuild Automation', 'Apex Machine Works', 'GlobalPolymer Solutions',
    'Starlight Energy Systems', 'TerraFoundry Works', 'EcoPower Renewable Tech', 'Precision Dynamics Corp', 'Vulcan Thermal Systems',
    'Skyline Infra Developers', 'Cornerstone Realty Group', 'UrbanVista Properties', 'Prestige Haven Living', 'TerraPrime Constructions',
    'GrandView Estates', 'Blueprints Engineering', 'Summit Heights Realty', 'Anchorstone Infra', 'HavenCrest Housing',
    'EduCore Learning Hub', 'TalentBridge Academy', 'CogniLearn EdTech', 'BrightPath Tutors', 'SkillMatrix Pro',
    'Vanguard University Prep', 'OmniClass Virtual', 'Apex Knowledge Base', 'EduSphere Global', 'MasteryEdge Learning',
    'Serenade Luxury Resorts', 'Voyageur Travel Group', 'Azure Coast Getaways', 'GrandHeritage Hotels', 'Compass Expeditions',
    'Solaria Retreats', 'Highland Eco Lodge', 'Wanderlust Destinations', 'RoyalVista Palace', 'Oasis Hospitality',
    'Starlight Digital Media', 'BrandCraft Marketing', 'Visionary PR Group', 'Catalyst Media Works', 'AdVantage Global',
    'BluePrint Strategy Advisors', 'PeakPulse Creative', 'OmniReach Marketing', 'AlphaMind Consulting', 'EchoPoint Productions'
  ];

  v_first_names text[] := ARRAY['Aarav', 'Vivaan', 'Aditya', 'Vihaan', 'Arjun', 'Sai', 'Reyansh', 'Ayaan', 'Krishna', 'Ishaan', 'Shaurya', 'Atharv', 'Ananya', 'Diya', 'Isha', 'Aadhya', 'Kavya', 'Rhea', 'Pooja', 'Sneha', 'Tanvi', 'Meera', 'Rohan', 'Vikram', 'Neha', 'Priya', 'Amit', 'Rahul', 'Deepak', 'Sanjay'];
  v_last_names text[] := ARRAY['Sharma', 'Verma', 'Patel', 'Mehta', 'Reddy', 'Nair', 'Iyer', 'Gupta', 'Singh', 'Chopra', 'Malhotra', 'Bhat', 'Deshmukh', 'Joshi', 'Kulkarni', 'Rao', 'Shetty', 'Pillai', 'Menon', 'Saxena'];
BEGIN
  v_msgs_per_conv := GREATEST(1, p_messages_per_channel / v_convs_per_channel);

  RAISE NOTICE '================================================================================';
  RAISE NOTICE 'Starting UnifyDesk Simulation:';
  RAISE NOTICE '  - Companies: %', p_companies_count;
  RAISE NOTICE '  - Users per Company: 5 (Total %)', p_companies_count * 5;
  RAISE NOTICE '  - Channels per Company: 3 Email + 3 WhatsApp = 6 (Total %)', p_companies_count * 6;
  RAISE NOTICE '  - Messages per Channel: % (% msgs/company, Total %)', p_messages_per_channel, p_messages_per_channel * 6, p_companies_count * p_messages_per_channel * 6;
  RAISE NOTICE '  - Contacts per Company: % (Total %)', p_contacts_per_company, p_companies_count * p_contacts_per_company;
  RAISE NOTICE '  - CRM Tasks per Company: % (Total %)', p_tasks_per_company, p_companies_count * p_tasks_per_company;
  RAISE NOTICE '  - CRM Deals per Company: % (Total %)', p_deals_per_company, p_companies_count * p_deals_per_company;
  RAISE NOTICE '================================================================================';

  FOR i IN 1..p_companies_count LOOP
    v_co_start_time := clock_timestamp();
    v_base_time := (extract(epoch from clock_timestamp()) * 1000)::bigint - 1704067200000::bigint;
    
    -- Pick or construct company name
    IF i <= array_length(v_names, 1) THEN
      v_company_name := v_names[i];
    ELSE
      v_company_name := 'Company Enterprise ' || i;
    END IF;

    v_clean_name := lower(regexp_replace(v_company_name, '[^a-zA-Z0-9]+', '', 'g'));
    v_slug := 'sim-' || lower(regexp_replace(regexp_replace(v_company_name, '[^a-zA-Z0-9]+', '-', 'g'), '^-|-$', '', 'g')) || '-' || i;
    v_domain := v_clean_name || i || '.com';

    -- Generate Primary IDs
    v_owner_id := ((v_base_time + 10) << 22) | (1 << 12) | 1;
    v_mgr_id   := ((v_base_time + 10) << 22) | (1 << 12) | 2;
    v_agt1_id  := ((v_base_time + 10) << 22) | (1 << 12) | 3;
    v_agt2_id  := ((v_base_time + 10) << 22) | (1 << 12) | 4;
    v_agt3_id  := ((v_base_time + 10) << 22) | (1 << 12) | 5;

    v_company_id := ((v_base_time + 1) << 22) | (1 << 12) | (i % 4096);
    v_sub_id     := ((v_base_time + 2) << 22) | (1 << 12) | (i % 4096);
    v_pipe_id    := ((v_base_time + 3) << 22) | (1 << 12) | (i % 4096);

    -- 1. Insert 5 Users (Owner, Manager, 3 Members)
    INSERT INTO users (id, email, name, is_verified, username, password, two_factor_enabled, status)
    VALUES
      (v_owner_id, 'owner.sim' || i || '@sim.unifydesk.in', v_company_name || ' Owner', true, 'sim_owner_' || i, v_pwd_hash, false, 'ACTIVE'),
      (v_mgr_id,   'mgr.sim' || i || '@sim.unifydesk.in',   v_company_name || ' Manager', true, 'sim_mgr_' || i,   v_pwd_hash, false, 'ACTIVE'),
      (v_agt1_id,  'agt1.sim' || i || '@sim.unifydesk.in',  v_company_name || ' Agent 1', true, 'sim_agt1_' || i,  v_pwd_hash, false, 'ACTIVE'),
      (v_agt2_id,  'agt2.sim' || i || '@sim.unifydesk.in',  v_company_name || ' Agent 2', true, 'sim_agt2_' || i,  v_pwd_hash, false, 'ACTIVE'),
      (v_agt3_id,  'agt3.sim' || i || '@sim.unifydesk.in',  v_company_name || ' Agent 3', true, 'sim_agt3_' || i,  v_pwd_hash, false, 'ACTIVE')
    ON CONFLICT (email) DO NOTHING;

    -- 2. Insert Company
    INSERT INTO companies (id, name, slug, business_email, country, timezone, status, created_by_user_id, created_at, updated_at)
    VALUES (v_company_id, v_company_name, v_slug, 'contact@' || v_domain, 'India', 'Asia/Kolkata', 'ACTIVE', v_owner_id, now() - interval '60 days', now())
    ON CONFLICT (slug) DO UPDATE SET updated_at = now();

    -- 3. Insert Company Wallet (₹25,000 INR balance, active auto recharge)
    INSERT INTO company_wallets (company_id, balance, currency, auto_recharge_enabled, auto_recharge_threshold, auto_recharge_amount, version)
    VALUES (v_company_id, 25000.00, 'INR', true, 1000.00, 5000.00, 0)
    ON CONFLICT (company_id) DO UPDATE SET balance = 25000.00;

    -- 4. Insert Company Subscription (Active Enterprise Plan)
    INSERT INTO company_subscriptions (id, company_id, plan_id, plan_price_id, status, started_at, current_period_start, current_period_end, retention_until, created_at, updated_at)
    VALUES (v_sub_id, v_company_id, v_plan_id, v_price_id, 'ACTIVE', now() - interval '30 days', now() - interval '5 days', now() + interval '25 days', now() + interval '8 years', now(), now())
    ON CONFLICT (id) DO NOTHING;

    -- 5. Insert Company Members (including Super Admin as OWNER)
    INSERT INTO company_members (id, company_id, user_id, role, status, created_at, updated_at)
    VALUES
      (((v_base_time + 20) << 22) | (1 << 12) | 1, v_company_id, v_owner_id, 'OWNER', 'ACTIVE', now(), now()),
      (((v_base_time + 20) << 22) | (1 << 12) | 2, v_company_id, v_mgr_id,   'MANAGER', 'ACTIVE', now(), now()),
      (((v_base_time + 20) << 22) | (1 << 12) | 3, v_company_id, v_agt1_id,  'MEMBER', 'ACTIVE', now(), now()),
      (((v_base_time + 20) << 22) | (1 << 12) | 4, v_company_id, v_agt2_id,  'MEMBER', 'ACTIVE', now(), now()),
      (((v_base_time + 20) << 22) | (1 << 12) | 5, v_company_id, v_agt3_id,  'MEMBER', 'ACTIVE', now(), now()),
      (((v_base_time + 20) << 22) | (1 << 12) | 6, v_company_id, v_superadmin_id, 'OWNER', 'ACTIVE', now(), now())
    ON CONFLICT (company_id, user_id) DO UPDATE SET role = 'OWNER', status = 'ACTIVE';

    INSERT INTO admin_company_assignments (id, admin_user_id, company_id, assigned_by_user_id, created_at)
    VALUES
      (((v_base_time + 21) << 22) | (1 << 12) | 1, v_superadmin_id, v_company_id, v_superadmin_id, now())
    ON CONFLICT (admin_user_id, company_id) DO NOTHING;

    -- 6. Insert Company Custom Roles
    INSERT INTO company_custom_roles (id, company_id, name, description, system_role, created_by_user_id, created_at, updated_at)
    VALUES
      (((v_base_time + 30) << 22) | (1 << 12) | 1, v_company_id, 'Owner', 'System role representing owner', true, v_owner_id, now(), now()),
      (((v_base_time + 30) << 22) | (1 << 12) | 2, v_company_id, 'Manager', 'System role representing manager', true, v_owner_id, now(), now()),
      (((v_base_time + 30) << 22) | (1 << 12) | 3, v_company_id, 'Member', 'System role representing member', true, v_owner_id, now(), now())
    ON CONFLICT DO NOTHING;

    -- 7. Insert CRM Task Columns (Required for task foreign keys)
    INSERT INTO crm_task_columns (company_id, column_key, name, status_group, position, system_column)
    VALUES
      (v_company_id, 'OPEN', 'To do', 'OPEN', 0, true),
      (v_company_id, 'IN_PROGRESS', 'In progress', 'IN_PROGRESS', 1, true),
      (v_company_id, 'DONE', 'Done', 'DONE', 2, true),
      (v_company_id, 'CANCELLED', 'Cancelled', 'CANCELLED', 3, true)
    ON CONFLICT DO NOTHING;

    -- 8. Insert Sales Pipeline & 6 Stages
    INSERT INTO crm_pipelines (id, company_id, name, description, is_default, display_order)
    VALUES (v_pipe_id, v_company_id, 'Standard Sales Pipeline', 'Default sales pipeline', true, 0)
    ON CONFLICT DO NOTHING;

    INSERT INTO crm_pipeline_stages (id, company_id, pipeline_id, key, name, category, probability, display_order, color)
    VALUES
      (((v_base_time + 40) << 22) | (1 << 12) | 1, v_company_id, v_pipe_id, 'NEW', 'New', 'OPEN', 10, 1, 'sky'),
      (((v_base_time + 40) << 22) | (1 << 12) | 2, v_company_id, v_pipe_id, 'QUALIFIED', 'Qualified', 'OPEN', 25, 2, 'indigo'),
      (((v_base_time + 40) << 22) | (1 << 12) | 3, v_company_id, v_pipe_id, 'PROPOSAL', 'Proposal', 'OPEN', 50, 3, 'violet'),
      (((v_base_time + 40) << 22) | (1 << 12) | 4, v_company_id, v_pipe_id, 'NEGOTIATION', 'Negotiation', 'OPEN', 75, 4, 'amber'),
      (((v_base_time + 40) << 22) | (1 << 12) | 5, v_company_id, v_pipe_id, 'WON', 'Won', 'WON', 100, 5, 'emerald'),
      (((v_base_time + 40) << 22) | (1 << 12) | 6, v_company_id, v_pipe_id, 'LOST', 'Lost', 'LOST', 0, 6, 'rose')
    ON CONFLICT DO NOTHING;

    -- 9. Insert 3 Email Channels + 3 WhatsApp Channels (Total 6 per company)
    INSERT INTO channel_connections (id, company_id, channel_type, provider_type, name, external_account_id, status, created_by_user_id, created_at, updated_at)
    SELECT
      ((v_base_time + 700 + ch) << 22) | (1 << 12) | ch,
      v_company_id,
      case when ch <= 3 then 'EMAIL' else 'WHATSAPP' end,
      case when ch <= 3 then 'IMAP_SMTP' else 'META_WHATSAPP' end,
      case
        when ch = 1 then 'Support Email'
        when ch = 2 then 'Sales Inquiries'
        when ch = 3 then 'Billing Desk'
        when ch = 4 then 'Customer Care WhatsApp'
        when ch = 5 then 'Sales Inquiries WhatsApp'
        else 'Alerts and Notifications WhatsApp'
      end,
      case
        when ch = 1 then 'support@' || v_domain
        when ch = 2 then 'sales@' || v_domain
        when ch = 3 then 'billing@' || v_domain
        when ch = 4 then '+919100' || lpad(i::text, 3, '0') || '001'
        when ch = 5 then '+919100' || lpad(i::text, 3, '0') || '002'
        else '+919100' || lpad(i::text, 3, '0') || '003'
      end,
      'CONNECTED',
      v_owner_id,
      now() - interval '45 days',
      now()
    FROM generate_series(1, 6) ch
    ON CONFLICT DO NOTHING;

    -- Sender Identities
    INSERT INTO sender_identities (id, connection_id, identity_type, value, status, is_default, created_at, updated_at)
    SELECT
      ((v_base_time + 710 + ch) << 22) | (1 << 12) | ch,
      ((v_base_time + 700 + ch) << 22) | (1 << 12) | ch,
      case when ch <= 3 then 'EMAIL_ADDRESS' else 'PHONE_NUMBER' end,
      case
        when ch = 1 then 'support@' || v_domain
        when ch = 2 then 'sales@' || v_domain
        when ch = 3 then 'billing@' || v_domain
        when ch = 4 then '+919100' || lpad(i::text, 3, '0') || '001'
        when ch = 5 then '+919100' || lpad(i::text, 3, '0') || '002'
        else '+919100' || lpad(i::text, 3, '0') || '003'
      end,
      'ACTIVE',
      (ch IN (1, 4)),
      now(),
      now()
    FROM generate_series(1, 6) ch
    ON CONFLICT DO NOTHING;

    -- WhatsApp Meta Connections
    INSERT INTO meta_whatsapp_connections (
      connection_id, company_id, account_model_version, legacy_waba_id, whatsapp_account_id,
      selected_phone_number_id, product_state, auth_state, registration_state, subscription_state, created_at, updated_at
    )
    SELECT
      ((v_base_time + 700 + ch) << 22) | (1 << 12) | ch,
      v_company_id,
      'LEGACY_WABA',
      'waba_' || (((v_base_time + 700 + ch) << 22) | (1 << 12) | ch),
      'waba_' || (((v_base_time + 700 + ch) << 22) | (1 << 12) | ch),
      'phone_' || (((v_base_time + 700 + ch) << 22) | (1 << 12) | ch),
      'ACTIVE', 'ACTIVE', 'ACTIVE', 'SUBSCRIBED', now(), now()
    FROM generate_series(4, 6) ch
    ON CONFLICT DO NOTHING;

    -- 10. Bulk Insert Contacts (p_contacts_per_company)
    INSERT INTO contacts (id, company_id, first_name, last_name, display_name, organization, job_title, country_code, timezone, language, source, owner_user_id, status, created_by_user_id, created_at, updated_at, version)
    SELECT
      ((v_base_time + 100 + (s / 4000)) << 22) | (1 << 12) | (s % 4096),
      v_company_id,
      v_first_names[(s % array_length(v_first_names, 1)) + 1],
      v_last_names[(s % array_length(v_last_names, 1)) + 1],
      v_first_names[(s % array_length(v_first_names, 1)) + 1] || ' ' || v_last_names[(s % array_length(v_last_names, 1)) + 1],
      'Client Corp ' || ((s % 100) + 1),
      case when s % 4 = 0 then 'VP of Operations' when s % 4 = 1 then 'Procurement Lead' when s % 4 = 2 then 'General Manager' else 'Senior Executive' end,
      'IN',
      'Asia/Kolkata',
      'en',
      case when s % 3 = 0 then 'WEB' when s % 3 = 1 then 'IMPORT' else 'WHATSAPP' end,
      case when s % 3 = 0 then v_agt1_id when s % 3 = 1 then v_agt2_id else v_agt3_id end,
      'ACTIVE',
      v_owner_id,
      now() - (interval '1 hour' * (s % 720)),
      now(),
      0
    FROM generate_series(1, p_contacts_per_company) s
    ON CONFLICT DO NOTHING;

    -- 11. Bulk Insert Contact Identities (Email + Phone)
    INSERT INTO contact_identities (id, company_id, contact_id, identity_type, value, normalized_value, is_primary, status, created_at, updated_at)
    SELECT
      ((v_base_time + 300 + (s / 4000)) << 22) | (1 << 12) | (s % 4096),
      v_company_id,
      ((v_base_time + 100 + (s / 4000)) << 22) | (1 << 12) | (s % 4096),
      'EMAIL',
      'client_' || i || '_' || s || '@customer-reach.com',
      'client_' || i || '_' || s || '@customer-reach.com',
      true,
      'ACTIVE',
      now(),
      now()
    FROM generate_series(1, p_contacts_per_company) s
    ON CONFLICT DO NOTHING;

    INSERT INTO contact_identities (id, company_id, contact_id, identity_type, value, normalized_value, is_primary, status, created_at, updated_at)
    SELECT
      ((v_base_time + 500 + (s / 4000)) << 22) | (1 << 12) | (s % 4096),
      v_company_id,
      ((v_base_time + 100 + (s / 4000)) << 22) | (1 << 12) | (s % 4096),
      'PHONE',
      '+91' || ((8000000000::bigint + ((i * 100000::bigint) + s))::text),
      '+91' || ((8000000000::bigint + ((i * 100000::bigint) + s))::text),
      true,
      'ACTIVE',
      now(),
      now()
    FROM generate_series(1, p_contacts_per_company) s
    ON CONFLICT DO NOTHING;

    -- 12. Insert 20 CRM Customer Accounts
    INSERT INTO crm_accounts (id, company_id, name, normalized_name, website, notes, owner_user_id, version, created_at, updated_at)
    SELECT
      ((v_base_time + 600) << 22) | (1 << 12) | a,
      v_company_id,
      'Partner Account ' || a || ' (' || v_company_name || ')',
      lower('partner account ' || a || ' (' || v_company_name || ')'),
      'https://partner' || a || '.org',
      'Strategic B2B relationship established for annual procurement.',
      case when a % 2 = 0 then v_agt1_id else v_agt2_id end,
      0,
      now() - (interval '1 day' * a),
      now()
    FROM generate_series(1, 20) a
    ON CONFLICT DO NOTHING;

    -- 13. Insert 30 CRM Sales Deals across stages
    INSERT INTO crm_deals (id, company_id, title, description, stage, amount, currency, expected_close_date, owner_user_id, contact_id, account_id, pipeline_id, closed_at, version, created_at, updated_at)
    SELECT
      ((v_base_time + 650) << 22) | (1 << 12) | d,
      v_company_id,
      case
        when d % 6 = 1 then 'Annual Cloud Infrastructure License #' || d
        when d % 6 = 2 then 'Enterprise Tier Support Package #' || d
        when d % 6 = 3 then 'Custom Integration and Data Migration #' || d
        when d % 6 = 4 then 'Multi-Channel Campaign Retainer #' || d
        when d % 6 = 5 then 'Strategic Partnership Contract #' || d
        else 'Procurement Renewal Agreement #' || d
      end,
      'Standard high-value commercial opportunity with prospective enterprise customer.',
      case
        when d % 6 = 1 then 'NEW'
        when d % 6 = 2 then 'QUALIFIED'
        when d % 6 = 3 then 'PROPOSAL'
        when d % 6 = 4 then 'NEGOTIATION'
        when d % 6 = 5 then 'WON'
        else 'LOST'
      end,
      (50000 + (d * 35000))::numeric(20,3),
      'INR',
      (current_date + (d * 3)),
      case when d % 3 = 0 then v_agt1_id when d % 3 = 1 then v_agt2_id else v_agt3_id end,
      ((v_base_time + 100 + (d / 4000)) << 22) | (1 << 12) | (d % 4096),
      ((v_base_time + 600) << 22) | (1 << 12) | ((d % 20) + 1),
      v_pipe_id,
      case when (d % 6) in (5, 0) then (now() - interval '5 days') else null end,
      0,
      now() - (interval '1 day' * (40 - d)),
      now()
    FROM generate_series(1, p_deals_per_company) d
    ON CONFLICT DO NOTHING;

    -- 14. Insert CRM Tasks (p_tasks_per_company)
    INSERT INTO crm_tasks (id, company_id, title, details, status, priority, due_at, assignee_user_id, created_by_user_id, column_key, task_type, reminder_generation, created_at, updated_at, completed_at)
    SELECT
      ((v_base_time + 680) << 22) | (1 << 12) | tk,
      v_company_id,
      case
        when tk % 5 = 1 then 'Follow up on Enterprise Proposal with Partner #' || ((tk % 20) + 1)
        when tk % 5 = 2 then 'Schedule Product Demo and Onboarding Walkthrough'
        when tk % 5 = 3 then 'Review SLA terms and finalize pricing addendum'
        when tk % 5 = 4 then 'Send updated GST invoice and verify payment status'
        else 'Quarterly Business Review and Account Check-in'
      end,
      'High priority action item to ensure timely milestone completion.',
      case
        when tk % 3 = 1 then 'OPEN'
        when tk % 3 = 2 then 'IN_PROGRESS'
        else 'DONE'
      end,
      case when tk % 3 = 1 then 'HIGH' when tk % 3 = 2 then 'NORMAL' else 'LOW' end,
      now() + (interval '1 day' * ((tk % 14) - 3)),
      case when tk % 4 = 0 then v_agt1_id when tk % 4 = 1 then v_agt2_id when tk % 4 = 2 then v_agt3_id else v_mgr_id end,
      v_owner_id,
      case
        when tk % 3 = 1 then 'OPEN'
        when tk % 3 = 2 then 'IN_PROGRESS'
        else 'DONE'
      end,
      'TODO',
      0,
      now() - (interval '1 day' * (tk % 15)),
      now(),
      case when tk % 3 = 0 then (now() - interval '2 days') else null end
    FROM generate_series(1, p_tasks_per_company) tk
    ON CONFLICT DO NOTHING;

    -- 15. Insert 120 Conversations across the 6 channels (20 conversations per channel)
    INSERT INTO inbox_conversations (id, company_id, connection_id, provider_type, external_conversation_id, subject, last_message_preview, last_message_at, unread_count, status, created_at, updated_at)
    SELECT
      ((v_base_time + 800 + ((ch - 1) * v_convs_per_channel) + cv) << 22) | (1 << 12) | (((ch - 1) * v_convs_per_channel) + cv),
      v_company_id,
      ((v_base_time + 700 + ch) << 22) | (1 << 12) | ch,
      case when ch <= 3 then 'IMAP_SMTP' else 'META_WHATSAPP' end,
      'conv_' || (((v_base_time + 800 + ((ch - 1) * v_convs_per_channel) + cv) << 22) | (1 << 12) | (((ch - 1) * v_convs_per_channel) + cv)),
      case
        when ch <= 3 then 'Service Inquiry & Quote Request #' || (ch * 100 + cv)
        else 'WhatsApp Query from ' || v_first_names[(cv % array_length(v_first_names, 1)) + 1]
      end,
      'Thank you for confirming. We will process this immediately.',
      now() - (interval '10 minutes' * cv),
      case when cv <= 3 then 2 else 0 end,
      'OPEN',
      now() - interval '20 days',
      now()
    FROM generate_series(1, 6) ch
    CROSS JOIN generate_series(1, v_convs_per_channel) cv
    ON CONFLICT DO NOTHING;

    -- 16. Insert Participants & Contact Links for all 120 conversations
    INSERT INTO inbox_participants (id, conversation_id, participant_type, address, display_name, created_at, updated_at)
    SELECT
      ((v_base_time + 900 + ((ch - 1) * v_convs_per_channel) + cv) << 22) | (1 << 12) | (cv * 2 - 1),
      ((v_base_time + 800 + ((ch - 1) * v_convs_per_channel) + cv) << 22) | (1 << 12) | (((ch - 1) * v_convs_per_channel) + cv),
      case when ch <= 3 then 'EMAIL_ADDRESS' else 'PHONE_NUMBER' end,
      case
        when ch <= 3 then 'client_' || i || '_' || cv || '@customer-reach.com'
        else '+91' || ((8000000000::bigint + ((i * 100000::bigint) + cv))::text)
      end,
      v_first_names[(cv % array_length(v_first_names, 1)) + 1] || ' ' || v_last_names[(cv % array_length(v_last_names, 1)) + 1],
      now() - interval '20 days',
      now()
    FROM generate_series(1, 6) ch
    CROSS JOIN generate_series(1, v_convs_per_channel) cv
    ON CONFLICT DO NOTHING;

    INSERT INTO inbox_participant_contact_links (
      id, company_id, conversation_id, participant_address, identity_type, normalized_identity,
      contact_id, contact_identity_id, resolution, resolved_at, created_at, updated_at
    )
    SELECT
      ((v_base_time + 950 + ((ch - 1) * v_convs_per_channel) + cv) << 22) | (1 << 12) | cv,
      v_company_id,
      ((v_base_time + 800 + ((ch - 1) * v_convs_per_channel) + cv) << 22) | (1 << 12) | (((ch - 1) * v_convs_per_channel) + cv),
      case
        when ch <= 3 then 'client_' || i || '_' || cv || '@customer-reach.com'
        else '+91' || ((8000000000::bigint + ((i * 100000::bigint) + cv))::text)
      end,
      case when ch <= 3 then 'EMAIL' else 'PHONE' end,
      case
        when ch <= 3 then 'client_' || i || '_' || cv || '@customer-reach.com'
        else '+91' || ((8000000000::bigint + ((i * 100000::bigint) + cv))::text)
      end,
      ((v_base_time + 100 + (cv / 4000)) << 22) | (1 << 12) | (cv % 4096),
      case
        when ch <= 3 then (((v_base_time + 300 + (cv / 4000)) << 22) | (1 << 12) | (cv % 4096))
        else (((v_base_time + 500 + (cv / 4000)) << 22) | (1 << 12) | (cv % 4096))
      end,
      'MATCHED',
      now(),
      now(),
      now()
    FROM generate_series(1, 6) ch
    CROSS JOIN generate_series(1, v_convs_per_channel) cv
    ON CONFLICT DO NOTHING;

    -- 17. Bulk Insert Messages (Total p_messages_per_channel * 6 per company) in ONE Set-based query!
    INSERT INTO inbox_messages (
      id, conversation_id, connection_id, external_message_id, direction, status,
      subject, snippet, body_text, sent_at, is_read, storage_bytes, provider_folder_key, created_at, updated_at
    )
    SELECT
      ((v_base_time + 1000 + (t.conv_num * v_msgs_per_conv) + (m / 4000)) << 22) | (1 << 12) | ((t.conv_num * v_msgs_per_conv + m) % 4096),
      t.conv_id,
      t.conn_id,
      'msg_' || t.conv_id || '_' || m,
      case when m % 2 = 1 then 'INBOUND' else 'OUTBOUND' end,
      case when m % 2 = 1 then 'RECEIVED' else 'SENT' end,
      case when t.channel_num <= 3 then 'Re: Inquiry Ticket #' || (t.channel_num * 100 + t.conv_seq) else null end,
      case
        when m % 4 = 1 then 'Hello team, following up regarding the latest service update.'
        when m % 4 = 2 then 'Hi! We received your request and our team is looking into it.'
        when m % 4 = 3 then 'Can you please confirm the delivery ETA and tracking number?'
        else 'The tracking details have been verified and updated successfully.'
      end,
      case
        when m % 4 = 1 then 'Hello support team, I would like to check the current delivery progress and verify our account details.'
        when m % 4 = 2 then 'Greetings! We have verified your account information. Everything is active and operating normally.'
        when m % 4 = 3 then 'Thank you! Could you also share the latest monthly performance statement for our accounting records?'
        else 'Certainly! The latest statement has been attached and sent directly to your registered correspondence channel.'
      end,
      now() - (interval '1 hour' * ((v_msgs_per_conv - m) * 4 + 1)),
      (m < v_msgs_per_conv - 1),
      128,
      case when t.channel_num <= 3 then 'INBOX' else 'WHATSAPP' end,
      now(),
      now()
    FROM (
      SELECT
        ((v_base_time + 800 + ((ch - 1) * v_convs_per_channel) + cv) << 22) | (1 << 12) | (((ch - 1) * v_convs_per_channel) + cv) AS conv_id,
        ((v_base_time + 700 + ch) << 22) | (1 << 12) | ch AS conn_id,
        ch AS channel_num,
        cv AS conv_seq,
        ((ch - 1) * v_convs_per_channel + cv) AS conv_num
      FROM generate_series(1, 6) ch
      CROSS JOIN generate_series(1, v_convs_per_channel) cv
    ) t
    CROSS JOIN generate_series(1, v_msgs_per_conv) m
    ON CONFLICT DO NOTHING;

    -- Commit after each company to release locks and manage memory cleanly
    COMMIT;

    RAISE NOTICE '[% / %] Company "%" simulated in % ms (5 users, 6 channels, % contacts, % msgs, % tasks, % deals)',
      i,
      p_companies_count,
      v_company_name,
      (extract(epoch from (clock_timestamp() - v_co_start_time)) * 1000)::int,
      p_contacts_per_company,
      p_messages_per_channel * 6,
      p_tasks_per_company,
      p_deals_per_company;
  END LOOP;

  RAISE NOTICE '================================================================================';
  RAISE NOTICE 'SIMULATION COMPLETED IN % SECONDS!', (extract(epoch from (clock_timestamp() - v_sim_start_time)))::int;
  RAISE NOTICE 'All % companies are ready with channels, contacts, messages, and tasks.', p_companies_count;
  RAISE NOTICE '================================================================================';
END;
$$;
