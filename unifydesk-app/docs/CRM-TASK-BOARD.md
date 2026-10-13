# CRM task board

Tasks open in a Kanban board. Drag a card between steps, or use its step selector with a keyboard or touch device. Open a card to see its details and history; Edit switches to the list editor. Both views share status and assignee filters. Counts show loaded tasks; use Load more to include older tasks.

Workspace owners and managers can use **Manage steps** to create, rename and reorder up to 20 steps. Custom steps can be removed when empty; the four default steps can be renamed and reordered. Custom roles require `crm.task.workflow.modify`. Moving cards requires `crm.task.modify`.

Each step belongs to To do, In progress, Done or Cancelled. This group stays fixed after creation, keeping overdue reminders, completion times and reports consistent. For example, Review and Waiting for customer may both belong to In progress. Moving between them still records the actor, timestamp and previous/new step names. Renaming a step preserves names recorded in historical events.

Migration V114 assigns existing tasks to their current status step without removing data. Version checks reject stale task moves and step configuration. All steps and task associations are scoped to the workspace.

From the app directory, rebuild using `./local.ps1 up local` or `./local.ps1 up tunnel`. Backend startup applies the migration automatically.

Creation and editing now use focused dialogs for Tasks, Deals, Leads, Companies, Lists and activity notes. Step management also opens in a dialog. CRM filters are available from a filter button with an applied-filter count and clear action.

The task board fills the available viewport height, with independently scrolling columns. Task and deal moves update the board immediately, save in the background, and reconcile the returned version without refetching the entire board. Only the affected card shows Saving and blocks another move until the request settles. A failed move restores its previous position and displays an error; other cards can continue saving independently. When an acknowledged move leaves the selected status/stage filter, its card is removed from those filtered results.

Deals now uses the same viewport-height columns and shared Board/List toolbar as Tasks. Both boards expose assignment and activity history directly on their cards; edits use dialogs. Contacts uses compact search with remaining filters in its dropdown. CRM tables, boards, overview metrics, history, relationships, companies, lists, segments, reports, import details and settings use skeletons while their data loads. Pagination keeps existing records visible and adds placeholder rows during fetching.
