#!/usr/bin/env node

/**
 * UnifyDesk 50 Concurrent Active Users Load Testing Tool
 * 
 * Simulates 50 distinct concurrent active users simultaneously performing realistic
 * workloads across the system:
 *   - Authentication & session negotiation
 *   - Workspace & company switching
 *   - Inbox conversations & message inspection
 *   - Contact directory search & browsing (across 1M contacts)
 *   - CRM Kanban tasks & Sales pipelines
 *   - Billing wallet checks
 *   - Real-time CRM task creation (write load)
 * 
 * Reports detailed latency percentiles (p50, p90, p95, p99), throughput (RPS),
 * and container resource consumption.
 */

import { performance } from 'perf_hooks';
import { spawnSync } from 'child_process';

const BASE_URL = process.env.BASE_URL || 'http://localhost:8001';
const CONCURRENT_USERS = parseInt(process.env.USERS || '50', 10);
const TEST_DURATION_SECS = parseInt(process.env.DURATION || '30', 10);
const INCLUDE_WRITES = process.env.WRITES !== 'false';

// ANSI Colors
const cyan = (t) => `\x1b[36m${t}\x1b[0m`;
const green = (t) => `\x1b[32m${t}\x1b[0m`;
const yellow = (t) => `\x1b[33m${t}\x1b[0m`;
const red = (t) => `\x1b[31m${t}\x1b[0m`;
const bold = (t) => `\x1b[1m${t}\x1b[0m`;
const dim = (t) => `\x1b[2m${t}\x1b[0m`;

function getDockerStats() {
  try {
    const res = spawnSync('docker', ['stats', '--no-stream', '--format', '{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}'], {
      encoding: 'utf-8',
      timeout: 5000
    });
    if (!res.stdout) return null;
    const lines = res.stdout.trim().split('\n');
    const stats = {};
    for (const line of lines) {
      const [name, cpu, mem] = line.split('\t');
      if (name) stats[name.trim()] = { cpu: (cpu || '').trim(), mem: (mem || '').trim() };
    }
    return stats;
  } catch (e) {
    return null;
  }
}

class UserSession {
  constructor(userIdx) {
    this.userIdx = userIdx;
    this.email = `owner.sim${userIdx}@sim.unifydesk.in`;
    this.password = 'Password@123';
    this.xsrfToken = '';
    this.msidCookie = '';
    this.companyId = null;
    this.companyName = '';
  }

