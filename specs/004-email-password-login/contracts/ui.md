# Login UI Contract

## Routes and state

- Public /login sits outside application navigation. Protected shell contains /, /locations, /rooms, /rooms/new and /rooms/:roomId, plus future business pages. Navigation is inside that shell.
- On app load, resolve GET /api/auth/me and then obtain CSRF for a valid identity before publishing authenticated readiness. Loading renders a neutral accessible status, never private content. Authenticated mounts the shell. Anonymous uses replace navigation to /login. Unavailable shows a retry state without mounting the shell.
- Authenticated users visiting /login go to /. Successful login always replaces /login with /; no return URL parameter or external destination is accepted.
- Protected-request 401 clears identity and mounted private data, then replaces location with /login. Do not leave cached room/form contents visible. A 403 shows the appropriate CSRF/permission message; 503/network errors show a retry state rather than invalid-credential feedback.

## Form

Use a heading, associated Email address and Password labels, type=email with autocomplete=username, and type=password with autocomplete=current-password. Never change password type or supply a reveal control. Support keyboard submission, visible focus and password-manager autofill. Submit button reads Log in and is disabled while a submission is pending or a known cooldown is active for the currently entered canonical email; announce pending/error status. No public registration, reset-password or logout flow is added.

Zod schema validates strings, canonical email and required/format/length constraints from the API contract. Password validation checks length only and does not trim. Field errors use aria-invalid and associated descriptions; generic failures use an announced error summary. Focus first invalid field, or the generic error summary after server rejection. Preserve email after failure, clear password after an attempted server login, and never copy it into diagnostics or browser persistence. Client-side correction errors need not clear unsubmitted input.

## Submission lifecycle

1. Validate form; invalid values do not submit.
2. Obtain CSRF token if none is loaded, then submit credentials once. No duplicate submission while pending.
3. 200: enter finishing-login state with the returned identity, clear password, and fetch replacement CSRF. Only after refresh succeeds publish authenticated readiness and navigate to /. If refresh fails, remain outside the protected shell, show a recoverable setup error and retry token acquisition without resubmitting credentials. Preserve the already-created server session. The authenticated-visit redirect must not run in finishing-login state.
4. 400: show safe field corrections. 401: Email address or password is incorrect. Both unknown-email and wrong-password cases use identical text. If the fifth 401 includes retryAfterSeconds, also enter cooldown feedback. 429 LOGIN_COOLDOWN shows the same generic wait feedback for registered and unknown emails. 403: refresh CSRF and ask for explicit retry. 409: refresh /me and navigate for the already-authenticated identity. 503/network: Login is temporarily unavailable. Please try again.
5. Expiry during an operation clears rendered private state after the resulting 401. No automatic replay of mutations after reauthentication.

Reuse existing visual styles and test tooling. Browser acceptance must verify typing, paste and autofill remain masked, reload keeps valid authentication, expired sessions return to login, and browser history does not mount protected content without a new identity check.

## Cooldown feedback and retry

On the triggering 401 or a 429, retain email, clear password and show a generic explanation plus the remaining wait from retryAfterSeconds (fallback to Retry-After if needed). Store a deadline only in page memory, associated with that canonical email. The existing AuthProvider remains anonymous; this is form feedback, not a new authenticated state. No submitted email, password or token is persisted in browser storage.

Use a locally updated countdown; no background login requests or polling. Announce the initial cooldown and when retry becomes available without announcing every second. Browser focus/visibility changes recompute remaining time; a delayed timer must not add another 15 minutes. Equivalent email case/whitespace variants keep the same wait. Switching to a different canonical email allows its own login; it does not erase server history for the blocked email.

When the display reaches zero, enable an explicit login attempt; never resubmit the password automatically. The server may return an updated wait, which the UI must honor. Reload or a second browser may not know the deadline until submission, but the server still rejects the attempt. Invalid/missing retry metadata uses a generic wait message and permits only a user-initiated retry; never invent a new server deadline.

A cooldown does not redirect or sign out another authenticated browser. No administrator-unlock link, reset-password detour or registration flow is added. Component tests use fake timers for countdown and canonical-email changes; integration tests verify actual enforcement rather than trusting the disabled button.