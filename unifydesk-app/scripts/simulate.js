#!/usr/bin/env node

/**
 * UnifyDesk Enterprise Scale Simulation CLI
 * 
 * Simulates realistic enterprise companies with:
 *   - At least 100 companies
 *   - 5 users per company (Owner, Manager, 3 Agents) with Password@123
 *   - 3 Email channels + 3 WhatsApp channels per company (600 channels)
 *   - 1,000 messages per channel (6,000 msgs/company, 600,000 total)
 *   - 10,000 to 50,000 contacts per company (1,000,000+ contacts total)
 *   - 50 CRM tasks, 30 CRM deals, 20 CRM accounts per company
 *   - ₹25,000 Wallet balance & Active Enterprise Subscription
 * 
 * Usage:
 *   node scripts/simulate.js --companies 100 --contacts 10000 --messages 1000
 *   node scripts/simulate.js --status
 *   node scripts/simulate.js --cleanup
 */

import { spawnSync } from 'child_process';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Color helpers
const cyan = (t) => `\x1b[36m${t}\x1b[0m`;
const green = (t) => `\x1b[32m${t}\x1b[0m`;
const yellow = (t) => `\x1b[33m${t}\x1b[0m`;
const red = (t) => `\x1b[31m${t}\x1b[0m`;
const bold = (t) => `\x1b[1m${t}\x1b[0m`;
const dim = (t) => `\x1b[2m${t}\x1b[0m`;

function parseArgs() {
  const args = process.argv.slice(2);
  const options = {
    companies: 100,
    contacts: 10000,
    messages: 1000,
    tasks: 50,
    deals: 30,
    cleanup: false,
    status: false,
    help: false
  };

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (arg === '--companies' || arg === '-c') {
      options.companies = parseInt(args[++i], 10) || 100;
    } else if (arg === '--contacts' || arg === '-k') {
      options.contacts = parseInt(args[++i], 10) || 10000;
    } else if (arg === '--messages' || arg === '-m') {
      options.messages = parseInt(args[++i], 10) || 1000;
    } else if (arg === '--tasks' || arg === '-t') {
      options.tasks = parseInt(args[++i], 10) || 50;
    } else if (arg === '--deals' || arg === '-d') {
      options.deals = parseInt(args[++i], 10) || 30;
    } else if (arg === '--cleanup' || arg === '-x') {
      options.cleanup = true;
    } else if (arg === '--status' || arg === '-s') {
      options.status = true;
    } else if (arg === '--help' || arg === '-h') {
      options.help = true;
    }
  }

  return options;
}

function runPsql(sqlCommand) {
  const res = spawnSync('docker', [
    'exec', '-i', 'messaging-postgres',
    'psql', '-U', 'unifydesk', '-d', 'unifydesk', '-p', '5002',
    '-c', sqlCommand
  ], { encoding: 'utf-8' });

  if (res.error) {
    throw res.error;
  }
  return res.stdout || res.stderr || '';
}

function ensureProcedures() {
  const sqlFile = path.join(__dirname, 'simulate_companies_dataset.sql');
  const res = spawnSync('docker', [
    'exec', '-i', 'messaging-postgres',
    'psql', '-U', 'unifydesk', '-d', 'unifydesk', '-p', '5002'
  ], {
    input: spawnSync('cat', [sqlFile], { encoding: 'utf-8' }).stdout || '',
    encoding: 'utf-8',
    shell: true
  });
}

