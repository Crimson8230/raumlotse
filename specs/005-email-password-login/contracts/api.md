# Authentication API Contract

Planned interface; not yet implemented. Browser and API share one origin. JSON request/response bodies except where noted. Auth and error responses use `Cache-Control: no-store`. All runtime diagnostics exclude personal data and credentials.

## Shared schemas

AuthenticatedUser: `{ "userId": "UUID string", "displayName": "plain text" }`. These are the only identity fields returned; never password/hash or authoritative role claims.

Problem retains existing fields `title: string`, `status: integer`, `detail: string`, `errors: [{field: string, message: string}]`, and adds optional `code: string` and `retryAfterSeconds: integer`. Authentication error responses always include a code and errors array; cooldown metadata appears only as specified below. Codes/messages are fixed, not derived from exceptions or submitted values.

Example credential rejection:

```json
{"title":"Unauthorized","status":401,"detail":"Email address or password is incorrect.","errors":[],"code":"INVALID_CREDENTIALS"}
```

## GET /api/auth/csrf

Public; creates an anonymous session if needed. 200: `{ "headerName": "X-CSRF-TOKEN", "token": "opaque masked token" }`. Return the resolved request token, not the raw session secret. Client sends token unchanged in the named header for POST/PUT/PATCH/DELETE. No permissive cross-origin reads. The pre-login session cookie does not grant authentication.

## POST /api/auth/login

Public but requires a valid session-bound CSRF header. Content-Type application/json. Request has exactly the credential fields `{ "email": "user@example.test", "password": "exact user input" }`; never accept userId or roles as authentication authority. Unknown extra fields are rejected with 400.

- Email: canonicalize surrounding whitespace/case as described in data-model.md, then required, valid format, maximum 254 characters.
- Password: required string, 1-1024 UTF-16 code units, no trimming/normalization; whitespace-only values remain possible if provisioned.
- Validate with Bean Validation; malformed/non-object JSON or wrong field types is 400; unsupported content type is 415. Error responses never include rejected values.

| Status | Code | Behavior |
|---|---|---|
| 200 | none | AuthenticatedUser; rotates session id and saves context; invalidates pre-login CSRF token |
| 400 | VALIDATION_FAILED | Field corrections or safe malformed-request explanation; no authentication |
| 401 | INVALID_CREDENTIALS | Identical generic body for wrong password and unknown well-formed email; fifth failure adds cooldown metadata |
| 403 | CSRF_INVALID | Missing/invalid CSRF; credentials are not processed |
| 409 | ALREADY_AUTHENTICATED | Existing authenticated account unchanged; no account-switching flow |
| 415 | UNSUPPORTED_MEDIA_TYPE | Safe fixed error; no authentication |
| 429 | LOGIN_COOLDOWN | Active per-email cooldown; generic wait message plus retryAfterSeconds and Retry-After |
| 503 | AUTH_UNAVAILABLE | Account store/provider/limiter unavailable, lock timeout, unusable stored credential or failed session preparation/commit/publication; fixed retry message; no failure added for the outage (see commit uncertainty below) |

Success response sets/rotates JSESSIONID, HttpOnly, SameSite=Lax, Path=/, Secure outside local HTTP. No token in JSON, URL, localStorage or sessionStorage. No remember-me expiry. Session inactivity limit is 30 minutes. Login does not change account roles. No HTTP redirect from this JSON API; frontend navigation supplies the redirect to /.

After 200 the frontend must obtain a fresh CSRF token. Old session id must not authenticate, and old CSRF token must not authorize a mutation with the new session. A fresh token with the new cookie permits otherwise authorized writes. Existing valid session POST login receives 409 only after CSRF validation; frontend refreshes /me rather than assuming the submitted account succeeded.

## Login cooldown semantics

Only CSRF-valid, well-formed anonymous attempts that actually fail credential verification count. Email canonicalization is identical to lookup; registered/unknown email variants share their own respective histories across browsers and devices. Malformed input, CSRF rejection, already-authenticated login and outages do not increment counters.

The first four mismatches return generic 401. The fifth within the rolling 15-minute window returns the same 401 code/detail and starts a fixed 15-minute cooldown, adding `retryAfterSeconds: 900` and `Retry-After: 900`. Example fifth-failure body:

```json
{"title":"Unauthorized","status":401,"detail":"Email address or password is incorrect.","errors":[],"code":"INVALID_CREDENTIALS","retryAfterSeconds":900}
```

