# Feature Specification: User Role Management

**Feature Branch**: `003-user-role-management`

**Created**: 2026-09-16

**Status**: Draft

**Input**: User description: "As an admin, I want to assign one or more roles to users so that permissions and access within Raumlotse can be controlled. Roles: Admin, University Staff, Student, Lecturer, Viewer. Only admins can view and modify user role assignments. Every user must have at least one role, multiple roles are allowed, new users start as Viewers, Viewer is the default, and removing all roles must be prevented. University Staff is separate from Admin."

## Clarifications

### Session 2026-09-16

- Q: Should sign-in and user account creation be included in this feature, given that Raumlotse currently has no authentication system? → A: Build authentication separately; role management depends on it before release.
- Q: Should the system prevent removing Admin from the last remaining Admin user? → A: Block removal of Admin from the last Admin, including self-removal.
- Q: If two Admins edit the same user's roles simultaneously, what should happen when the second Admin saves an outdated selection? → A: Reject the outdated save and require reviewing the latest roles before retrying.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - View and modify a user's roles (Priority: P1)

As an Admin, I want to select a user, review their assigned roles, and save a revised selection so their responsibilities are represented accurately.

**Why this priority**: Managing assignments is the central capability and enables controlled access decisions.

**Independent Test**: With an Admin and an existing user, view the user's roles, save a different nonempty selection, and reopen the user to verify the saved selection.

**Acceptance Scenarios**:

1. **Given** an Admin and existing users, **When** the Admin selects a user for role management, **Then** the user's identity and all currently assigned roles are displayed together with the five available roles.
2. **Given** a user with Viewer, **When** an Admin assigns Student and Lecturer and removes Viewer in one change, **Then** exactly Student and Lecturer are saved and displayed when the user is reopened.
3. **Given** a user with several roles, **When** an Admin removes one while retaining another, **Then** the remaining selection is saved successfully.
4. **Given** an Admin preparing a change, **When** they cancel without saving, **Then** the user's saved roles remain unchanged.
5. **Given** a successful save, **When** the Admin reviews the result, **Then** they see confirmation and the saved roles; if saving fails, they see a failure message and no partial assignment is applied.
6. **Given** two Admins opened the same user's roles and one saved a change, **When** the other saves their outdated selection, **Then** the save is rejected without overwriting the first change, the conflict is explained, and the Admin must review the latest roles before retrying.

---

### User Story 2 - Restrict role management to Admins (Priority: P1)

As a user, I want role assignments protected from unauthorized changes so only trusted administrators can grant or remove roles.

**Why this priority**: Role management would allow privilege escalation without enforced authorization.

**Independent Test**: Attempt role management as an Admin, as each non-Admin role, as a combination of non-Admin roles, and without signing in.

**Acceptance Scenarios**:

1. **Given** a user without Admin, **When** they attempt to access role management or submit any role change, including changing their own roles or bypassing visible controls, **Then** access is denied and assignments remain unchanged.
2. **Given** a user with University Staff but without Admin, **When** they attempt role management, **Then** access is denied; University Staff does not confer Admin privileges.
3. **Given** a user with Admin and any additional roles, **When** they manage roles, **Then** they have the same role management rights as a user with Admin alone.
4. **Given** an unauthenticated person, **When** they attempt role management, **Then** access is denied and no roles change.
5. **Given** an Admin who opened role management and subsequently lost Admin, **When** they attempt another view or save, **Then** access is denied based on their current roles.
6. **Given** only one user has Admin, **When** that user attempts to remove their own Admin role while retaining another role, **Then** the change is rejected with an explanation that at least one Admin must remain and their previous roles are preserved.
7. **Given** multiple users have Admin, **When** an authorized change removes Admin from one user while another Admin remains, **Then** the change succeeds provided that the target user retains at least one role.

---

### User Story 3 - Keep every user assigned at least one role (Priority: P1)

As an Admin, I want new users to start as Viewers and invalid empty assignments to be rejected so every user has an explicit role.

**Why this priority**: Default assignments and the minimum-role rule are required for a consistent access model.

**Independent Test**: Create a user, verify Viewer is their only initial role, attempt to save an empty assignment, and verify their previous roles remain intact.

**Acceptance Scenarios**:

1. **Given** a new user is created through any supported user creation flow, **When** creation completes, **Then** Viewer is automatically assigned as their only initial role without requiring manual selection.
2. **Given** any user's existing roles, **When** an Admin tries to save no roles, **Then** the change is rejected with a message that at least one role is required and all previous assignments remain unchanged.
3. **Given** a Viewer, **When** an Admin removes Viewer and assigns another supported role in the same change, **Then** the nonempty replacement is accepted.
4. **Given** a user with Viewer and Student, **When** an Admin saves that selection, **Then** both roles are retained; Viewer is not automatically removed because another role exists.

### Edge Cases

