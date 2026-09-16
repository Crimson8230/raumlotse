# Authentication Prerequisite Handoff

Authentication, account creation and initial Admin provisioning are separate work, per clarification. These capabilities are required without choosing a sign-in protocol here.

## Capabilities required

- Resolve verified stable account identity per request; absence -> 401. Browser-supplied id/roles never establish identity.
- Account directory: opaque id, displayName, unique accountLabel, existence lookup, paginated substring search. API serializes ids as strings; storage uses native identity keys.
- Frontend authenticated identity and sign-in/expired-session flow. Navigation derives from current roles.
- Secure credential transport. For cookie sessions, use prerequisite CSRF tokens and same-origin policy; for other transport, use its documented protections. Never disable protection for role tests.
- Transactional account creation hook writes Viewer and version 0 before account visibility. Registration cannot request elevated roles.
- Secure initial Admin provisioning/recovery with no public promotion endpoint or committed secret. Coordinate through the shared mutation guard.
- Coordinate supported deletion/deactivation with last-Admin preservation and common lock order. No lifecycle endpoint is added here.
- Permit per-request persisted role checks; sign-in-time role claims are not authoritative after updates.

## Ownership and release gate

Identity owns account storage, sign-in/out, credentials/session verification, registration and provisioning. Role management owns role vocabulary, assignments, versioning, authorization and editor. A small adapter maps identity to this contract; do not add a second authentication framework.

Before implementing the adapter, verify the delivered prerequisite and record its concrete module/key mapping in the implementation PR. Before release, prove these capabilities with real authenticated accounts, expired credentials and request-forgery rejection where applicable.

Missing prerequisite capability blocks integration/release, not planning. Isolated tests may supply verified principal fixtures; production must never use mock identity or implicit Admin.

