# `data-testid` reference (web)

Selectors for the Playwright e2e suite. Use `page.getByTestId('<id>')`. Ids in kebab-case; `<id>` = numeric entity id, `<i>` = 0-based row index, `<code>` = category code.

## How the ids land in the DOM

| Element | Where the id is | How to drive it |
|---|---|---|
| Text inputs, textareas, money inputs, date inputs (`el-input`, `MoneyInput`, `SkillsInput`) | on the native `<input>` / `<textarea>` | `fill()`, `press('Enter')`, `inputValue()` |
| Dates (`project-start-date`, `…-due-date`) | native `<input type="date">` | `fill('2027-01-31')` (format `YYYY-MM-DD`) |
| Money (`project-budget`, `…-amount`) | native `<input>` | `fill('1500')` or `fill('1500.50')` |
| Skills (`project-skills`) | native `<input>` | `fill('Vue')` then `press('Enter')` (or comma). Chips: `project-skills-tag-<i>`; suggestions: `project-skills-suggest-<slug>` |
| Selects (`el-select`) | wrapper `div.el-select` | `click()` the select, then `click()` the option id (`…-option-<value>`) |
| Checkboxes (`el-checkbox`) | wrapper `<label>` | `check()` / `isChecked()` (Playwright retargets to the input) |
| Radio buttons (`el-radio`, `el-radio-button`) | wrapper `<label>` | `check()` or `click()` |
| Switches (`el-switch`) | wrapper `div.el-switch` | `click()` |
| Buttons | the `<button>` | `click()`, `isDisabled()` |
| Status tags (`StatusTag`) | `<span class="el-tag">` | text is translated; the raw enum is in `data-status` (e.g. `toHaveAttribute('data-status', 'OPEN')`) |
| Dialogs / drawers | the dialog / drawer element | `waitFor()` / `waitFor({ state: 'hidden' })` |

Locale: the client area follows the browser language (fallback `en`); the admin area defaults to `es`. Prefer asserting on ids and `data-status` rather than on text. To force a locale use the switcher (`locale-en` / `locale-es`, `admin-locale-es` / `admin-locale-en`) or set `localStorage['qiao.locale']` / `localStorage['qiao.admin.locale']`.

Tokens: `localStorage['qiao.user.token']` (client) and `localStorage['qiao.admin.token']` (admin).

## Layout (client area)

| id | element |
|---|---|
| `nav-brand` | logo link |
| `nav-dashboard`, `nav-company`, `nav-account` | header links (desktop, logged in) |
| `nav-menu-toggle` | hamburger (≤760 px); then `nav-dashboard-mobile`, `nav-company-mobile`, `nav-account-mobile`, `nav-logout-mobile` |
| `nav-login` | "Sign in" button (logged out) |
| `nav-logout` | "Sign out" (desktop) |
| `locale-switcher`, `locale-en`, `locale-es` | language switch |
| `footer-terms`, `footer-privacy`, `footer-declarations` | footer legal links |

## Landing `/`

`landing-eyebrow`, `landing-title`, `landing-cta` (Post a project), `landing-how`, `landing-value-vetted|legal|milestones|privacy`, `landing-cta-bottom`.

## Login `/login`

| id | element |
|---|---|
| `login-title` | heading |
| `login-email` | email input |
| `login-send-code` | "Send code" |
| `login-code-sent` | "We sent a code to …" text |
| `login-change-email` | back to the email step |
| `login-code` | 6-digit code input |
| `login-new-account` | block shown only for a new user (after the API answered `TERMS_REQUIRED` / `AUTH_ROLE_REQUIRED`) |
| `login-accept-terms` | Terms + Privacy checkbox (new user) |
| `login-terms-link`, `login-privacy-link` | links to `/legal/terms`, `/legal/privacy` (new tab) |
| `login-terms-error` | "you must accept" message |
| `login-submit` | "Sign in" / "Create account" |
| `login-resend` | resend code (60 s cooldown) |
| `login-error` | general error alert |
| `login-expired` | "session expired" notice (after a 401 redirect) |
| `login-not-client`, `login-engineer-app-link`, `login-engineer-link` | engineer account notice / link to `/m/` |

