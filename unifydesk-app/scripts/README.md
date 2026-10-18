# UnifyDesk High-Scale Test Dataset Simulation

This directory contains high-performance simulation tools to populate UnifyDesk with realistic enterprise multi-tenant test data at scale.

## Overview of Simulated Data

For **100 companies** (configurable), the simulation creates:
- **5 Users per Company** (500 users total):
  - 1 Owner (`OWNER` role)
  - 1 Manager (`MANAGER` role)
  - 3 Agents (`MEMBER` role)
  - All users have verified email, active status, and password **`Password@123`**.
- **6 Channels per Company** (600 channels total):
  - **3 Email Channels**: Customer Support, Sales Inquiries, Billing Desk (IMAP/SMTP with sender identities).
  - **3 WhatsApp Channels**: Support WhatsApp, Sales WhatsApp, Alerts WhatsApp (Meta WhatsApp Business connections).
- **1,000 Messages per Channel** (6,000 messages per company, **600,000 messages total**):
  - Distributed across 20 realistic conversation threads per channel (120 conversations per company).
  - Alternating INBOUND (customer) and OUTBOUND (agent) messages with realistic dialogue snippets and timestamps spanning the last 30 days.
  - Linked to participants and contact profiles via `inbox_participant_contact_links`.
- **10,000 to 50,000 Contacts per Company** (Default: 10,000 = **1,000,000 contacts total**):
  - Full contact profiles with names, titles, organizations, timezone, source, and owner assignments.
  - Dual identities: 1 verified primary Email identity and 1 verified primary Phone identity per contact (2,000,000 identities).
- **CRM Tasks & Activities** (50 tasks per company, **5,000 tasks total**):
  - Statuses across `OPEN`, `IN_PROGRESS`, `DONE`, `CANCELLED` matching task board columns.
  - Priorities (`HIGH`, `NORMAL`, `LOW`), due dates (upcoming and overdue), assignees, and deal linkages.
- **CRM Deals & Accounts** (30 deals and 20 customer accounts per company):
  - Multi-stage pipeline (`NEW`, `QUALIFIED`, `PROPOSAL`, `NEGOTIATION`, `WON`, `LOST`) with commercial amounts (₹50,000 – ₹25,00,000).
- **Billing & Wallets**:
  - Every company has an **ACTIVE Enterprise Subscription** and **₹25,000 INR Wallet Balance** with automated top-up rules.

---

## Quick Start Commands

### Option A: Using Node.js CLI (Recommended)

From `unifydesk-app/`:

```bash
# Run simulation for 100 companies, 10,000 contacts each, 1,000 msgs per channel:
node scripts/simulate.js --companies 100 --contacts 10000 --messages 1000

# Check current count of simulated records in database:
node scripts/simulate.js --status

# Quick test with 5 companies (1,000 contacts, 500 messages):
node scripts/simulate.js --companies 5 --contacts 1000 --messages 500

# Clean up all simulated test data (leaves real production/admin accounts untouched):
node scripts/simulate.js --cleanup
```

### Option B: Using PowerShell

From `unifydesk-app/`:

```powershell
# Run full 100-company simulation:
.\scripts\simulate.ps1 -Companies 100 -Contacts 10000 -Messages 1000

# Check database status:
.\scripts\simulate.ps1 -Status

# Cleanup all simulated data:
.\scripts\simulate.ps1 -Cleanup
```

### Option C: Direct SQL (inside PostgreSQL container)

```sql
-- Generate 100 companies with 10k contacts and 1k msgs per channel:
CALL simulate_test_dataset(100, 10000, 1000, 50, 30);

-- Clean up simulated data:
CALL cleanup_simulated_test_data();
```

---

## Default User Login Credentials

All generated test users use the password: **`Password@123`**

Examples:
| Company | Role | Email | Password |
|---|---|---|---|
| Apex Cloud Innovations | Owner | `owner.sim1@sim.unifydesk.in` | `Password@123` |
| Apex Cloud Innovations | Manager | `mgr.sim1@sim.unifydesk.in` | `Password@123` |
| Apex Cloud Innovations | Senior Agent | `agt1.sim1@sim.unifydesk.in` | `Password@123` |
| Nexus Data Labs | Owner | `owner.sim2@sim.unifydesk.in` | `Password@123` |
| Zenith Health Sciences | Owner | `owner.sim21@sim.unifydesk.in` | `Password@123` |

Super Admin can also log in as `renukaprasadms@unifydesk.in` to view and manage all 100 companies simultaneously.