- Unknown role names are rejected without changing existing assignments; the same role cannot be assigned twice.
- Saving the same roles again from the current assignment succeeds without creating duplicate assignments; an outdated selection remains subject to conflict rejection.
- An attempt to edit a user who no longer exists fails with an understandable message and does not affect other users.
- If a user's roles changed after an Admin opened them, saving that outdated selection is rejected without changing the latest saved roles. The Admin must review the latest roles before retrying; changes are not automatically merged or overwritten. Competing edits must also preserve at least one role per user.
- An Admin may edit their own roles subject to the minimum-role and last-Admin safeguards. A permitted removal of their own Admin role ends their role management access on the next action.
- Removing Admin from the last remaining Admin is rejected, including self-removal. Concurrent role changes must not leave the system without an Admin; any change that would do so is rejected without altering the target user's saved roles.
- Existing users with no roles receive Viewer before this feature is made available; valid existing assignments are preserved.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST support exactly five assignable roles: Admin, University Staff, Student, Lecturer, and Viewer.
- **FR-002**: Only authenticated users whose current assignments include Admin MUST be allowed to access role management or modify any user's roles. This restriction MUST apply to every route of access, including attempts that bypass visible controls.
- **FR-003**: An Admin MUST be able to select an existing user, identify that user, and view their complete current role selection and available roles.
- **FR-004**: An Admin MUST be able to add or remove roles and save any nonempty combination of the supported roles, including assigning multiple roles to one user, provided at least one user retains Admin.
- **FR-005**: Every user MUST have at least one assigned role. A change that would remove all roles MUST be rejected with an explanation and MUST preserve the previous selection; it MUST NOT silently substitute Viewer for an invalid empty selection.
- **FR-006**: Newly created users MUST start with Viewer as their sole default role in every supported creation flow. Subsequent role changes MUST require Admin authorization.
- **FR-007**: Viewer MUST be an ordinary assignable role after creation: it MAY coexist with other roles and MAY be removed when at least one other role remains.
- **FR-008**: University Staff MUST be separate from Admin. Neither role MUST automatically assign the other; University Staff alone MUST NOT grant role management access.
- **FR-009**: Saved assignments MUST persist and be shown when the user is revisited. Successful saves MUST be confirmed; failed saves MUST explain the failure and MUST NOT partially apply the proposed selection. Canceling MUST leave saved assignments unchanged.
- **FR-010**: The system MUST reject unsupported roles and MUST store each role at most once per user. Repeating an unchanged valid selection MUST leave the assignment unchanged.
- **FR-011**: Authorization MUST use the acting user's current roles for each role management view or change. Granting or removing Admin MUST affect the user's next role management action without requiring a fresh sign-in.
- **FR-012**: Changes targeting a nonexistent user MUST fail without modifying another user. Concurrent changes MUST preserve the minimum of one role per user.
- **FR-013**: Before role management becomes available, existing users without an assigned role MUST receive Viewer, and existing valid role assignments MUST be preserved.
- **FR-014**: The system MUST reject any role change that would remove Admin from the last remaining Admin user, including self-removal and concurrent changes. Rejection MUST preserve the target user's saved roles and explain that at least one Admin must remain.
- **FR-015**: The system MUST reject a role save if the target user's assignments have changed since the acting Admin loaded them. It MUST preserve the latest saved roles, explain the conflict, and require the Admin to review the latest roles before retrying. It MUST NOT automatically merge the conflicting selections or overwrite the intervening change.

### Key Entities *(include if feature involves data)*

- **User**: An identifiable Raumlotse account with one or more assigned roles.
- **Role**: One of the five predefined responsibility categories. Admin specifically grants the ability to manage user roles.
- **Role Assignment**: The association between a user and a supported role. Each association is unique and a user's complete selection contains between one and five roles.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In acceptance testing, an Admin can locate a known user, review their roles, save a valid revision, and verify it within two minutes without assistance.
- **SC-002**: All 31 possible nonempty combinations of the five roles can be saved and subsequently viewed accurately for a target user when a separate Admin remains assigned.
- **SC-003**: 100% of tested role management attempts by unauthenticated users or users without Admin are denied, with zero unauthorized assignment changes.
- **SC-004**: 100% of tested new-user creation paths assign Viewer as the sole initial role, and zero users remain without a role after existing users are prepared for this feature.
- **SC-005**: 100% of tested empty-role changes are rejected without altering previous assignments, including competing-change scenarios.
- **SC-006**: In acceptance testing, Admin grants and removals take effect on the affected user's next role management action in every case.
- **SC-007**: 100% of tested attempts to remove the last Admin are rejected without changing saved assignments, including self-removal and concurrent-change scenarios; at least one Admin remains after every accepted role change.
- **SC-008**: 100% of tested outdated role saves are rejected without overwriting intervening changes. In acceptance testing, an authorized Admin can review the latest roles and successfully retry a valid change when no further conflicting change occurs.

## Assumptions

- This feature governs user role assignments and permission to manage those assignments. Defining or changing room, reservation, equipment, navigation, or other business permissions for each role is outside scope.
- Roles apply across Raumlotse rather than per university, room, or department. The five roles are predefined; creating custom roles is outside scope.
- Authentication, sign-in, and user account creation MUST be delivered as a separate prerequisite feature. Role management MUST NOT be released until identifiable user accounts, authenticated identity, and an initial Admin account are available. The implicit administrator used by earlier features cannot satisfy acceptance criteria.
- Sign-in methods, account registration design, and the secure operational process for establishing or recovering the initial Admin are separate prerequisite concerns. Ordinary new-user creation still defaults exclusively to Viewer.
- Self-editing is allowed subject to the minimum-role and last-Admin safeguards. Initial Admin provisioning and operational recovery remain separate prerequisite concerns.
- Existing valid assignments are assumed to use the supported role vocabulary. Any incompatible legacy assignments must be resolved during planning before rollout.