Flow for a **new** user: fill `login-email` → click `login-send-code` → fill `login-code` (from `GET /api/dev/last-otp`) → click `login-submit` → wait for `login-accept-terms` → `check()` → click `login-submit` → URL `/client`.
For an **existing** user the first `login-submit` signs in directly.

## Client dashboard `/client`

| id | element |
|---|---|
| `dashboard-title` | heading |
| `dashboard-new-project`, `dashboard-empty-new-project` | new project buttons |
| `company-status-banner` | banner |
| `company-status` | company status tag (`data-status` = DRAFT / PENDING_REVIEW / APPROVED / REJECTED / BLOCKED) |
| `company-review-note` | review note (REJECTED / BLOCKED) |
| `dashboard-company-cta` | "Complete / Update company profile" |
| `project-list`, `dashboard-no-projects` | project list / empty state |
| `project-row-<id>`, `project-link-<id>`, `project-status-<id>` | project row, title link, status tag |
| `contract-list`, `contract-row-<id>`, `contract-link-<id>`, `contract-status-<id>` | contracts |

## Company `/client/company`

| id | element |
|---|---|
| `company-title`, `company-status` | heading, status tag |
| `company-name` | input |
| `company-country` → `company-country-option-<ISO2>` | select (filterable: you can also type, e.g. `Germany`) |
| `company-website` | input (`https://…`) |
| `company-size` → `company-size-option-1-10`, `-11-50`, `-51-200`, `-201-1000`, `-1000plus` | select |
| `company-save` | save details (PUT) |
| `company-declarations-text`, `company-declarations-version` | declarations (markdown) and version |
| `company-accept-declarations` | checkbox |
| `company-submit` | save + accept declarations → PENDING_REVIEW, then navigates to `/client` |
| `company-error`, `company-locked`, `company-review-note`, `company-declarations-accepted` | messages |

## Project form `/client/projects/new`, `/client/projects/:id/edit`

| id | element |
|---|---|
| `project-form-title`, `project-form-status` | heading, status (edit) |
| `project-title`, `project-description` | inputs |
| `project-category` → `project-category-option-<code>` | select; prohibited categories have the class `is-disabled` |
| `project-skills` (+ `project-skills-tag-<i>`, `project-skills-suggest-<slug>`) | skills |
| `project-keyword-hint` | live warning when employment / sensitive keywords are typed |
| `project-budget` | budget (USD) |
| `project-start-date`, `project-end-date` | dates |
| `project-milestone-<i>` | milestone block |
| `project-milestone-<i>-title`, `-deliverable`, `-due-date`, `-amount`, `-remove` | milestone fields |
| `project-add-milestone` | add a milestone |
| `project-milestone-sum` | live sum line; `data-match="true"` when the sum equals the budget |
| `project-milestone-sum-diff`, `project-budget-from-milestones` | difference / "use the total as budget" |
| `project-decl-no-controlled-tech`, `project-decl-no-bulk-us-sensitive-data`, `project-decl-no-employment`, `project-decl-no-prohibited-category` | the four declarations |
| `project-save-draft`, `project-submit`, `project-cancel` | actions (on success both navigate to `/client/projects/<id>`) |
| `project-form-error` | form-level error (e.g. `COMPANY_NOT_APPROVED`, with `project-form-company-link`) |
| `project-error-milestones`, `project-error-milestones-sum`, `project-error-declarations` | group errors |
| `project-form-review-note`, `project-form-readonly` | CHANGES_REQUESTED note / not editable |

Per-field errors (client-side or from `VALIDATION_ERROR.data.fields`) are shown by the form item: the item gets the class `is-error`, e.g. `page.locator('.el-form-item.is-error').filter({ has: page.getByTestId('project-budget') })`.