function showStatus() {
  console.log(bold(cyan('\n=== Current UnifyDesk Simulated Dataset Status ===\n')));
  const query = `
    SELECT 'Companies' AS Entity, count(*)::text AS Count FROM companies WHERE slug LIKE 'sim-%'
    UNION ALL
    SELECT 'Users', count(*)::text FROM users WHERE email LIKE '%@sim.unifydesk.in'
    UNION ALL
    SELECT 'Channels (3 Email + 3 WhatsApp)', count(*)::text FROM channel_connections WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%')
    UNION ALL
    SELECT 'WhatsApp Meta Connections', count(*)::text FROM meta_whatsapp_connections WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%')
    UNION ALL
    SELECT 'Contacts', count(*)::text FROM contacts WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%')
    UNION ALL
    SELECT 'Contact Identities (Email+Phone)', count(*)::text FROM contact_identities WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%')
    UNION ALL
    SELECT 'Inbox Conversations', count(*)::text FROM inbox_conversations WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%')
    UNION ALL
    SELECT 'Inbox Messages', count(*)::text FROM inbox_messages WHERE connection_id IN (SELECT id FROM channel_connections WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%'))
    UNION ALL
    SELECT 'CRM Tasks', count(*)::text FROM crm_tasks WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%')
    UNION ALL
    SELECT 'CRM Deals', count(*)::text FROM crm_deals WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%')
    UNION ALL
    SELECT 'CRM Accounts', count(*)::text FROM crm_accounts WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');
  `;

  const output = runPsql(query);
  console.log(output);
}

function runCleanup() {
  console.log(yellow('\nCleaning up all simulated companies, users, messages, channels and tasks...'));
  const output = runPsql('CALL cleanup_simulated_test_data();');
  console.log(green('✓ Cleanup finished successfully.'));
  console.log(dim(output.trim()));
}

function runSimulation(opts) {
  console.log(bold(cyan('\n============================================================')));
  console.log(bold(cyan('      UNIFYDESK ENTERPRISE DATASET SIMULATOR                ')));
  console.log(bold(cyan('============================================================')));
  console.log(`\nSimulation Parameters:`);
  console.log(`  • Companies:           ${bold(opts.companies)}`);
  console.log(`  • Users per Company:   ${bold('5')} (Total: ${opts.companies * 5} users)`);
  console.log(`  • Channels / Company:  ${bold('6')} (3 Email + 3 WhatsApp, Total: ${opts.companies * 6} channels)`);
  console.log(`  • Messages / Channel:  ${bold(opts.messages)} (Total: ${opts.companies * 6 * opts.messages} messages)`);
  console.log(`  • Contacts / Company:  ${bold(opts.contacts)} (Total: ${opts.companies * opts.contacts} contacts)`);
  console.log(`  • CRM Tasks / Company: ${bold(opts.tasks)} (Total: ${opts.companies * opts.tasks} tasks)`);
  console.log(`  • CRM Deals / Company: ${bold(opts.deals)} (Total: ${opts.companies * opts.deals} deals)`);
  console.log(`  • Default User Login:  ${bold('owner.sim1@sim.unifydesk.in')} / ${bold('Password@123')}`);
  console.log(bold(cyan('============================================================\n')));

  console.log(yellow('Ensuring database procedures are compiled...'));
  ensureProcedures();

  console.log(green('Starting batch generation in PostgreSQL (with per-company commits)...\n'));
  const start = Date.now();

  const callQuery = `CALL simulate_test_dataset(${opts.companies}, ${opts.contacts}, ${opts.messages}, ${opts.tasks}, ${opts.deals});`;
  const result = runPsql(callQuery);
  const duration = ((Date.now() - start) / 1000).toFixed(1);

  console.log(result);
  console.log(bold(green(`\n✓ Simulation completed successfully in ${duration}s!\n`)));
  showStatus();
}

// Main
const opts = parseArgs();

if (opts.help) {
  console.log(`
${bold('UnifyDesk Enterprise Scale Simulation Tool')}

${bold('Usage:')}
  node scripts/simulate.js [options]

${bold('Options:')}
  -c, --companies <n>   Number of companies to simulate (default: 100)
  -k, --contacts <n>    Contacts per company (default: 10000, e.g. 10000 to 50000)
  -m, --messages <n>    Messages per channel (default: 1000)
  -t, --tasks <n>       CRM tasks per company (default: 50)
  -d, --deals <n>       CRM deals per company (default: 30)
  -s, --status          Check count of currently simulated records
  -x, --cleanup         Remove all simulated test companies and records
  -h, --help            Show this help menu
`);
  process.exit(0);
}

if (opts.cleanup) {
  runCleanup();
} else if (opts.status) {
  showStatus();
} else {
  runSimulation(opts);
}
