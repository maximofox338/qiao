# Qiao (桥) — MVP technical spec (phase 1)

Single source of truth for backend, web and h5. If something is ambiguous, choose the simplest option that respects the compliance rules below and write it down in `docs/DECISIONS.md`.

## 0. What the product is
A curated project marketplace. Foreign client companies post **projects with milestones** (never jobs). Chinese engineers (in mainland China, no VPN) apply. A Hong Kong company ("the Ltd") sells the service to the client and takes prepayment per milestone (Stripe). A Chinese partner company contracts and pays the engineers in RMB (with tax withheld) using a monthly settlement the platform exports.

Phase 1 runs entirely in Alibaba Cloud Hong Kong. Engineer pages must load from mainland China: **zero external network requests** (no Google Fonts, no CDNs, no analytics SaaS, no reCAPTCHA, no external icon fonts). System font stack only: `-apple-system, BlinkMacSystemFont, "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Segoe UI", Roboto, sans-serif`.

## 1. Compliance rules that shape the code (do not violate)
1. No job postings: a project must have ≥1 milestone with a deliverable and due date, an end date, duration ≤ 12 months. No salary/hours/office fields. Text with employment keywords is **flagged** for manual review (not auto-rejected): `full-time, full time, fulltime, permanent, salary, monthly salary, 9-to-5, office hours, 8 hours a day, 40 hours a week, 全职, 月薪, 工资, 坐班, 朝九晚五, 雇佣, tiempo completo, sueldo, jornada laboral, horario de oficina`.
2. Prohibited categories cannot be selected (API rejects `PROHIBITED_CATEGORY`). Text with sensitive keywords is flagged: `crypto, blockchain, token sale, semiconductor, chip design, lithography, drone, uav, lidar, military, defense, defence, government data, state-owned, survey, mapping, geodata, gis, rare earth, lithium, battery cathode, genome, biometric, research assistant, expert network, data collection, 军, 国防, 无人机, 测绘, 地理信息, 芯片, 半导体, 稀土, 加密货币, 区块链, 调研, 数据采集`.
3. Every project needs client declarations (all true): `noControlledTech`, `noBulkUsSensitiveData`, `noEmployment`, `noProhibitedCategory`.
4. Projects are visible to engineers only after ops approval (status OPEN).
5. Clients see engineers only as a **pseudonymized profile** (alias, headline, skills, years, region, languages, rate, availability, rating). Never email, real name, CV, education or portfolio links unless the engineer granted `SHARE_FULL_CV` consent for that project.
6. Every time a client is shown engineer personal data, write a `transfer_event` (calendar-year counter of unique engineers; thresholds 50k warn, 80k critical-warn, 100k limit).
7. The platform never stores ID numbers, bank cards, faces or real names in phase 1. The partner does KYC; we store only `partnerRef`.
8. Consents are separate and recorded with version, timestamp, IP, user agent: `TERMS`, `PRIVACY`, `CROSS_BORDER_PROCESSING` (engineers, data processed in Hong Kong), `SHARE_PROFILE_PSEUDONYMIZED` (required to apply), `SHARE_FULL_CV` (per project, `scopeRef = projectId`).
9. Account deletion: refused while a contract is ACTIVE; otherwise PII is anonymized and transactional records are kept (3-year retention).
10. Audit log for: logins, admin actions, reviews, consent changes, exports, deletions, settlements, partner API calls.

