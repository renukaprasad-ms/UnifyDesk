# CRM Custom Fields — Implementation

Branch context: follows the CRM R1/R2 workspace (dev baseline).

## What was added

### Database — V116__crm_custom_fields.sql

- `crm_custom_field_defs`: tenant-scoped field schema table.
  - `entity_type`: `LEAD | DEAL | ACCOUNT`
  - `field_type`: `TEXT | NUMBER | DATE | BOOLEAN | DROPDOWN | MULTI_SELECT | URL`
  - `field_key`: slug key, `^[a-z][a-z0-9_]{0,62}$`, unique per company+entity.
  - `options`: JSONB array, required for DROPDOWN/MULTI_SELECT, forbidden otherwise (enforced by check constraint).
  - Max 50 fields per entity type per workspace (enforced in service).
- `custom_fields JSONB` column added (with `DEFAULT '{}'`) to `crm_leads`, `crm_deals`, `crm_accounts`.
- GIN indexes on `custom_fields` for future `@>` containment queries.

### Backend

| File | Purpose |
|---|---|
| `CrmCustomFieldService.java` | CRUD for definitions + `validateValues()` called at write time from other services |
| `CrmCustomFieldController.java` | `GET/POST /crm/custom-fields`, `PATCH/DELETE /crm/custom-fields/{id}` |
| `CompanyPermissionKeys.java` | `crm.field.read`, `crm.field.modify` added; Manager defaults both; Member defaults read-only |

### Frontend

| File | Purpose |
|---|---|
| `crmCustomFieldsApi.ts` | Typed API client |
| `CustomFieldRenderer.tsx` | Renders any field type in a form; `CustomFieldsSection` renders all defs for an entity |
| `CrmCustomFieldsSettingsPage.tsx` | Admin UI at `/crm/settings/custom-fields` — tabbed by entity, table view, create dialog with option builder |
| `CrmSidebar.tsx` | "Custom Fields" link added (visible with `crm.field.read`) |
| `AppRoutes.tsx` | Route `crm/settings/custom-fields` wired |
| `permissions.ts` | `CRM_FIELD_READ`, `CRM_FIELD_MODIFY` added |

## How to integrate custom field values into existing forms

When a form creates/updates a Lead or Deal, fetch the field defs and include `customFields` in the request body:

```ts
// In CrmDealsPage (or CrmLeadsPage) — before mounting the form:
const [fieldDefs, setFieldDefs] = useState<CrmFieldDef[]>([])
useEffect(() => {
  void crmCustomFieldsApi.list(companyId, 'DEAL').then(setFieldDefs)
}, [companyId])

// In the form state:
const [customFields, setCustomFields] = useState<CustomFieldValues>({})

// In the form JSX, after the standard fields:
<CustomFieldsSection
  defs={fieldDefs}
  values={customFields}
  onChange={(key, val) => setCustomFields(prev => ({ ...prev, [key]: val }))}
/>

// In the submit payload, add: customFields
```

The backend `CrmCustomFieldService.validateValues()` should be called from `CrmDealsService.validate()` and `LeadService.enroll()`/`move()` before writing to the entity row.

## Full Stack Integration Status

Custom fields are fully implemented, validated, and integrated across all three supported CRM entities:

| Entity | Settings & Definitions | Creation & Edit Forms | Details & Drawer View | Table / Board Cards | Backend Validation & JSONB |
|---|---|---|---|---|---|
| **Leads** | `/crm/settings/custom-fields` (LEAD tab) | Create Lead Dialog (`CrmLeadsPage`) | `LeadHistoryPanel` (Inline Edit & Display) | Lead Table Rows | `LeadService`, `LeadController` (`PATCH /custom-fields`) |
| **Deals** | `/crm/settings/custom-fields` (DEAL tab) | New & Edit Deal Dialogs (`CrmDealsPage`) | `DealDetails` Drawer | Kanban Cards & Table View | `CrmDealsService`, `CrmSalesController` |
| **Companies / Accounts** | `/crm/settings/custom-fields` (ACCOUNT tab) | New Company Modal (`CrmCompaniesPage`) | Company Profile Page (`CrmCompanyDetailPage`) | Company List Rows | `CrmWorkService`, `CrmWorkController` |

### Architecture Highlights
- **Schema & Storage**: Per-workspace field schemas stored in `crm_custom_field_defs`; entity values persisted as typed JSONB on `crm_leads`, `crm_deals`, and `crm_accounts` with GIN indices.
- **Validation**: `CrmCustomFieldService.validateValues()` runs at write time across all entities, enforcing required fields, types (TEXT, NUMBER, DATE, BOOLEAN, DROPDOWN, MULTI_SELECT, URL), and option sets.
- **Backward Compatibility**: Explicit Spring Boot `@Autowired` constructors and overloaded domain record constructors ensure 100% compatibility with test suites and existing API consumers.
