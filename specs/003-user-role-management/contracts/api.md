# Role Management API Contract

Base /api. JSON bodies. New endpoints only; existing room/catalog APIs unchanged.

Every endpoint requires authenticated current ADMIN membership from persistence. Missing identity returns 401, non-Admin returns 403 before target existence is disclosed. Actor identity/authority never comes from request bodies. Credential transport and CSRF follow authentication.md. Responses: Cache-Control: no-store.

## Shared shapes

- RoleCode: ADMIN | UNIVERSITY_STAFF | STUDENT | LECTURER | VIEWER.
- RoleOption: {code: RoleCode, label: string}.
- UserSummary: {id: string, displayName: string, accountLabel: string}.
- UserRoles: {user: UserSummary, roles: RoleCode[], version: string, availableRoles: RoleOption[]}.
- accountLabel: unique human-readable identity-directory identifier; never a credential.
- version: nonnegative bigint serialized as canonical decimal text; clients treat it as opaque.
- roles: unique, returned in the RoleCode order above. availableRoles: all five code/label pairs.

## GET /admin/users

Query: q optional trimmed string, maximum 100 characters; page integer >=0 default 0; size integer 1-100 default 25.
Search: case-insensitive literal substring over displayName/accountLabel; blank means all. Escape wildcard characters in parameterized queries.
Order: displayName, then stable id; no client-defined sort expression.
200: {items: UserSummary[], page: number, size: number, totalElements: number}.
Empty/out-of-range page -> empty items and accurate metadata.
400 invalid parameters; 401/403 authorization; 503 unavailable dependency/storage.

## GET /admin/users/{userId}/roles

200: UserRoles. 404 unknown user; 401/403 authorization; 503 unavailable storage.
userId is an opaque path-encoded identifier, not a numeric role key.

## PUT /admin/users/{userId}/roles

Request: {roles: RoleCode[], expectedVersion: string}.
Required fields; roles contains 1-5 unique supported values. Nulls, duplicates, unknown codes and invalid/missing versions -> 400. Full replacement, not patch.
200: committed UserRoles; current unchanged set returns unchanged version.
404 absent target; 409 stale version or last-Admin removal; 401/403 authorization; 503 lock timeout/unavailability.
No mutation on non-200 response. Unexpected errors -> sanitized 500. Lost responses require refetching before retry, not blind replay.
Authorization is rechecked after obtaining the guard. Stale version comparison precedes no-op handling.

Example:
```json
{"roles":["STUDENT","LECTURER"],"expectedVersion":"4"}
```

If changed, result includes roles ["STUDENT","LECTURER"] and version "5", plus user identity and the full role catalog.

## Errors

Preserve Problem fields: {title: string, status: number, detail: string, errors: [{field: string, message: string}]}.
Role responses add code: string; existing consumers remain compatible.

| Status | code | Client action |
|---|---|---|
| 400 | INVALID_ROLE_SELECTION | Correct empty, duplicate or unsupported selection |
| 400 | INVALID_REQUEST | Correct JSON, version or query |
| 401 | AUTHENTICATION_REQUIRED | Use prerequisite sign-in flow |
| 403 | ADMIN_REQUIRED | Remove editing access |
| 404 | USER_NOT_FOUND | Return to list |
| 409 | STALE_ROLES | Explicitly review latest roles before retry |
| 409 | LAST_ADMIN_REQUIRED | Keep an Admin assigned; explain safeguard |
| 503 | ROLE_MANAGEMENT_UNAVAILABLE | Explain failure; refetch before user-initiated retry |
| 500 | INTERNAL_ERROR | Generic error without internals |

Errors/logs contain no submitted personal data, account identifiers, SQL or stack traces. Validation errors identify only safe field names and fixed messages.