## Project detail `/client/projects/:id`

| id | element |
|---|---|
| `project-title`, `project-status`, `project-budget`, `project-description` | header / data |
| `project-edit`, `project-detail-submit` | Edit / Submit for review (DRAFT, CHANGES_REQUESTED) |
| `project-under-review`, `project-review-note`, `project-flags` | alerts |
| `project-milestones` | milestones table |
| `applications`, `applications-empty` | applications section |
| `application-card-<id>` | card |
| `application-<id>-alias` | engineer alias |
| `application-status-<id>`, `application-total-<id>` | status tag, client price |
| `application-prices-toggle-<id>` | per-milestone breakdown |
| `application-shortlist-<id>` (SUBMITTED) | |
| `application-select-<id>` (SUBMITTED, SHORTLISTED) | |
| `application-decline-<id>` (SUBMITTED, SHORTLISTED, SELECTED) | |
| `application-hire-<id>` (SELECTED, project OPEN) → `hire-dialog`, `hire-confirm`, `hire-cancel` | hire; navigates to `/client/contracts/<contractId>` |
| `application-cv-<id>` → `cv-dialog`, `cv-text`, `cv-education`, `cv-portfolio`, `cv-close` | CV |
| `cv-consent-required` | shown when the API answers `CV_CONSENT_REQUIRED` |
| `application-team-<id>` | team card of a team application (spec §12) instead of the single engineer card |
| `application-team-name-<id>` | team name |
| `application-<id>-member-<alias>` | one member of the team card (`…-alias` inside it is the alias) |
| `application-<id>-member-<alias>-cv` → `cv-dialog` | that member's CV (each member consents separately; `application-cv-<id>` is not rendered for teams) |

## Contract `/client/contracts/:id`

| id | element |
|---|---|
| `contract-title`, `contract-status`, `contract-total`, `contract-project-link`, `contract-back` | header |
| `contract-paid-banner` | after returning from checkout (`?paid=1`) |
| `contract-pending-signatures` | contract PENDING_SIGNATURES |
| `contract-doc-<docId>` | document card (`data-kind` = CLIENT_SERVICES / ENGINEER_DELIVERABLES) |
| `contract-doc-body-<docId>` | rendered markdown |
| `contract-doc-accept-<docId>` | Accept (client document, not yet accepted) |
| `contract-doc-accepted-<docId>` | accepted marker |
| `contract-doc-waiting-<docId>` | engineer document not yet accepted |
| `contract-engineer-agreement` | engineer side acceptance (`data-status` = ACCEPTED / PENDING; for a team: every member) |
| `contract-team-name`, `contract-team`, `contract-team-member-<alias>` | team contracts: members and their acceptance (`data-status` = ACCEPTED / PENDING) |
| `milestone-row-<id>`, `milestone-status-<id>`, `milestone-amount-<id>`, `milestone-note-<id>` | milestone |
| `milestone-fund-<id>` | Fund (PENDING_FUNDING, enabled when contract ACTIVE) → checkout → navigates to the returned url |
| `milestone-accept-<id>`, `milestone-revision-<id>`, `milestone-dispute-<id>` | SUBMITTED actions |
| `milestone-reason-dialog`, `milestone-reason-input`, `milestone-reason-submit`, `milestone-reason-cancel` | reason dialog (revision / dispute) |
| `message-list`, `message-item-<id>`, `message-input`, `message-send`, `message-refresh` | messages (Ctrl/Cmd+Enter also sends) |
| `attachment-list`, `attachment-empty`, `attachment-item-<id>`, `attachment-download-<id>` | files |
| `attachment-milestone` → `attachment-milestone-option-<milestoneId>` | optional milestone for the upload |
| `attachment-file-input` | hidden `<input type="file">`: use `setInputFiles()` (uploads immediately) |
| `attachment-upload` | button that opens the file picker |

