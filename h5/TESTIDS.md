# H5 — `data-testid` reference

Every interactive element and the key texts of the engineer app carry a `data-testid` (kebab-case).
Use them as `[data-testid="…"]` selectors; Playwright's `getByTestId()` works too.

Conventions

- **Text inputs and textareas**: the id is on the native `<input>` / `<textarea>` (not on Vant's wrapper
  `<div>`), so `page.fill()` works directly. The file input of the milestone uploader works with `setInputFiles()`.
- **Checkboxes** (`van-checkbox`) are `role="checkbox"` elements with `aria-checked`; `click()` / `check()` both work.
  Their clickable area contains no links, so a click on the element centre always toggles it.
- **Switches** (`van-switch`) are `role="switch"` with `aria-checked`.
- **Status tags** (profile, application, contract, milestone, settlement) expose the raw API status in
  `data-status`, e.g. `[data-testid="milestone-status-51"][data-status="SUBMITTED"]`.
- `<id>` placeholders are the API ids (`project.id`, `milestone.id`, `application.id`, `document.id`, …).
- Confirmation dialogs are rendered once by the app: `confirm-dialog`, `confirm-ok`, `confirm-cancel`.
- Bottom tab bar: `tabbar`, `tab-projects`, `tab-applications`, `tab-contracts`, `tab-me`. Detail pages have no tab bar;
  their back arrow is `nav-back`. Every page header is `page-header`.
- Generic states: `state-loading`, `state-error` (+ `state-retry`), `state-empty`.

## Main flow (as requested)

| testid | Element |
|---|---|
| `login-email` | Email input |
| `login-send-code` | "获取验证码" button |
| `login-code` | 6-digit code input |
| `login-accept-terms` | Terms + Privacy checkbox (required for new users) |
| `login-accept-crossborder` | Separate cross-border (Hong Kong) consent checkbox |
| `login-submit` | Log in / register button |
| `profile-headline` | Profile headline input |
| `profile-submit` | "提交审核" (submit profile for review) |
| `project-card-<id>` | Project card in the list (link to the detail) |
| `apply-price-<milestoneId>` | Price input per milestone in the apply form |
| `apply-submit` | Send bid |
| `consent-pseudonymized-accept` | "同意并投标" in the SHARE_PROFILE_PSEUDONYMIZED consent sheet |
| `cv-consent-grant-<applicationId>` | Grant SHARE_FULL_CV for a SELECTED application |
| `contract-accept-<docId>` | Accept a contract document (only `ENGINEER_DELIVERABLES`) |
| `milestone-submit-<id>` | Open the "submit delivery" sheet for a milestone |
| `message-input` | Message textarea |
| `message-send` | Send message |

## Landing `/m/`

`landing`, `landing-title`, `landing-locale` (zh/en toggle), `landing-login` (hero CTA, logged out),
`landing-enter` (hero CTA, logged in), `landing-login-bottom`, `landing-feature-<project|pay|vpn|privacy>`,
`landing-prohibited`, `landing-prohibited-<categoryCode>`, `landing-legal-terms`, `landing-legal-privacy`,
`landing-legal-cross-border`.

## Login `/m/login`

`login-page`, `login-home`, `login-email`, `login-change-email`, `login-send-code`, `login-code-sent`, `login-code`,
`login-resend`, `login-agreements`, `login-new-user-notice` (shown after `TERMS_REQUIRED` / `AUTH_ROLE_REQUIRED`),
`login-accept-terms`, `login-link-terms`, `login-link-privacy`, `login-accept-crossborder`,
`login-crossborder-explain`, `login-link-crossborder`, `login-submit`.

The agreement block is visible on both steps (the app cannot know beforehand whether the email is new).
Typical new-user flow: fill email → `login-send-code` → fill `login-code` → click `login-accept-terms`
(and optionally `login-accept-crossborder`) → `login-submit`. After login a new engineer lands on `/m/profile`.

## Legal `/m/legal/:doc`

`legal-page`, `legal-doc-<doc>` (`terms`, `privacy`, `cross-border`, `engineer-declaration`, `client-declarations`).

## Profile `/m/profile`