  async init() {
    // 1. CSRF
    const csrfRes = await fetch(`${BASE_URL}/api/auth/csrf`);
    const csrfRawCookies = csrfRes.headers.getSetCookie ? csrfRes.headers.getSetCookie() : [csrfRes.headers.get('set-cookie')];
    for (const c of csrfRawCookies) {
      const match = c && c.match(/XSRF-TOKEN=([^;]+)/);
      if (match) this.xsrfToken = match[1];
    }

    // 2. Login
    const loginRes = await fetch(`${BASE_URL}/api/auth/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Cookie': `XSRF-TOKEN=${this.xsrfToken}`,
        'X-XSRF-TOKEN': this.xsrfToken
      },
      body: JSON.stringify({
        identifier: this.email,
        password: this.password
      })
    });

    if (loginRes.status !== 200) {
      throw new Error(`Login failed for ${this.email} with HTTP ${loginRes.status}`);
    }

    const loginCookies = loginRes.headers.getSetCookie ? loginRes.headers.getSetCookie() : [loginRes.headers.get('set-cookie')];
    for (const c of loginCookies) {
      const match = c && c.match(/msid=([^;]+)/);
      if (match) this.msidCookie = match[0];
    }

    // 3. Resolve Workspace / Company
    const compRes = await fetch(`${BASE_URL}/api/v1/companies/me`, {
      headers: { 'Cookie': this.msidCookie }
    });
    const compJson = await compRes.json();
    if (compJson.data && compJson.data.length > 0) {
      this.companyId = compJson.data[0].companyId;
      this.companyName = compJson.data[0].name;
    } else {
      throw new Error(`No company workspace found for ${this.email}`);
    }
  }

  get headers() {
    return {
      'Cookie': `${this.msidCookie}; XSRF-TOKEN=${this.xsrfToken}`,
      'X-XSRF-TOKEN': this.xsrfToken,
      'Content-Type': 'application/json'
    };
  }
}

async function runBenchmark() {
  console.log(bold(cyan('\n============================================================')));
  console.log(bold(cyan('      UNIFYDESK 50 CONCURRENT ACTIVE USERS LOAD TEST        ')));
  console.log(bold(cyan('============================================================')));
  console.log(`Target Backend:       ${bold(BASE_URL)}`);
  console.log(`Concurrent Users:     ${bold(CONCURRENT_USERS)} active users at once`);
  console.log(`Test Duration:        ${bold(TEST_DURATION_SECS)} seconds`);
  console.log(`Write Workload:       ${bold(INCLUDE_WRITES ? 'Enabled (Task Creation)' : 'Disabled')}`);
  console.log(bold(cyan('============================================================\n')));

  const initialStats = getDockerStats();
  if (initialStats) {
    console.log(dim(`Baseline Container Stats:`));
    console.log(dim(`  • backend:  CPU ${initialStats['messaging-backend']?.cpu || 'N/A'}, RAM ${initialStats['messaging-backend']?.mem || 'N/A'}`));
    console.log(dim(`  • postgres: CPU ${initialStats['messaging-postgres']?.cpu || 'N/A'}, RAM ${initialStats['messaging-postgres']?.mem || 'N/A'}\n`));
  }

  // 1. Session Setup
  process.stdout.write(`Authenticating and establishing sessions for ${CONCURRENT_USERS} users... `);
  const userSessions = [];
  const initStart = performance.now();

  const initPromises = Array.from({ length: CONCURRENT_USERS }, async (_, i) => {
    const session = new UserSession(i + 1);
    await session.init();
    return session;
  });

  const sessions = await Promise.all(initPromises);
  const initElapsed = ((performance.now() - initStart) / 1000).toFixed(2);
  console.log(green(`Done in ${initElapsed}s!`));
  console.log(dim(`  (50 unique sessions established across 50 simulated enterprise companies)\n`));

  // Metrics Collectors
  const metrics = {
    totalRequests: 0,
    successfulRequests: 0,
    failedRequests: 0,
    endpoints: {
      'GET /companies/me': [],
      'GET /inbox/conversations': [],
      'GET /contacts': [],
      'GET /crm/tasks': [],
      'GET /crm/deals': [],
      'GET /billing/wallet': [],
      'POST /crm/tasks (Write)': []
    }
  };

  async function executeRequest(endpointName, url, options) {
    const start = performance.now();
    metrics.totalRequests++;
    try {
      const res = await fetch(url, options);
      const duration = performance.now() - start;
      if (res.status >= 200 && res.status < 400) {
        metrics.successfulRequests++;
        metrics.endpoints[endpointName].push(duration);
      } else {
        metrics.failedRequests++;
        // console.error(`Failed ${endpointName}: HTTP ${res.status}`);
      }
    } catch (err) {
      metrics.failedRequests++;
    }
  }

  // Worker Loop for each active user
  let stopTest = false;
  let activeWorkers = 0;

  async function userWorker(session) {
    activeWorkers++;
    let loopIteration = 0;

    while (!stopTest) {
      loopIteration++;

      // 1. Workspace check
      await executeRequest('GET /companies/me', `${BASE_URL}/api/v1/companies/me`, {
        headers: session.headers
      });
      if (stopTest) break;

      // 2. Inbox Conversations
      await executeRequest(
        'GET /inbox/conversations',
        `${BASE_URL}/api/v1/companies/${session.companyId}/inbox/conversations?size=20`,
        { headers: session.headers }
      );
      if (stopTest) break;

      // 3. Contacts Directory (searches 10k contacts for company)
      await executeRequest(
        'GET /contacts',
        `${BASE_URL}/api/v1/companies/${session.companyId}/contacts?size=20`,
        { headers: session.headers }
      );
      if (stopTest) break;

      // 4. CRM Kanban Tasks
      await executeRequest(
        'GET /crm/tasks',
        `${BASE_URL}/api/v1/companies/${session.companyId}/crm/tasks?size=20`,
        { headers: session.headers }
      );
      if (stopTest) break;

      // 5. CRM Deals
      await executeRequest(
        'GET /crm/deals',
        `${BASE_URL}/api/v1/companies/${session.companyId}/crm/deals?size=20`,
        { headers: session.headers }
      );
      if (stopTest) break;

      // 6. Billing Wallet
      await executeRequest(
        'GET /billing/wallet',
        `${BASE_URL}/api/v1/companies/${session.companyId}/billing/wallet`,
        { headers: session.headers }
      );
      if (stopTest) break;

      // 7. Write Action: Create CRM Task (if enabled)
      if (INCLUDE_WRITES && loopIteration % 2 === 0) {
        await executeRequest(
          'POST /crm/tasks (Write)',
          `${BASE_URL}/api/v1/companies/${session.companyId}/crm/tasks`,
          {
            method: 'POST',
            headers: session.headers,
            body: JSON.stringify({
              title: `Live Load Test Task #${loopIteration} from User ${session.userIdx}`,
              details: `Generated during 50 concurrent active users benchmark at ${new Date().toISOString()}`,
              priority: 'NORMAL',
              columnKey: 'OPEN',
              taskType: 'TODO'
            })
          }
        );
      }

      // Small jitter think time (30ms - 80ms)
      const jitter = Math.floor(Math.random() * 50) + 30;
      await new Promise((r) => setTimeout(r, jitter));
    }
    activeWorkers--;
  }

  console.log(bold(green(`🚀 Launching 50 concurrent virtual users now for ${TEST_DURATION_SECS} seconds...`)));
  const testStartTime = performance.now();

  // Progress ticker
  const progressInterval = setInterval(() => {
    const elapsed = Math.floor((performance.now() - testStartTime) / 1000);
    const rps = (metrics.totalRequests / (elapsed || 1)).toFixed(1);
    process.stdout.write(
      `\r[${elapsed}s / ${TEST_DURATION_SECS}s] Active Users: ${bold(cyan(activeWorkers))} | Requests: ${bold(metrics.totalRequests)} (${green(metrics.successfulRequests)} ok, ${red(metrics.failedRequests)} err) | Rate: ${yellow(rps + ' req/s')}`
    );
  }, 1000);

  // Start all 50 workers simultaneously
  const workerPromises = sessions.map((s) => userWorker(s));

  // Run for TEST_DURATION_SECS
  await new Promise((r) => setTimeout(r, TEST_DURATION_SECS * 1000));
  stopTest = true;

  clearInterval(progressInterval);
  await Promise.all(workerPromises);

  const testTotalTime = (performance.now() - testStartTime) / 1000;
  console.log(`\n\n${bold(green('✓ Load test run completed!'))}\n`);

  // Compute Statistics
  function calcPercentile(arr, p) {
    if (arr.length === 0) return 0;
    const sorted = [...arr].sort((a, b) => a - b);
    const idx = Math.floor((p / 100) * sorted.length);
    return sorted[Math.min(idx, sorted.length - 1)].toFixed(1);
  }

  function calcAvg(arr) {
    if (arr.length === 0) return 0;
    const sum = arr.reduce((a, b) => a + b, 0);
    return (sum / arr.length).toFixed(1);
  }

  const allDurations = Object.values(metrics.endpoints).flat();
  const overallAvg = calcAvg(allDurations);
  const overallP50 = calcPercentile(allDurations, 50);
  const overallP90 = calcPercentile(allDurations, 90);
  const overallP95 = calcPercentile(allDurations, 95);
  const overallP99 = calcPercentile(allDurations, 99);
  const overallMin = allDurations.length ? Math.min(...allDurations).toFixed(1) : '0';
  const overallMax = allDurations.length ? Math.max(...allDurations).toFixed(1) : '0';

  const rps = (metrics.totalRequests / testTotalTime).toFixed(1);
  const successRate = ((metrics.successfulRequests / (metrics.totalRequests || 1)) * 100).toFixed(2);

  const peakStats = getDockerStats();

  console.log(bold(cyan('========================================================================')));
  console.log(bold(cyan('                     LOAD TEST PERFORMANCE REPORT                       ')));
  console.log(bold(cyan('========================================================================')));
  console.log(`• Concurrent Active Users:  ${bold(CONCURRENT_USERS)} users`);
  console.log(`• Duration:                 ${testTotalTime.toFixed(1)} seconds`);
  console.log(`• Total HTTP Requests:      ${bold(metrics.totalRequests.toLocaleString())}`);
  console.log(`• Successful Requests:      ${green(metrics.successfulRequests.toLocaleString())} (${successRate}%)`);
  console.log(`• Failed Requests:          ${metrics.failedRequests > 0 ? red(metrics.failedRequests) : green('0')}`);
  console.log(`• Throughput:               ${bold(yellow(rps + ' req/sec'))}`);
  console.log(`\n${bold('Global Response Times:')}`);
  console.log(`  Min: ${overallMin}ms | Avg: ${overallAvg}ms | Median (p50): ${overallP50}ms | p90: ${overallP90}ms | p95: ${overallP95}ms | p99: ${overallP99}ms | Max: ${overallMax}ms`);
  console.log(bold(cyan('------------------------------------------------------------------------')));

  console.log(`\n${bold('Endpoint Breakdown:')}`);
  console.log(`${'Endpoint'.padEnd(28)} | ${'Reqs'.padEnd(7)} | ${'Avg'.padEnd(8)} | ${'p50'.padEnd(8)} | ${'p95'.padEnd(8)} | ${'p99'.padEnd(8)}`);
  console.log('-----------------------------+---------+----------+----------+----------+---------');

  for (const [ep, durs] of Object.entries(metrics.endpoints)) {
    if (durs.length === 0) continue;
    const cnt = durs.length.toString().padEnd(7);
    const avg = `${calcAvg(durs)}ms`.padEnd(8);
    const p50 = `${calcPercentile(durs, 50)}ms`.padEnd(8);
    const p95 = `${calcPercentile(durs, 95)}ms`.padEnd(8);
    const p99 = `${calcPercentile(durs, 99)}ms`.padEnd(8);
    console.log(`${ep.padEnd(28)} | ${cnt} | ${avg} | ${p50} | ${p95} | ${p99}`);
  }

  if (peakStats) {
    console.log(`\n${bold('Container Resource Consumption Under Peak 50-User Load:')}`);
    console.log(`  • messaging-backend:  CPU ${bold(peakStats['messaging-backend']?.cpu || 'N/A')}, Memory ${bold(peakStats['messaging-backend']?.mem || 'N/A')}`);
    console.log(`  • messaging-postgres: CPU ${bold(peakStats['messaging-postgres']?.cpu || 'N/A')}, Memory ${bold(peakStats['messaging-postgres']?.mem || 'N/A')}`);
  }
  console.log(bold(cyan('========================================================================\n')));
}

runBenchmark().catch((err) => {
  console.error(red(`\nLoad test failed: ${err.message}`));
  console.error(err.stack);
  process.exit(1);
});
