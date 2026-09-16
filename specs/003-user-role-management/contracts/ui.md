# Role Management UI Contract

Routes: /admin/users and /admin/users/:userId/roles. Admin-only navigation entry using existing styling. Client guards aid navigation; backend remains authoritative.

List: search name/account label, paginated results (25 default), loading, empty and retryable failure states. Display both name and unique account label.

Editor: identity and five native labeled checkboxes using the exact role labels; Save and Cancel. Shared typed validation schema/function checks 1-5 unique supported codes before submission; backend validation remains authoritative. No new library required for the fixed shape.

Disable Save before load, during save, and for invalid/empty selection; explain empty selection visibly. Cancel discards draft and returns to list. Success displays confirmation and committed roles/version. Failure preserves draft; uncertain commit status requires refetch before retry.

STALE_ROLES: explain another Admin changed the roles; stop saves and show Review latest roles. Explicit review reloads persisted selection/version and replaces the draft. Admin then selects and saves again; never merge or auto-resubmit. Repeat conflict handling if another change intervenes.

LAST_ADMIN_REQUIRED: preserve draft, explain safeguard and allow correction.
USER_NOT_FOUND: explain and return to list.
ADMIN_REQUIRED: remove editing controls and show access denied.
AUTHENTICATION_REQUIRED: use prerequisite sign-in flow.
After successful self-demotion, refresh current membership and leave role management.

Accessibility: keyboard navigation for all controls, fieldset/legend around roles, associated validation text, announced loading/save/error states, visible focus, focus on conflict explanation. Reuse responsive layout and current application language conventions; no localization system added.

Component tests: save, cancel, empty selection, error, conflict-review-retry, access denial and self-demotion. Manual checks: keyboard, small viewport and two-minute journey.