| testid | Element |
|---|---|
| `profile-page`, `profile-status-card` | Page / status card |
| `profile-alias` | Alias text (e.g. QE-7K2P) |
| `profile-status` | Status tag (`data-status` = DRAFT / PENDING_REVIEW / APPROVED / REJECTED / NONE) |
| `profile-review-note` | Review note (REJECTED only) |
| `profile-headline`, `profile-bio` | Inputs |
| `profile-region` | Province field (opens picker) |
| `profile-region-picker`, `profile-region-confirm`, `profile-region-cancel` | Province picker and its buttons |
| `profile-skills` | Skill tag list |
| `profile-skill-input`, `profile-skill-add` | Add a skill |
| `profile-skill-remove-<index>` | Remove skill |
| `profile-skill-suggest-<slug>` | Quick-add suggestion (e.g. `profile-skill-suggest-spring-boot`) |
| `profile-years`, `profile-rate`, `profile-hours` | Years of experience, hourly rate (USD), hours/week |
| `profile-english-<NONE\|BASIC\|GOOD\|FLUENT>` | English level (radio buttons) |
| `profile-spanish-<NONE\|BASIC\|GOOD\|FLUENT>` | Spanish level |
| `profile-cv`, `profile-education` | Textareas |
| `profile-link-<index>`, `profile-link-remove-<index>`, `profile-link-add` | Portfolio links |
| `profile-declaration-text`, `profile-declaration-retry` | Declaration text (from `/api/public/legal/engineer-declaration`) |
| `profile-declaration` | Declaration checkbox |
| `profile-crossborder-missing`, `profile-grant-crossborder`, `profile-link-crossborder` | Shown when CROSS_BORDER_PROCESSING is missing |
| `profile-kyc` | "Real-name check and payouts are done by the partner" card |
| `profile-save`, `profile-submit` | Save / submit for review (bar hidden while PENDING_REVIEW) |

Province picker: click `profile-region`, then `profile-region-confirm` (first option, Beijing, is pre-selected).

## Projects `/m/projects` and `/m/projects/:id`

List: `projects-page`, `projects-search` (input; press Enter to search), `projects-categories`,
`projects-category-all`, `projects-category-<code>`, `projects-profile-banner`, `projects-list`,
`project-card-<id>`, `project-applied-<id>`, `projects-empty`.

Detail: `project-page`, `project-title`, `project-talent-budget`, `project-client-country`, `project-description`,
`project-milestone-<milestoneId>`, `apply-form`, `apply-not-approved`, `apply-go-profile`, `apply-cover-note`,
`apply-price-<milestoneId>`, `apply-total`, `apply-submit`, `project-applied` (shown once applied),
`project-view-applications`.

Consent sheet (opens on `apply-submit` when SHARE_PROFILE_PSEUDONYMIZED is missing, or on `CONSENT_REQUIRED`):
`consent-pseudonymized-sheet`, `consent-pseudonymized-accept` (records the consent, then applies),
`consent-pseudonymized-cancel`, `consent-pseudonymized-privacy`.

## Applications `/m/applications`

`applications-page`, `applications-browse` (empty state), `application-<id>`, `application-project-<id>`,
`application-status-<id>`, `application-withdraw-<id>` (asks `confirm-ok`), `cv-consent-<id>` (prompt box, SELECTED only),
`cv-consent-grant-<id>`, `cv-consent-granted-<id>`.

## Contracts `/m/contracts` and `/m/contracts/:id`

List: `contracts-page`, `contract-card-<id>`.

Detail: `contract-page`, `contract-title`, `contract-status`, `contract-doc-<docId>`, `contract-doc-toggle-<docId>`
(expand/collapse text), `contract-accept-<docId>`, `contract-doc-accepted-<docId>`, `milestone-<id>`,
`milestone-status-<id>`, `milestone-submit-<id>` (FUNDED / REVISION_REQUESTED and contract ACTIVE).

Submit sheet: `milestone-submit-sheet`, `milestone-note`, `milestone-file` (file input; each file is uploaded
immediately with `POST /api/contracts/{id}/attachments`), `milestone-submit-confirm`.