Further well-formed, CSRF-valid login attempts for that email, even with the correct password, return 429 without password verification or deadline extension. Fixed message: `Too many failed login attempts. Please try again after the indicated wait.` The body is Problem with title `Too Many Requests`, status 429, code `LOGIN_COOLDOWN`, errors [] and retryAfterSeconds. Send `Retry-After` with the same positive integer (ceiling of remaining seconds, 1-900) and no-store. No email, account existence, counter or key is returned. Identical policies/messages apply to unknown emails.

After the deadline, clear the triggering history and allow a fresh attempt automatically. Successful eligible login clears prior failures. Other email identities and existing authenticated sessions are unaffected. Database state and server time are authoritative; session/cookie changes, browser reload and backend restart cannot reset the cooldown. Invalid requests may still receive 400/403/415 before cooldown lookup; they never authenticate or change the deadline. Requests already authenticated receive 409 and leave history/session untouched.

Do not create/save a usable authenticated session on a blocked outcome or failed limiter transaction. Keep authenticated context unpublished until database commit is confirmed; concurrent requests may not use a prepared session. If commit acknowledgement is lost, history may already be durable even though authentication is denied with 503. Never replay the attempt or add a failure for the outage. If postcommit context publication fails, the committed history reset remains. Detailed compensation behavior is specified in data-model.md. Commit successful failure-count changes before sending a credential error; fail closed if state cannot be saved. A successful login committed before another attempt later triggers a cooldown remains admitted.

## GET /api/auth/me

Requires authentication. 200: AuthenticatedUser for the current existing account. 401 `AUTH_REQUIRED` for anonymous, invalid/expired session or missing account; clear invalid context. 503 `AUTH_UNAVAILABLE` for account lookup outage. No roles are cached as authorization claims. Used at app initialization/reload and explicit recovery, not idle keepalive polling.

## Business APIs and error precedence

Only GET /api/health, GET /api/auth/csrf and POST /api/auth/login are public API operations. Protect all other application APIs by default, including existing rooms, buildings, floors and equipment-types and future handlers. Keep GET /api/health's existing body unchanged. Disable generated login/logout/basic-auth endpoints. Error dispatch may run to render sanitized errors but must not become a bypass to business handlers.

| Request | Result |
|---|---|
| Anonymous GET of business resource | 401 AUTH_REQUIRED, no protected content |
| Anonymous unsafe business request, with or without CSRF | 401 AUTH_REQUIRED, no action; CSRF denial handler must preserve this policy |
| Authenticated unsafe request with missing/invalid CSRF | 403 CSRF_INVALID before controller execution |
| Authenticated request lacking an installed role permission | 403 FORBIDDEN, no data/change |
| Authenticated otherwise-permitted request | Existing business contract, with CSRF required on unsafe methods |
| Account lookup unavailable | 503 AUTH_UNAVAILABLE, no protected access |

Do not use HTML redirects or WWW-Authenticate Basic challenges for API denials. Spring filter-layer entry/denial handlers and MVC exception handlers share Problem serialization. Authentication-specific failures must not flow into existing raw database exception logging. GET operations must remain read-only.

Place verified-account existence checking after security-context loading and before CSRF validation for requests carrying an authenticated principal. A missing account clears/invalidate its authentication before the CSRF denial handler classifies it; a lookup outage returns sanitized 503. The CSRF denial handler returns 403 for public login, 401 for anonymous protected business requests, and 403 for valid authenticated business requests. Test anonymous writes without tokens and deleted-account writes with stale tokens explicitly. Do not rely on Spring's default CSRF-before-authorization response to satisfy this contract.

## Client compatibility / deployment migration

Existing room/catalog response bodies and paths remain unchanged. Their formerly public access is deliberately removed. All callers must first get CSRF, log in, obtain fresh CSRF, and retain the session cookie; mutations send the header. Deploy updated frontend/client and backend together; update existing tests and any scripts before rollout. Keep /api/health public for probes. Never temporarily whitelist business endpoints to preserve old clients.

Frontend uses same-origin fetch credentials, a shared in-memory CSRF token and a protected-request 401 notification. 401 from login is a local credential error, not a global redirect. 403 never automatically logs users out or replays a mutation: refresh token and ask for explicit retry for CSRF_INVALID; FORBIDDEN remains a permission message. 503/network errors are retryable availability failures. Authentication responses and browser error text never include passwords.

The shared client exposes retryAfterSeconds and Retry-After to the login page for the fifth 401 and active-cooldown 429. It does not automatically retry either response, emit a global authentication-expiry event for them, or apply that email's wait to unrelated emails. Existing authenticated business calls remain unaffected by login cooldowns.
