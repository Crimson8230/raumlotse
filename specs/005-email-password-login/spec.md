# Feature Specification: Email and Password Login

**Feature Branch**: `005-email-password-login`

**Created**: 2026-09-20

**Status**: Draft

**Input**: User description: "As a user I want to log in using my email address and password so that I can securely access the system. The login page provides email address and password fields. Registered users can log in with valid credentials. Incorrect credentials cause login to fail with an appropriate error message. Successful login redirects to the authenticated area. Password input is masked and never displayed in plain text. Unauthenticated users cannot access protected areas."

## Clarifications

### Session 2026-09-20

- Q: What should happen after repeated failed login attempts? → A: After 5 failed attempts for the same email within 15 minutes, block further attempts for that email for 15 minutes. Apply equally to registered and unknown emails.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Log in with valid credentials (Priority: P1)

As a registered user, I want to enter my email address and password and reach the authenticated area so I can use Raumlotse securely.

**Why this priority**: This is the primary route into the application for registered users.

**Independent Test**: Use an existing account with known credentials to log in, verify the displayed inputs and masking, and confirm access to the authenticated landing area.

**Acceptance Scenarios**:

1. **Given** an unauthenticated user, **When** they open the login page, **Then** labeled email address and password fields and a login submission control are available.
2. **Given** the login page, **When** a user types, pastes, or autofills a password, **Then** its characters remain masked and no control displays the password in plain text.
3. **Given** a registered account with no active login cooldown, **When** its matching email address and password are submitted, **Then** login succeeds for that account and the user is redirected to the authenticated landing area.
4. **Given** a successful login, **When** the user navigates between permitted protected pages or reloads one while their authentication remains valid, **Then** they remain recognized as the same user without signing in again.

---

### User Story 2 - Understand and recover from a failed login (Priority: P1)

As a user, I want understandable feedback when login fails so I can correct my input and try again.

**Why this priority**: Incorrect credentials must never grant access, and users need a clear recovery path.

**Independent Test**: Submit unknown emails, incorrect passwords, and incomplete entries; verify rejection and feedback, then correct the credentials and successfully retry.

**Acceptance Scenarios**:

1. **Given** a registered email address, **When** an incorrect password is submitted, **Then** login fails, the user stays on the login page, and a message such as "Email address or password is incorrect" is displayed.
2. **Given** an unregistered email address, **When** credentials are submitted, **Then** login fails with the same message as an incorrect password, without revealing whether an account exists.
3. **Given** either input is empty or the email address has an invalid format, **When** login is submitted, **Then** login does not succeed and feedback identifies the required correction.
4. **Given** a failed login and no active cooldown, **When** the user corrects the credentials and submits again, **Then** valid credentials allow a successful login and redirect.
5. **Given** login cannot be completed because the service is unavailable, **When** the user submits credentials, **Then** access is not granted and a message explains that login is temporarily unavailable and can be retried.
6. **Given** four incorrect-credential attempts for the same email in the preceding 15 minutes, **When** a fifth attempt fails, **Then** a 15-minute cooldown begins and subsequent attempts for that email are blocked, including attempts using the correct password.
7. **Given** an active cooldown for either a registered or unknown email, **When** another login is attempted from any browser or device, **Then** access is denied with the same temporary-cooldown message and remaining wait time, without revealing whether the account exists or extending the cooldown.
8. **Given** the cooldown has expired, **When** the registered user submits valid credentials, **Then** login succeeds without administrator intervention and the previous cooldown's failures no longer count toward a new cooldown.

---

### User Story 3 - Keep protected areas inaccessible without login (Priority: P1)

As a user, I want protected content and actions restricted to authenticated users so application information is not accessible without login.

**Why this priority**: Login only provides value if protected access actually requires it.

**Independent Test**: In an unauthenticated state, try protected navigation, bookmarked addresses, and direct attempts to read protected information or perform protected actions. Verify that no protected content or action is available.

**Acceptance Scenarios**:

1. **Given** an unauthenticated user, **When** they open a protected page through navigation or a direct address, **Then** they are directed to login without seeing protected content.
2. **Given** an unauthenticated user, **When** they attempt a protected read or action while bypassing visible navigation, **Then** access is denied, no protected data is disclosed, and no protected change occurs.
3. **Given** authentication is expired or otherwise invalid, **When** the user next requests a protected page or action, **Then** it is denied and page navigation directs them to login.
4. **Given** a user has logged in, **When** they access an area subject to additional role restrictions, **Then** existing role restrictions still apply; login does not grant additional roles or permissions.

### Edge Cases