Review (contract `COMPLETED`, spec §13): `review` (card), `review-star-1` … `review-star-5` (buttons, `aria-checked`), `review-comment` (textarea), `review-submit`, `review-done` (`data-stars` = the stars given; shown once the client side reviewed).

## Test checkout `/pay/fake/:paymentId`

`pay-test-mode`, `pay-amount`, `pay-status`, `pay-confirm` ("Pay (test mode)" → back to `/client/contracts/<id>?paid=1`), `pay-error`, `pay-back`.

## Account `/account`, legal `/legal/:doc`

`account-title`, `account-email`, `account-logout`, `account-locale` / `account-locale-en` / `account-locale-es`, `account-export`, `account-delete` → `account-delete-dialog`, `account-delete-understand`, `account-delete-confirm`, `account-delete-cancel`, `account-delete-error` (e.g. `ACCOUNT_HAS_ACTIVE_CONTRACTS`).

`legal-title`, `legal-version`, `legal-body`, `legal-error`, `legal-unknown`, `legal-nav-terms|privacy|client-declarations`. 404 page: `not-found`, `not-found-home`.

## Admin

### Login `/admin/login` and layout

`admin-login-title`, `admin-login-email`, `admin-login-password`, `admin-login-totp` (optional), `admin-login-submit`, `admin-login-error`.

