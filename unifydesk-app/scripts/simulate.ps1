<#
.SYNOPSIS
    Simulates realistic enterprise test companies in UnifyDesk.

.DESCRIPTION
    Simulates at least 100 companies with 5 users each, 3 Email + 3 WhatsApp channels,
    1,000 messages per channel, 10,000 to 50,000 contacts each, and CRM tasks & deals.

.PARAMETER Companies
    Number of companies to generate (default: 100).

.PARAMETER Contacts
    Number of contacts per company (default: 10000, supports 10000 to 50000).

.PARAMETER Messages
    Messages per channel (default: 1000).

.PARAMETER Tasks
    CRM tasks per company (default: 50).

.PARAMETER Deals
    CRM deals per company (default: 30).

.PARAMETER Status
    Displays current simulated data counts in the database.

.PARAMETER Cleanup
    Removes all simulated companies, users, and artifacts.

.EXAMPLE
    .\scripts\simulate.ps1 -Companies 100 -Contacts 10000 -Messages 1000
    .\scripts\simulate.ps1 -Status
    .\scripts\simulate.ps1 -Cleanup
#>

param(
    [int]$Companies = 100,
    [int]$Contacts = 10000,
    [int]$Messages = 1000,
    [int]$Tasks = 50,
    [int]$Deals = 30,
    [switch]$Status,
    [switch]$Cleanup
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# Ensure database procedure is loaded
Write-Host "Checking / updating simulation SQL procedures..." -ForegroundColor Cyan
Get-Content "$scriptDir\simulate_companies_dataset.sql" | docker exec -i messaging-postgres psql -U unifydesk -d unifydesk -p 5002

if ($Cleanup) {
    Write-Host "`nCleaning up simulated test data..." -ForegroundColor Yellow
    docker exec -i messaging-postgres psql -U unifydesk -d unifydesk -p 5002 -c "CALL cleanup_simulated_test_data();"
    Write-Host "`nCleanup completed successfully." -ForegroundColor Green
    exit 0
}

if ($Status) {
    Write-Host "`nQuerying simulated dataset status..." -ForegroundColor Cyan
    $statusQuery = @"
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
"@
    docker exec -i messaging-postgres psql -U unifydesk -d unifydesk -p 5002 -c $statusQuery
    exit 0
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "      UNIFYDESK ENTERPRISE DATASET SIMULATOR                " -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "Simulation Parameters:" -ForegroundColor White
Write-Host "  * Companies:           $Companies" -ForegroundColor Yellow
Write-Host "  * Users per Company:   5 (Total $($Companies * 5))" -ForegroundColor Yellow
Write-Host "  * Channels / Company:  6 (3 Email + 3 WhatsApp, Total $($Companies * 6))" -ForegroundColor Yellow
Write-Host "  * Messages / Channel:  $Messages (Total $($Companies * 6 * $Messages))" -ForegroundColor Yellow
Write-Host "  * Contacts / Company:  $Contacts (Total $($Companies * $Contacts))" -ForegroundColor Yellow
Write-Host "  * CRM Tasks / Company: $Tasks (Total $($Companies * $Tasks))" -ForegroundColor Yellow
Write-Host "  * CRM Deals / Company: $Deals (Total $($Companies * $Deals))" -ForegroundColor Yellow
Write-Host "============================================================`n" -ForegroundColor Cyan

$startTime = Get-Date

docker exec -i messaging-postgres psql -U unifydesk -d unifydesk -p 5002 -c "CALL simulate_test_dataset(p_companies_count := $Companies, p_contacts_per_company := $Contacts, p_messages_per_channel := $Messages, p_tasks_per_company := $Tasks, p_deals_per_company := $Deals);"

$elapsed = ((Get-Date) - $startTime).TotalSeconds
Write-Host "`nSimulation completed in $([math]::Round($elapsed, 1)) seconds!" -ForegroundColor Green

Write-Host "`nFinal Dataset Status:" -ForegroundColor Cyan
$statusQuery = @"
SELECT 'Companies' AS Entity, count(*)::text AS Count FROM companies WHERE slug LIKE 'sim-%'
UNION ALL
SELECT 'Users', count(*)::text FROM users WHERE email LIKE '%@sim.unifydesk.in'
UNION ALL
SELECT 'Channels', count(*)::text FROM channel_connections WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%')
UNION ALL
SELECT 'Contacts', count(*)::text FROM contacts WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%')
UNION ALL
SELECT 'Inbox Messages', count(*)::text FROM inbox_messages WHERE connection_id IN (SELECT id FROM channel_connections WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%'))
UNION ALL
SELECT 'CRM Tasks', count(*)::text FROM crm_tasks WHERE company_id IN (SELECT id FROM companies WHERE slug LIKE 'sim-%');
"@
docker exec -i messaging-postgres psql -U unifydesk -d unifydesk -p 5002 -c $statusQuery