- Submitting two empty inputs produces required-field feedback and does not grant access.
- A password differing only in capitalization or whitespace fails unless it exactly matches the account password; entered passwords are not trimmed or otherwise modified.
- Incorrect credentials receive the generic credential error until five failures for the same email within a rolling 15-minute window trigger a 15-minute cooldown. Attempts during the cooldown receive a generic temporary-cooldown message and remaining wait time; they cannot authenticate or extend the cooldown. Email variants treated as the same login identity share the same failure history across browsers and devices. Concurrent attempts must not bypass the threshold.
- Only incorrect-credential failures count toward the threshold; input-validation failures and service outages do not. Successful login clears prior failures. Cooldown expiry clears the failures that caused it; unrelated email addresses are unaffected. Existing authenticated sessions remain valid during a login cooldown.
- A failed login never echoes the password in an error message or elsewhere on screen.
- A direct protected address remains inaccessible after a failed login and becomes accessible only after authentication and any applicable permission checks.
- Failure diagnostics contain neither submitted credentials nor personal data, including the submitted email address.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The login page MUST be accessible to unauthenticated users and provide labeled email address and password inputs and a way to submit them.
- **FR-002**: The password input MUST remain masked during typing, pasting, and autofill. The application MUST NOT provide plaintext password display or repeat the password in messages.
- **FR-003**: Both fields MUST be required. Missing values or an invalid email format MUST prevent successful login and produce understandable correction feedback.
- **FR-004**: A registered user submitting their matching email and password MUST be authenticated as that account when no login cooldown is active for that email. Password matching MUST preserve exact case and whitespace.
- **FR-005**: Incorrect emails or passwords MUST fail without creating authenticated access. Outside an active cooldown, both cases MUST produce the same understandable credential error without identifying which credential was incorrect or whether the account exists. During a cooldown, both cases MUST receive the same temporary-cooldown response defined in FR-014.
- **FR-006**: After successful login, the user MUST be redirected to the application's authenticated landing area.
- **FR-007**: A failed login MUST keep the user on the login page and allow corrected credentials to be submitted again when no cooldown is active; during a cooldown it MUST explain when retry becomes available.
- **FR-008**: Valid authentication MUST identify the same user across navigation and page reloads until that authentication expires or becomes invalid.
- **FR-009**: Unauthenticated users MUST be denied every protected page, data read, and action, including direct access that bypasses visible controls. Denial MUST disclose no protected data and make no protected changes. Protected page navigation MUST direct the user to login.
- **FR-010**: Expired or invalid authentication MUST be treated as unauthenticated on the next protected access attempt.
- **FR-011**: Successful login MUST preserve existing account roles and MUST NOT bypass additional permission restrictions.
- **FR-012**: If login is temporarily unavailable, the system MUST deny access and display an understandable retry message rather than reporting success or falsely identifying credentials as incorrect.
- **FR-013**: Login diagnostics MUST exclude credentials and personal data, including submitted email addresses. Verification MUST inspect diagnostics from successful, rejected, and unavailable-service login scenarios.

- **FR-014**: After five incorrect-credential failures for the same email within a rolling 15-minute window, the system MUST block further login attempts for that email for 15 minutes from the fifth failure, including valid credentials. The policy MUST apply equally to registered and unknown emails, across browsers/devices and equivalent email variants, and MUST hold under concurrent attempts. Blocked attempts MUST show a generic cooldown message and remaining wait time without revealing account existence or extending the cooldown. Expiry MUST restore eligibility automatically and clear the triggering failure history. Successful login MUST clear prior failures; validation failures and service outages MUST NOT count. A cooldown MUST NOT invalidate existing authenticated sessions or affect other email addresses.

### Key Entities *(include if feature involves data)*

- **Registered User**: An existing account with an identity, a registered email address, associated password credentials, and any existing roles.
- **Authentication State**: Recognition that a particular user has successfully logged in and that this recognition remains valid; invalid or expired recognition grants no protected access.
- **Login Attempt History**: Recent incorrect-credential failures and any cooldown expiry associated with an email identity, whether or not a registered account exists. Used only to enforce the attempt limit; never included in diagnostics.
- **Protected Area**: Application pages, information, and actions requiring valid authentication and any additional existing permissions.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In acceptance testing, a registered user with known valid credentials and no active cooldown can reach the authenticated landing area within one minute without assistance under normal service availability.
- **SC-002**: 100% of valid-credential acceptance cases outside an active cooldown authenticate the correct account and redirect successfully; 100% of incorrect-credential cases fail without granting access.
- **SC-003**: 100% of tested unauthenticated, expired-authentication, and invalid-authentication attempts to access protected pages, data, or actions are denied, including direct access attempts, with zero protected disclosures or changes.
- **SC-004**: In all password-entry and failure-message acceptance cases, zero plaintext passwords are displayed; login diagnostics contain zero credentials or personal data.
- **SC-005**: In acceptance testing, users can understand the failure feedback, correct an invalid entry, and successfully retry with valid credentials without assistance once any active cooldown expires.
- **SC-006**: All tested permitted navigation and reload flows retain the authenticated user's identity while authentication is valid, and all tested role-restricted flows retain their existing permission restrictions.

- **SC-007**: In all tested registered-email and unknown-email cases, the fifth incorrect-credential failure within 15 minutes starts a 15-minute cooldown. Every attempt during that cooldown is denied, including valid credentials and concurrent or cross-device attempts; attempts do not extend the deadline. At expiry, valid credentials succeed without administrator action. Tests confirm failure-count exclusions, successful-login reset, unaffected other emails and preservation of existing authenticated sessions.

## Assumptions

- Accounts with unique registered email addresses and usable password credentials are available before login acceptance testing and release. Account provisioning is a dependency, not a user registration flow included here.
- This feature covers email/password login and enforcement of authentication. Registration, password recovery or changes, logout user flows, remember-me options, and additional authentication methods are outside scope.
- Successful login uses one authenticated landing area available to registered users. Returning users to a previously requested page is not required.
- Email matching follows the account provisioning rules; this feature does not introduce a different email identity policy. Passwords are matched exactly.
- Authentication lifetime follows the application's agreed security policy, to be specified during planning; this specification requires rejection whenever authentication is expired or invalid.
- Role assignment and role-specific access rules remain governed by the separate user-role-management feature. This feature supplies authenticated identity but does not provision the initial Admin or change roles.
- The one-minute completion target assumes a user who knows their credentials and normal service availability; it is a proposed usability target rather than a measured result.