Messages: `messages`, `messages-refresh`, `message-list`, `message-<id>`, `message-input`, `message-send`.

## Teams `/m/teams` and `/m/teams/:id` (spec §12)

List: `teams-page`, `teams-explain`, `team-invitation-<teamId>` (pending invitation card),
`team-invitation-accept-<teamId>`, `team-invitation-decline-<teamId>`, `team-card-<teamId>` (link to the team),
`teams-empty`, `teams-need-approval`, `team-create-open` → sheet `team-create-sheet`, `team-create-name`,
`team-create-description`, `team-create-submit` (navigates to `/m/teams/<id>`).

Detail: `team-page`, `team-name`, `team-description`, `team-can-apply` (`data-status` = `READY` with ≥ 2 active members,
else `NOT_READY`), `team-member-<alias>` (`data-status` = INVITED / ACTIVE / DECLINED), `team-member-remove-<alias>`
(lead; cancels an invitation or removes a member, asks `confirm-ok`), `team-invite` (lead), `team-invite-alias`,
`team-invite-submit`, `team-invited` (+ `team-accept`, `team-decline`) for an invitee, `team-leave` (member),
`team-dissolve` (lead), `team-edit-open` → `team-edit-sheet`, `team-edit-name`, `team-edit-description`, `team-edit-save`.
Entry point: `me-teams` on `/m/me` (its value shows the number of pending invitations).

Team bid (project detail, only for the lead of a team that can apply): `apply-mode` with `apply-mode-individual` /
`apply-mode-team` (radios), `apply-team` (split editor), `apply-team-<teamId>` (radio, only with several teams),
`apply-share-even`, `apply-share-row-<alias>`, `apply-share-<alias>` (percentage input), `apply-share-amount-<alias>`
(≈ member total), `apply-share-total` (`data-valid="true"` when the split is > 0 for everyone and adds up to 100).
`apply-team-hint` is shown to approved engineers without such a team. Errors naming other members
(`CONSENT_REQUIRED`, `PROFILE_NOT_APPROVED`, `ALREADY_APPLIED` with `data.aliases`) appear as a toast listing their aliases.

Applications: `application-team-<id>` (`data-role` = LEAD / MEMBER, shows my share). Members have no
`application-withdraw-<id>`; each member has their own `cv-consent-grant-<id>`.

Team contract (detail): `contract-team` (members with `contract-team-member-<alias>`, `data-status` = ACCEPTED / PENDING),
`contract-my-share`, `contract-my-total`, `contract-team-total`, `milestone-amount-<id>` (my part),
`milestone-lead-only-<id>` (members, instead of `milestone-submit-<id>`). List: `contract-card-team-<id>`.

Review of the client (contract `COMPLETED`, spec §13): `review` (card), `review-star-1` … `review-star-5`
(`aria-checked`), `review-comment`, `review-submit`, `review-done` (`data-stars`), `review-lead-only` (team members:
the lead reviews for the team). The engineer's rating on `/m/profile`: `profile-rating`.

## Earnings `/m/earnings`

`earnings-page`, `earnings-summary`, `earning-<contractMilestoneId>`, `earning-settlement-<contractMilestoneId>`.

## Me `/m/me`

`me-page`, `me-email`, `me-profile-status`, `me-profile`, `me-earnings`, `me-language`, `me-language-sheet`,
`me-language-zh-CN`, `me-language-en`, `me-language-cancel`, `me-consents`, `consent-<TYPE>` (row),
`consent-toggle-CROSS_BORDER_PROCESSING`, `consent-toggle-SHARE_PROFILE_PSEUDONYMIZED`,
`consent-SHARE_FULL_CV-<projectId>`, `consent-toggle-SHARE_FULL_CV-<projectId>`,
`me-legal-<terms|privacy|cross-border|engineer-declaration>`, `me-export`, `me-delete`, `me-logout`.

Withdrawing a consent, deleting the account and logging out ask for confirmation (`confirm-ok` / `confirm-cancel`).
`ACCOUNT_HAS_ACTIVE_CONTRACTS` is shown in the same dialog with a single `confirm-ok`.

## Not found

`not-found`, `not-found-home`.