## 2. Stack and layout
- `backend/` Java 21, Spring Boot 3.5.x, MyBatis-Plus (`mybatis-plus-spring-boot3-starter`), Flyway, MySQL 8.4, Spring Security with stateless JWT (HS256, `com.auth0:java-jwt`), Bean Validation, Stripe Java SDK, Aliyun OSS SDK (adapter only), springdoc-openapi. Package `com.qiao`. Modular monolith packages: `account, profile, client, project, contract, delivery, billing, compliance, moderation, notify, admin, common`. Each module owns its tables; no cross-module SQL joins (call the other module's service).
- `web/` Vue 3 + TypeScript + Vite + Element Plus + Pinia + vue-router + vue-i18n. Client portal (en, es) and admin back-office (es, en). Dev server :5173, proxy `/api` → `http://localhost:8080`.
- `h5/` Vue 3 + TypeScript + Vite + Vant 4 + Pinia + vue-router + vue-i18n. Engineer mobile app, default `zh-CN`, also `en`. Vite `base: '/m/'`, dev server :5174, proxy `/api`.
- `deploy/` docker-compose (nginx, backend, mysql, redis), nginx serves web at `/`, h5 at `/m/`, proxies `/api`.
- `e2e/` Playwright end-to-end tests.

## 3. API conventions
- Base path `/api`. JSON. Auth header `Authorization: Bearer <token>`.
- Envelope for every JSON response: `{ "code": "OK" | "<ERROR_CODE>", "message": "<english fallback>", "data": <payload|null> }`. HTTP status still meaningful: 200, 400 validation, 401 `UNAUTHORIZED`, 403 `FORBIDDEN`, 404 `NOT_FOUND`, 409 state conflict, 429 `RATE_LIMITED`.
- Validation error: code `VALIDATION_ERROR`, `data: { fields: { "<field>": "<code>" } }`.
- Pagination: query `page` (1-based), `size` (default 20, max 100) → `data: { records: [], total, page, size }`.
- Dates `YYYY-MM-DD`; timestamps ISO-8601 UTC. Money: numbers in USD with 2 decimals (`BigDecimal` server side). Field names camelCase.
- Frontends translate by `code`; `message` is only a fallback.

## 4. Roles and auth
- User roles: `CLIENT`, `ENGINEER`. Admin roles (multiple per admin): `SUPERADMIN`, `OPS`, `FINANCE`, `COMPLIANCE`. Admins are a separate table and login.
- Passwordless OTP for users: 6-digit code, expires 10 min, max 5 wrong attempts then 15-min lock, max 1 request/min per email and 20/hour per IP.
- Admin login: email + password (≥12 chars, bcrypt), lock after 5 failures for 15 min, optional TOTP (if enabled for that admin; enrolment and replay protection in §14).
- JWT expiry 7 days, claim `typ` = `user` | `admin`, `role`/`roles`, `sub` = id. Logout adds `jti` to a denylist table.
- Bootstrap admin from env `ADMIN_BOOTSTRAP_EMAIL` / `ADMIN_BOOTSTRAP_PASSWORD` (SUPERADMIN) on startup if no admin exists.
- Mail: `MAIL_MODE=log` writes to table `mail_outbox` (and logs); `smtp` sends via SMTP (Alibaba DirectMail). In profiles `dev` and `test` only: `GET /api/dev/last-otp?email=` returns the last code (for e2e).

## 5. Data model (MySQL, Flyway `V1__init.sql`, utf8mb4)
All tables: `id BIGINT AUTO_INCREMENT PK`, `created_at`, `updated_at` (DATETIME(3) UTC) unless noted.
- `app_user(email UNIQUE, role, status[ACTIVE|LOCKED|DELETED], locale, otp_locked_until, last_login_at)`
- `otp_code(email, code_hash, purpose, expires_at, attempts, used_at, request_ip)`
- `admin_user(email UNIQUE, password_hash, roles CSV, totp_secret NULL, totp_pending_secret NULL, totp_last_step NULL, failed_attempts, locked_until, status)` (TOTP columns: §14)
- `jwt_denylist(jti UNIQUE, expires_at)`
- `consent(user_id, type, version, granted BOOL, scope_ref NULL, ip, user_agent)` — append-only; current state = latest row per (user, type, scope).
- `engineer_profile(user_id UNIQUE, alias UNIQUE e.g. "QE-7K2P", headline, bio, skills JSON, years_experience, region (province-level), english_level[NONE|BASIC|GOOD|FLUENT], spanish_level, hourly_rate_usd, availability_hours_week, cv_text TEXT, education TEXT, portfolio_links JSON, declaration_no_restrictions BOOL, declaration_at, status[DRAFT|PENDING_REVIEW|APPROVED|REJECTED], review_note, partner_ref NULL, rating_avg, rating_count)`
- `client_company(name, country (ISO-2), website, size, status[DRAFT|PENDING_REVIEW|APPROVED|REJECTED|BLOCKED], sanctions_status[PENDING|CLEAR|HIT], sanctions_checked_at, declarations_version, declarations_hash, declarations_at, review_note)`; `client_member(company_id, user_id UNIQUE)`
- `category(code UNIQUE, name_en, name_es, name_zh, allowed BOOL, sort)` — seed in migration (§9).
- `project(company_id, title, description TEXT, category_code, skills JSON, budget_usd, start_date, end_date, status[DRAFT|SUBMITTED|CHANGES_REQUESTED|REJECTED|OPEN|IN_CONTRACT|COMPLETED|CANCELLED], flags JSON (keyword hits), declarations JSON, review_note, reviewed_by, reviewed_at)`
- `project_milestone(project_id, seq, title, deliverable TEXT, due_date, amount_usd)` — client-side budget split; sum must equal `budget_usd`.
- `application(project_id, engineer_user_id, cover_note TEXT, prices JSON [{milestoneId, talentAmountUsd}], talent_total_usd, client_total_usd, status[SUBMITTED|SHORTLISTED|SELECTED|HIRED|DECLINED|WITHDRAWN])` — UNIQUE(project_id, engineer_user_id).
- `contract(project_id, application_id, company_id, engineer_user_id, status[PENDING_SIGNATURES|ACTIVE|COMPLETED|TERMINATED], fee_pct, talent_total_usd, client_total_usd, activated_at, completed_at)`
- `contract_document(contract_id, kind[CLIENT_SERVICES|ENGINEER_DELIVERABLES], template_version, locale, body TEXT, sha256, accepted_by_user_id NULL, accepted_at NULL, accepted_ip NULL)`
- `contract_milestone(contract_id, seq, title, deliverable, due_date, talent_amount_usd, client_amount_usd, status[PENDING_FUNDING|FUNDED|SUBMITTED|REVISION_REQUESTED|ACCEPTED|DISPUTED|REFUNDED], submitted_note, submitted_at, accepted_at, dispute_reason, settled_line_id NULL)`
- `payment(contract_milestone_id, provider[FAKE|STRIPE], provider_ref, amount_usd, status[PENDING|PAID|FAILED|REFUNDED], paid_at)`
- `message(contract_id, sender_user_id, body TEXT, flagged BOOL)`
- `attachment(contract_id, milestone_id NULL, uploader_user_id, filename, content_type, size_bytes, storage_key, sha256)`
- `settlement_batch(period CHAR(7), status[DRAFT|EXPORTED|PAID], created_by_admin_id, exported_at)`
- `settlement_line(batch_id, contract_milestone_id UNIQUE, engineer_user_id, partner_ref, alias, talent_amount_usd, status[PENDING|PAID], partner_payment_ref, iit_withheld_cny, net_paid_cny, paid_at)`
- `transfer_event(year SMALLINT, engineer_user_id, company_id, data_kind[PSEUDONYMIZED_PROFILE|FULL_CV], created_at)` — index (year, engineer_user_id).
- `audit_log(actor_type[USER|ADMIN|PARTNER|SYSTEM], actor_id, action, entity_type, entity_id, detail JSON, ip, created_at)`
- `mail_outbox(to_email, subject, body TEXT, status[LOGGED|SENT|FAILED])`
- `review(contract_id, from_user_id, to_user_id, from_side[CLIENT|ENGINEER], stars 1-5, comment)` — one per side and contract (§13).

## 6. Fees and money
- `PLATFORM_FEE_PCT` (default 25). Engineer proposes `talentAmountUsd` per milestone. Client price per milestone = talent × (1 + fee/100), rounded half-up to cents. Project `budgetUsd` is the client's indicative total; engineers see `talentBudgetUsd = budgetUsd / (1 + fee/100)`.

## 7. Endpoints
### Public
- `GET /api/public/categories` → `[{code, name:{en,es,zh}, allowed}]`
- `GET /api/public/legal/{doc}?locale=` doc ∈ `terms, privacy, client-declarations, engineer-declaration, cross-border` → `{doc, version, locale, title, body}` (markdown texts in `resources/legal/{doc}.{locale}.md`, locales en, es, zh-CN; fall back to en).
- `GET /api/public/config` → `{feePct, paymentProvider, locales}`
- `GET /api/health` → `{status:"UP"}`

### Auth (users)
- `POST /api/auth/otp/request {email, locale}` → `{sent:true}`
- `POST /api/auth/otp/verify {email, code, role?, locale?, acceptTerms?, termsVersion?, privacyVersion?}` → `{token, user}`. New user requires `role` (`AUTH_ROLE_REQUIRED`) and `acceptTerms=true` (`TERMS_REQUIRED`); records TERMS and PRIVACY consents. Errors: `OTP_INVALID`, `OTP_EXPIRED`, `OTP_LOCKED`.
- `POST /api/auth/logout`
- `GET /api/me` → `{id, email, role, status, locale, engineerProfileStatus?, companyStatus?, consents:{TYPE: bool}}`
- `PUT /api/me {locale}`
- `GET /api/me/consents` → list of latest per type/scope; `POST /api/me/consents {type, granted, scopeRef?}`
- `GET /api/me/export` → JSON of all own data (attachment download)
- `POST /api/me/delete` → 409 `ACCOUNT_HAS_ACTIVE_CONTRACTS` or `{deleted:true}`

### Engineer (`ENGINEER`)
- `GET /api/engineer/profile`, `PUT /api/engineer/profile {headline, bio, skills[], yearsExperience, region, englishLevel, spanishLevel, hourlyRateUsd, availabilityHoursWeek, cvText, education, portfolioLinks[], declarationNoRestrictions}`
- `POST /api/engineer/profile/submit` → PENDING_REVIEW (requires `declarationNoRestrictions=true`, `CROSS_BORDER_PROCESSING` consent; error `CONSENT_REQUIRED`).
- `GET /api/engineer/projects?q=&category=&page=&size=` → OPEN projects: `{id, title, description, categoryCode, skills, clientCountry, talentBudgetUsd, startDate, endDate, milestones:[{id, seq, title, deliverable, dueDate, talentAmountUsd}], applied:bool}`
- `GET /api/engineer/projects/{id}`
- `POST /api/engineer/projects/{id}/applications {coverNote, prices:[{milestoneId, talentAmountUsd}]}` — requires profile APPROVED (`PROFILE_NOT_APPROVED`) and `SHARE_PROFILE_PSEUDONYMIZED` consent (`CONSENT_REQUIRED`); one per project (`ALREADY_APPLIED`).
- `GET /api/engineer/applications` → with project title and status
- `POST /api/engineer/applications/{id}/withdraw`
- `POST /api/engineer/applications/{id}/cv-consent {granted}` → records `SHARE_FULL_CV` with `scopeRef=projectId`
- `GET /api/engineer/contracts`, `GET /api/engineer/contracts/{id}` (includes documents and milestones)
- `POST /api/engineer/contracts/{id}/documents/{docId}/accept`
- `POST /api/engineer/milestones/{id}/submit {note, attachmentIds[]}` (from FUNDED or REVISION_REQUESTED; error `MILESTONE_NOT_FUNDED`)
- `GET /api/engineer/earnings` → `[{contractMilestoneId, projectTitle, talentAmountUsd, milestoneStatus, settlementStatus: NONE|PENDING|PAID, paidAt}]`

### Client (`CLIENT`)
- `GET /api/client/company`, `PUT /api/client/company {name, country, website, size}`
- `POST /api/client/company/declarations {version, accepted:true}` → company PENDING_REVIEW, stores sha256 of the declarations text.
- `POST /api/client/projects {title, description, categoryCode, skills[], budgetUsd, startDate, endDate, milestones:[{title, deliverable, dueDate, amountUsd}], declarations:{noControlledTech, noBulkUsSensitiveData, noEmployment, noProhibitedCategory}, submit: bool}` → DRAFT or SUBMITTED. Errors: `COMPANY_NOT_APPROVED` (on submit), `PROHIBITED_CATEGORY`, `MILESTONES_REQUIRED`, `MILESTONE_SUM_MISMATCH`, `END_DATE_REQUIRED`, `DURATION_TOO_LONG`, `DECLARATIONS_REQUIRED`.
- `PUT /api/client/projects/{id}` (only DRAFT or CHANGES_REQUESTED), `POST /api/client/projects/{id}/submit`
- `GET /api/client/projects`, `GET /api/client/projects/{id}` (includes `flags`, `reviewNote`)
- `GET /api/client/projects/{id}/applications` → `[{id, status, coverNote, talentTotalUsd, clientTotalUsd, prices, engineer: <pseudonymized profile>, cvAvailable}]` (+ transfer_event PSEUDONYMIZED_PROFILE per engineer)
- `POST /api/client/applications/{id}/shortlist`, `/decline`, `/select`
- `GET /api/client/applications/{id}/cv` → `{cvText, education, portfolioLinks}` only with consent (`CV_CONSENT_REQUIRED`) (+ transfer_event FULL_CV)
- `POST /api/client/applications/{id}/hire` (from SELECTED) → contract PENDING_SIGNATURES with two documents rendered from templates (`resources/contracts/*.md`, placeholders filled) and contract milestones; project → IN_CONTRACT; other applications → DECLINED.
- `GET /api/client/contracts`, `GET /api/client/contracts/{id}`
- `POST /api/client/contracts/{id}/documents/{docId}/accept` — when both documents accepted → contract ACTIVE.
- `POST /api/client/milestones/{id}/checkout` → `{paymentId, url}` (contract ACTIVE, milestone PENDING_FUNDING)
- `POST /api/client/milestones/{id}/accept`, `/request-revision {reason}`, `/dispute {reason}` (from SUBMITTED). When all milestones ACCEPTED or REFUNDED → contract COMPLETED, project COMPLETED.

### Contract participants (client member of the company or the engineer)
- `GET /api/contracts/{id}/messages`, `POST /api/contracts/{id}/messages {body}` (keyword flags set `flagged`)
- `POST /api/contracts/{id}/attachments` multipart `file` (+ optional `milestoneId`), max 20 MB → `{id, filename, sizeBytes}`
- `GET /api/attachments/{id}` → file download

### Payments
- `PAYMENT_PROVIDER=fake|stripe`. Fake: checkout returns `url = {APP_BASE_URL}/pay/fake/{paymentId}`; `POST /api/payments/fake/{paymentId}/confirm` (client owner, only when provider=fake) marks PAID → milestone FUNDED.
- Stripe: Checkout Session (mode payment, USD, line item "Milestone N – project title"), `success_url={APP_BASE_URL}/client/contracts/{contractId}?paid=1`, webhook `POST /api/payments/stripe/webhook` verifies `STRIPE_WEBHOOK_SECRET`, on `checkout.session.completed` → PAID/FUNDED.
- `GET /api/payments/{id}` → `{id, status, amountUsd}` (owner)

### Partner (header `X-Partner-Key: {PARTNER_API_KEY}`)
- `GET /api/partner/settlements?status=EXPORTED`, `GET /api/partner/settlements/{id}` → batch with lines `{lineId, alias, partnerRef, engineerEmail, talentAmountUsd, contractMilestoneId, projectTitle, status}`
- `POST /api/partner/settlements/{id}/lines/{lineId}/paid {partnerPaymentRef, iitWithheldCny, netPaidCny, paidAt}` → when all lines PAID, batch PAID.

### Admin (`/api/admin/**`)
- `POST /api/admin/auth/login {email, password, totp?}` → `{token, admin:{id, email, roles}}`; `GET /api/admin/me`
- `GET /api/admin/dashboard` → `{companies:{byStatus}, engineers:{byStatus}, projects:{byStatus}, contracts:{byStatus}, gmvUsd, feesUsd, transfers:{year, uniqueEngineers, level}}`
- Companies (OPS, COMPLIANCE): `GET /api/admin/companies?status=`, `GET /api/admin/companies/{id}`, `POST /api/admin/companies/{id}/review {decision: APPROVE|REJECT, sanctionsStatus: CLEAR|HIT, note}` (APPROVE requires CLEAR)
- Engineers (OPS): `GET /api/admin/engineers?status=`, `GET /api/admin/engineers/{id}` (full profile + email), `POST /api/admin/engineers/{id}/review {decision: APPROVE|REJECT, note, partnerRef}` (APPROVE requires partnerRef)
- Projects (OPS): `GET /api/admin/projects?status=`, `GET /api/admin/projects/{id}`, `POST /api/admin/projects/{id}/review {decision: APPROVE|REJECT|REQUEST_CHANGES, note}` (APPROVE → OPEN; rejects if category not allowed)
- Categories (SUPERADMIN, COMPLIANCE): `GET /api/admin/categories`, `PUT /api/admin/categories/{code} {allowed, nameEn, nameEs, nameZh}`
- Disputes (OPS): `GET /api/admin/disputes`, `POST /api/admin/milestones/{id}/resolve {resolution: ACCEPT|REFUND, note}`
- Settlements (FINANCE): `POST /api/admin/settlements {period}` (lines = ACCEPTED milestones with PAID payment and no settlement line; error `NOTHING_TO_SETTLE`), `GET /api/admin/settlements`, `GET /api/admin/settlements/{id}`, `GET /api/admin/settlements/{id}/export.csv` (columns: line_id, alias, partner_ref, engineer_email, contract_milestone_id, project_title, talent_amount_usd), `POST /api/admin/settlements/{id}/mark-exported`
- Compliance (COMPLIANCE, SUPERADMIN): `GET /api/admin/audit?action=&actorType=&page=`, `GET /api/admin/transfers/summary?year=` → `{year, uniqueEngineers:{PSEUDONYMIZED_PROFILE, FULL_CV, ANY}, thresholds:{warn:50000, critical:80000, limit:100000}, level: OK|WARN|CRITICAL|LIMIT}`
- `GET /api/admin/outbox` (mail log), `GET /api/admin/admins`, `POST /api/admin/admins {email, password, roles[]}` (SUPERADMIN)

## 8. Security
- BCrypt, constant-time compares, rate limits (in-memory Bucket4j or simple sliding window; Redis optional later).
- Strict headers from backend: `X-Content-Type-Options`, `Referrer-Policy`; nginx adds CSP `default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self'; frame-ancestors 'none'` and HSTS.
- Authorization checked in services (ownership), not only by role.
- File uploads: size limit, content-type sniff, stored under random keys, served with `Content-Disposition: attachment`.

## 9. Seed categories
Allowed: `web-dev` Web development / Desarrollo web / 网站开发; `mobile-dev` Mobile apps / Apps móviles / 移动应用开发; `backend-api` Backend & APIs / Backend y APIs / 后端与接口; `data-bi` Data analysis & BI / Análisis de datos y BI / 数据分析与BI; `ml-eng` Machine learning engineering (non-sensitive) / Ingeniería de ML (no sensible) / 机器学习工程（非敏感）; `qa` QA & testing / QA y pruebas / 软件测试; `devops` DevOps & cloud / DevOps y nube / 运维与云; `ui-ux` UI/UX design / Diseño UI/UX / UI/UX设计; `embedded-consumer` Embedded firmware (consumer products) / Firmware embebido (consumo) / 嵌入式固件（消费电子）; `cad-mech` Mechanical CAD / CAD mecánico / 机械CAD; `pcb-consumer` PCB design (consumer) / Diseño de PCB (consumo) / PCB设计（消费电子）; `tech-writing` Technical writing & localization / Redacción técnica y localización / 技术写作与本地化.
Prohibited (`allowed=false`): `crypto` Cryptocurrency & token platforms; `semiconductor` Semiconductor & chip design; `drones` Drones & UAV; `geodata` Mapping, surveying & geodata; `defense` Defense & military; `gov-data` Government or state-owned data; `expert-network` Expert consulting / research assistant / data collection; `critical-minerals` Rare earths, lithium & battery technology; `biometric-genomic` Biometric or genomic data. (Provide es/zh names too.)

## 10. Status machines (summary)
- Company: DRAFT → (declarations) PENDING_REVIEW → APPROVED | REJECTED; APPROVED → BLOCKED (sanctions hit).
- Engineer profile: DRAFT → PENDING_REVIEW → APPROVED | REJECTED (edit after REJECTED → DRAFT).
- Project: DRAFT → SUBMITTED → OPEN | CHANGES_REQUESTED (→ SUBMITTED) | REJECTED; OPEN → IN_CONTRACT → COMPLETED; client can CANCEL from DRAFT/SUBMITTED/OPEN.
- Application: SUBMITTED → SHORTLISTED → SELECTED → HIRED; any → DECLINED/WITHDRAWN before HIRED.
- Contract: PENDING_SIGNATURES → ACTIVE → COMPLETED.
- Milestone: PENDING_FUNDING → FUNDED → SUBMITTED → ACCEPTED | REVISION_REQUESTED (→ SUBMITTED) | DISPUTED → ACCEPTED | REFUNDED.

## 11. Definition of done (all parts)
- Builds from a clean checkout; `mvn -q verify` and `npm run build` pass.
- Engineer h5 build output contains no `http://` or `https://` URLs except the SVG/XML namespaces (`scripts/check-no-external.mjs`).
- All UI strings through i18n; no hard-coded user-facing text.

## 12. Teams (equipos) — phase 1.1
Clients can hire a **team** of engineers for a project. A team is only a way to apply and deliver together: it is not a legal entity, it has no bank account and it is never the counterparty of a contract. **Every member stays an individual contractor** with their own profile, consents, partner reference, deliverables agreement and settlement line. Individual applications and contracts keep working exactly as in §7.

### 12.1 Rules
1. **Who.** Only engineers with an `APPROVED` profile can create a team, be invited or accept an invitation. The creator is the team **lead**. A team has 2–10 active members (lead included) to apply; at most 10 members (active + invited).
2. **Invitations by alias.** The lead invites by alias (`QE-XXXX`). Unknown, non-approved or deleted aliases all answer `ENGINEER_NOT_FOUND` (no existence probing). The invitee accepts or declines in the h5. A member can leave; the lead can remove a member; the lead can dissolve the team. Leaving, removal and dissolution are refused (`TEAM_HAS_ACTIVE_WORK`) while the member (or, for dissolution, the team) is part of an application in `SUBMITTED/SHORTLISTED/SELECTED` or a contract `PENDING_SIGNATURES/ACTIVE`.
3. **What engineers see of each other.** Team members (and invitees, for the team they are invited to) see each other's **alias, headline, skills and role** only. Never email, CV, education, portfolio links or partner reference.
4. **Applying as a team.** Only the lead applies, choosing an `ACTIVE` team, the price per milestone (talent amounts, as §7) and a **split**: one `sharePct` per active member (> 0, two decimals, sum exactly 100). At that moment every member must be `APPROVED` (`PROFILE_NOT_APPROVED`) and have `SHARE_PROFILE_PSEUDONYMIZED` granted (`CONSENT_REQUIRED`, `data.aliases` lists who is missing it). An engineer takes part in at most one application per project, individual or team (`ALREADY_APPLIED`, `data.aliases`). The application stores a **snapshot** of the members and their shares; later changes of the team do not change it.
5. **What the client sees.** A team application is a **team card**: team name and description plus the **pseudonymized profile of every member** (§1.5) with its role. One `transfer_event PSEUDONYMIZED_PROFILE` is written **per member** every time the card is shown (list, shortlist, select, decline). The full CV of a member is available only with **that member's** `SHARE_FULL_CV` for the project (`scopeRef = projectId`) and writes a `FULL_CV` event for that member only. The client never sees the split.
6. **Hire.** Hiring a team application creates **one contract** (`CLIENT_SERVICES` unchanged, signed by the client; the professional named in it is "team «name»: alias, alias, …") and **one `ENGINEER_DELIVERABLES` document per member** (template `ENGINEER_DELIVERABLES_TEAM`), each with that member's alias, partner reference, share and own amounts. Each member accepts **their own** document; the contract becomes `ACTIVE` when the client and **all** members have accepted. Members are re-checked as `APPROVED` at hire.
7. **Delivery.** The lead submits milestones (members: `TEAM_LEAD_ONLY`); any member can post messages and upload files; messages and files are visible to all members and the client. Engineers still see the client only by country, never the client price.
8. **Money.** Client price per milestone = talent × (1 + fee) as §6, on the team's talent amount. Each member's amount per milestone = talent × sharePct / 100 rounded **down** to the cent; the lead receives the remaining cents, so the member amounts always add up to the milestone's talent amount. The monthly settlement creates **one line per member** per accepted milestone (`alias`, `partnerRef`, member amount); the partner API and CSV therefore list members separately. Earnings show each member's own amount.
9. **Privacy inside the team.** A member's views of contracts, applications and teams never include another member's email, CV or partner reference, nor another member's deliverables agreement.

### 12.2 Data model (`V2__teams.sql`)
- `team(name, description, lead_user_id, status[ACTIVE|DISSOLVED])` — module `team`.
- `team_member(team_id, user_id, role[LEAD|MEMBER], status[INVITED|ACTIVE|DECLINED|LEFT|REMOVED], invited_by_user_id, responded_at)` — UNIQUE(team_id, user_id).
- `application.team_id NULL`; `application_member(application_id, project_id, engineer_user_id, role, share_pct)` — UNIQUE(project_id, engineer_user_id). For a team application `application.engineer_user_id` is the lead.
- `contract.team_id NULL`, `contract.team_name NULL`; `contract_member(contract_id, engineer_user_id, role, share_pct)`; `contract_document.engineer_user_id NULL` (owner of an `ENGINEER_DELIVERABLES` document; unique per contract, kind and owner).
- `settlement_line`: unique (contract_milestone_id, engineer_user_id) instead of contract_milestone_id.

### 12.3 Endpoints (engineer)
- `POST /api/engineer/teams {name, description}` → team (caller = lead). `GET /api/engineer/teams` → teams where the caller is an active member. `GET /api/engineer/teams/{id}`, `PUT /api/engineer/teams/{id} {name, description}` (lead).
- Team view: `{id, name, description, status, leadAlias, myRole, myStatus, members:[{id, alias, headline, skills, role, status}], activeMembers, canApply, createdAt}` (members: ACTIVE and INVITED; the lead also sees DECLINED ones).
- `POST /api/engineer/teams/{id}/invitations {alias}` (lead) → member row INVITED. Errors: `ENGINEER_NOT_FOUND` 404, `ALREADY_TEAM_MEMBER` 409, `TEAM_FULL` 409.
- `GET /api/engineer/team-invitations` → `[{teamId, teamName, description, leadAlias, memberAliases, invitedAt}]`; `POST /api/engineer/teams/{id}/invitations/accept`, `/decline`.
- `POST /api/engineer/teams/{id}/members/{memberId}/remove` (lead), `POST /api/engineer/teams/{id}/leave` (member), `POST /api/engineer/teams/{id}/dissolve` (lead).
- `POST /api/engineer/projects/{id}/team-applications {teamId, coverNote, prices:[{milestoneId, talentAmountUsd}], shares:[{alias, sharePct}]}` (lead). Errors as §7 apply plus `TEAM_LEAD_ONLY` 403, `TEAM_TOO_SMALL` 409, `SHARES_SUM_MISMATCH` 400 and `VALIDATION_ERROR` on `shares` (members missing/duplicated/unknown).
- `GET /api/engineer/applications` also returns the team applications where the caller is a member; team items add `team:{id, name, myRole, mySharePct, members:[{alias, role, sharePct}]}`. Only the lead can withdraw; every member records their own `cv-consent` on it.
- `GET /api/engineer/contracts[/{id}]`: also contracts where the caller is a member. Team contracts add `team:{id, name, myRole, mySharePct, myTalentTotalUsd, members:[{alias, role, sharePct, accepted}]}`, milestones add `myAmountUsd`; `documents` holds only the caller's own document. `engineerAcceptedAt` is set when every member accepted.

### 12.4 Endpoints (client)
- `GET /api/client/projects/{id}/applications`: team items add `team:{name, description, members:[{alias, role, profile:<pseudonymized>, cvAvailable}]}`; `engineer` is the lead's pseudonymized profile and `cvAvailable` the lead's.
- `GET /api/client/applications/{id}/cv?alias=QE-XXXX` → that member's CV (alias required for team applications; `CV_CONSENT_REQUIRED` per member).
- Contract views add `team:{name, members:[{alias, role, accepted, acceptedAt}]}` (no shares). `engineerAcceptedAt` = when the last member accepted.

## 13. Reviews — phase 1.1
1. Once a contract is `COMPLETED` each side can leave **one** review: `{stars: 1–5, comment ≤ 1000 chars (optional)}`. Client side: any member of the client company (one review for the company). Engineer side: the engineer, or the **lead** of a team contract (members get `TEAM_LEAD_ONLY`).
2. The client's stars update the engineer's `rating_avg` (running average, 2 decimals) and `rating_count` — for a team contract, those of **every member**. They are part of the pseudonymized profile (`ratingAvg`, `ratingCount`) that clients already see, and of the engineer's own profile.
3. Review **texts** (both directions, including reviews about clients) are visible to the back-office only for now.
4. Endpoints: `POST /api/client/contracts/{id}/review {stars, comment}` and `POST /api/engineer/contracts/{id}/review {stars, comment}` → `{stars, comment, createdAt}`; errors `CONTRACT_NOT_COMPLETED` 409, `ALREADY_REVIEWED` 409, `VALIDATION_ERROR` (stars outside 1–5). Contract detail views add `myReview` (the viewer side's review, null until written; in a team the lead's review is shown to every member) and `canReview`. Admin: `GET /api/admin/reviews?side=CLIENT|ENGINEER&page=&size=` (OPS, COMPLIANCE) → page of `{id, contractId, projectId, projectTitle, fromSide, fromLabel, toLabel, stars, comment, createdAt}` (labels: company name / engineer alias or team aliases).
5. Data: `V3__reviews.sql` adds `review.from_side` and UNIQUE(contract_id, from_side). Account deletion clears the comments the user wrote; the stars stay (they feed ratings). Audit: `REVIEW_CREATED`.

## 14. Admin two-factor authentication (TOTP) — phase 1.1
1. RFC 6238: HMAC-SHA1, 6 digits, 30-second steps, ±1 step of drift. Secrets are 160 random bits, base32, generated server-side.
2. Self-service for any admin (`/admin/profile` in the back-office): `POST /api/admin/me/totp/setup` → `{secret, otpauthUri, issuer, account}` (stored as a **pending** secret, replacing an unconfirmed one; `TOTP_ALREADY_ENABLED` 409 when active). The back-office renders `otpauthUri` as a QR code in the browser with a bundled library — no external service. `POST /api/admin/me/totp/enable {code}` activates the pending secret when the code is valid (`TOTP_SETUP_REQUIRED` 409, `TOTP_CODE_INVALID` 400). `POST /api/admin/me/totp/disable {code}` needs a valid code (`TOTP_NOT_ENABLED` 409, `TOTP_CODE_INVALID` 400). Enrolment errors are 400/409, never 401, so the apps do not mistake them for an expired session. Code attempts on these endpoints: 10 per minute per admin.
3. `POST /api/admin/admins/{id}/totp/reset` (SUPERADMIN) removes another admin's TOTP (lost phone); they enrol again from their profile.
4. **No replay**: `admin_user.totp_last_step` stores the time-step of the last accepted code; a code is accepted only for a **later** step. At login a reused code answers `TOTP_INVALID` and counts as a failed attempt (audit reason `REPLAYED_TOTP`).
5. `GET /api/admin/me`, the login response `admin` and `GET /api/admin/admins` include `totpEnabled`. Data: `V4__admin_totp.sql`. Audit: `ADMIN_TOTP_ENABLED`, `ADMIN_TOTP_DISABLED`, `ADMIN_TOTP_RESET`.

## 15. Stripe test mode without real keys
- `STRIPE_API_BASE` (empty = `https://api.stripe.com`) points the Stripe SDK at another host; it exists to test against the official `stripe/stripe-mock` image and production refuses any other value at startup.
- `StripePaymentIT` runs with `PAYMENT_PROVIDER=stripe` against `stripe/stripe-mock` (Testcontainers): checkout session creation, a correctly signed `checkout.session.completed` webhook (signature computed as Stripe documents: `t=<unix>,v1=hex(HMAC-SHA256(secret, t + "." + payload))`) that marks the payment PAID and the milestone FUNDED, idempotent retries, `async_payment_succeeded`, non-ASCII payloads, badly signed / tampered / expired / unsigned events (`400 INVALID_SIGNATURE`) and a refund after a dispute resolved as `REFUND`.