Layout: `admin-menu-dashboard|companies|engineers|projects|disputes|settlements|compliance|categories|outbox|admins` (only the items allowed by the admin's roles are rendered; on ≤900 px open `admin-menu-toggle` and use the same ids with `-mobile`), `admin-current-email`, `admin-locale-es`, `admin-locale-en`, `admin-logout`, `admin-denied` (redirected from a section without the role).

### Dashboard `/admin`

`admin-dashboard-title`, `admin-dashboard-card-companies|engineers|projects|contracts|gmv|fees|transfers`, `admin-dashboard-open-companies|engineers|projects`, `admin-dashboard-transfer-level`.

### Companies `/admin/companies`

| id | element |
|---|---|
| `admin-companies-status` → `admin-companies-status-PENDING_REVIEW|APPROVED|REJECTED|BLOCKED|DRAFT|all` | status filter (default PENDING_REVIEW) |
| `admin-companies-table`, `admin-company-row-<id>`, `admin-company-status-<id>` | table |
| `admin-company-open-<id>` | open review drawer |
| `admin-company-drawer`, `admin-company-name`, `admin-company-status`, `admin-company-sanctions-status` | drawer |
| `admin-company-sanctions-clear`, `admin-company-sanctions-hit` | sanctions result |
| `admin-company-note` | note (required to reject) |
| `admin-company-approve` | disabled unless sanctions = CLEAR (`admin-company-approve-hint`) |
| `admin-company-reject`, `admin-company-error` | |

### Engineers `/admin/engineers`

`admin-engineers-status-<STATUS|all>`, `admin-engineers-table`, `admin-engineer-row-<id>`, `admin-engineer-status-<id>`, `admin-engineer-open-<id>`, `admin-engineer-drawer`, `admin-engineer-email`, `admin-engineer-cv`, `admin-engineer-partner-ref`, `admin-engineer-note`, `admin-engineer-approve` (disabled until a partnerRef is typed; `admin-engineer-approve-hint`), `admin-engineer-reject`, `admin-engineer-error`.

### Projects `/admin/projects`

`admin-projects-status-<STATUS|all>` (default SUBMITTED), `admin-projects-table`, `admin-project-row-<id>`, `admin-project-flags-<id>` (only when flagged), `admin-project-status-<id>`, `admin-project-open-<id>`, `admin-project-drawer`, `admin-project-status`, `admin-project-flags`, `admin-project-description` (flagged keywords wrapped in `<mark>`), `admin-project-category-prohibited`, `admin-project-note`, `admin-project-approve`, `admin-project-request-changes`, `admin-project-reject` (request changes / reject require a note), `admin-project-error`.

### Disputes `/admin/disputes`

`admin-disputes-table`, `admin-dispute-row-<milestoneId>`, `admin-dispute-resolve-<milestoneId>` → `admin-dispute-dialog`, `admin-dispute-resolution-accept`, `admin-dispute-resolution-refund`, `admin-dispute-note`, `admin-dispute-submit`, `admin-dispute-cancel`, `admin-dispute-error`.

### Settlements `/admin/settlements`

`admin-settlement-period` (text `YYYY-MM`, defaults to the previous month), `admin-settlement-create`, `admin-settlement-error` (e.g. `NOTHING_TO_SETTLE`), `admin-settlements-table`, `admin-settlement-row-<id>`, `admin-settlement-status-<id>`, `admin-settlement-view-<id>`, `admin-settlement-csv-<id>` (authenticated download: use `page.waitForEvent('download')`), `admin-settlement-export-<id>` (mark exported, DRAFT only), `admin-settlement-drawer`, `admin-settlement-lines`, `admin-settlement-drawer-csv`, `admin-settlement-drawer-export`.

### Compliance `/admin/compliance`

`admin-transfers`, `admin-transfers-year` → `admin-transfers-year-option-<yyyy>`, `admin-transfers-any`, `admin-transfers-pseudonymized`, `admin-transfers-full-cv`, `admin-transfers-level` (`data-status` = OK / WARN / CRITICAL / LIMIT), `admin-audit-action`, `admin-audit-actor-type` → `admin-audit-actor-type-option-USER|ADMIN|PARTNER|SYSTEM`, `admin-audit-search`, `admin-audit-table`, `admin-audit-pagination`.

### Categories `/admin/categories`

`admin-categories-table`, `admin-category-row-<code>`, `admin-category-allowed-<code>` (switch), `admin-category-name-en-<code>`, `admin-category-name-es-<code>`, `admin-category-name-zh-<code>`, `admin-category-save-<code>` (enabled after a change).

### Mail outbox `/admin/outbox`

`admin-outbox-table`, `admin-outbox-row-<id>`, `admin-outbox-view-<id>` → `admin-outbox-dialog`, `admin-outbox-body`, `admin-outbox-refresh`.

### Admins `/admin/admins` (SUPERADMIN)

`admin-admins-table`, `admin-admins-row-<id>`, `admin-admins-totp-<id>` (`data-status` = ENABLED / DISABLED), `admin-admins-totp-reset-<id>` (other admins with TOTP; Element Plus confirm box), `admin-new-email`, `admin-new-password`, `admin-new-role-SUPERADMIN|OPS|FINANCE|COMPLIANCE`, `admin-new-submit`, `admin-new-error`.

### Reviews `/admin/reviews` (OPS, COMPLIANCE)

`admin-reviews-title`, `admin-reviews-refresh`, `admin-reviews-side` with `admin-reviews-side-all|CLIENT|ENGINEER` (radio buttons), `admin-reviews-table`, `admin-review-row-<id>`, `admin-review-stars-<id>`.

### My profile `/admin/profile` (any admin, menu `admin-menu-profile`) — TOTP (spec §14)

`admin-profile-title`, `admin-profile-email`, `admin-totp-status` (`data-status` = ENABLED / DISABLED), `admin-totp-error`.
Setup: `admin-totp-setup` → `admin-totp-setup-panel` with `admin-totp-qr` (`<img>` with a `data:image/png` QR drawn locally), `admin-totp-secret` (base32 key text), `admin-totp-uri` (hidden input holding the `otpauth://` URI), `admin-totp-code`, `admin-totp-enable`, `admin-totp-cancel`.
Enabled: `admin-totp-disable-form`, `admin-totp-disable-code`, `admin-totp-disable`.
Login with TOTP: `admin-login-totp` (always visible); a reused code answers `TOTP_INVALID`.

Every admin page has `admin-<section>-title`; list pages also have `admin-<section>-refresh`.
