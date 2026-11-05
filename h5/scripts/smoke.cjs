#!/usr/bin/env node
// Qiao H5 — smoke test with Playwright against `vite preview` and a mocked API (no backend needed).
//
//   npm run build && node scripts/smoke.cjs [screenshotDir]
//
// - Phone viewport (iPhone 13 descriptor on Chromium).
// - Every request to a host other than localhost is BLOCKED and recorded: the run fails if there is any.
// - `vite preview` sends the production CSP (see vite.config.ts), so CSP violations show up as console errors.
// - Console errors / page errors fail the run.
// Requires the `playwright` package (resolved from NODE_PATH / a global install).
/* eslint-disable no-console */
const { spawn } = require('node:child_process')
const path = require('node:path')
const fs = require('node:fs')
const { chromium, devices } = require('playwright')

const PORT = 4174
const BASE = `http://localhost:${PORT}/m/`
const OUT = path.resolve(process.argv[2] || path.join(__dirname, '..', 'test-results', 'smoke'))
fs.mkdirSync(OUT, { recursive: true })

// ---------------------------------------------------------------- mock backend state
const state = {
  consents: {
    TERMS: true,
    PRIVACY: true,
    CROSS_BORDER_PROCESSING: false,
    SHARE_PROFILE_PSEUDONYMIZED: false,
  },
  cvConsents: {},
  profileStatus: 'DRAFT',
  applied: {},
  docAccepted: false,
  milestoneSubmitted: false,
  messages: [
    { id: 1, senderUserId: 900, body: 'Hi! Welcome aboard. Milestone 1 is funded, feel free to start.', flagged: false, createdAt: '2026-11-02T08:10:00Z' },
    { id: 2, senderUserId: 101, body: '收到，谢谢！我今天开始搭建项目结构，周五前给您第一版。', flagged: false, createdAt: '2026-11-02T09:02:00Z' },
    { id: 3, senderUserId: 900, body: 'Great. Please share the staging link in the delivery note.', flagged: false, createdAt: '2026-11-03T15:40:00Z' },
  ],
}
const calls = []

const me = () => ({
  id: 101,
  email: 'wang.lei@example.cn',
  role: 'ENGINEER',
  status: 'ACTIVE',
  locale: 'zh-CN',
  engineerProfileStatus: state.profileStatus,
  consents: { ...state.consents },
})

const CATEGORIES = [
  ['web-dev', 'Web development', 'Desarrollo web', '网站开发', true],
  ['mobile-dev', 'Mobile apps', 'Apps móviles', '移动应用开发', true],
  ['backend-api', 'Backend & APIs', 'Backend y APIs', '后端与接口', true],
  ['data-bi', 'Data analysis & BI', 'Análisis de datos y BI', '数据分析与BI', true],
  ['ml-eng', 'Machine learning engineering (non-sensitive)', 'Ingeniería de ML (no sensible)', '机器学习工程（非敏感）', true],
  ['qa', 'QA & testing', 'QA y pruebas', '软件测试', true],
  ['devops', 'DevOps & cloud', 'DevOps y nube', '运维与云', true],
  ['ui-ux', 'UI/UX design', 'Diseño UI/UX', 'UI/UX设计', true],
  ['embedded-consumer', 'Embedded firmware (consumer products)', 'Firmware embebido (consumo)', '嵌入式固件（消费电子）', true],
  ['crypto', 'Cryptocurrency & token platforms', 'Criptomonedas', '加密货币与代币平台', false],
].map(([code, en, es, zh, allowed]) => ({ code, name: { en, es, zh }, allowed }))

const PROJECTS = [
  {
    id: 11,
    title: 'Rebuild admin dashboard for a cross-border e-commerce store (Vue 3)',
    description:
      'We run a Shopify-based store selling home goods in the US. We need our internal admin dashboard rebuilt in Vue 3 + TypeScript: order list with filters, refunds workflow, and a simple inventory report.\n\nAPIs already exist (REST, documented in OpenAPI).',
    categoryCode: 'web-dev',
    skills: ['Vue 3', 'TypeScript', 'Vite', 'REST'],
    clientCountry: 'US',
    talentBudgetUsd: 4800,
    startDate: '2026-11-15',
    endDate: '2027-02-28',
    applied: false,
    milestones: [
      { id: 111, seq: 1, title: 'Project setup & order list', deliverable: 'Repo with CI, login, order list with filters and pagination, deployed to staging.', dueDate: '2026-12-05', talentAmountUsd: 1600 },
      { id: 112, seq: 2, title: 'Refunds workflow', deliverable: 'Refund request/approve flow with audit trail, unit tests.', dueDate: '2027-01-15', talentAmountUsd: 1800 },
      { id: 113, seq: 3, title: 'Inventory report & handover', deliverable: 'Inventory report with CSV export, handover docs and a 1-hour walkthrough call.', dueDate: '2027-02-20', talentAmountUsd: 1400 },
    ],
  },
  {
    id: 12,
    title: 'App de reservas para clínicas dentales (Flutter)',
    description: 'Necesitamos una app de reservas para 3 clínicas en Ciudad de México: calendario, recordatorios y pagos con tarjeta.',
    categoryCode: 'mobile-dev',
    skills: ['Flutter', 'Firebase', 'Dart'],
    clientCountry: 'MX',
    talentBudgetUsd: 6400,
    startDate: '2026-12-01',
    endDate: '2027-04-30',
    applied: true,
    milestones: [
      { id: 121, seq: 1, title: 'Diseño y calendario', deliverable: 'Pantallas principales y calendario funcional.', dueDate: '2027-01-10', talentAmountUsd: 3200 },
      { id: 122, seq: 2, title: 'Pagos y publicación', deliverable: 'Pagos y publicación en tiendas.', dueDate: '2027-04-15', talentAmountUsd: 3200 },
    ],
  },
  {
    id: 13,
    title: 'Firmware update for a Bluetooth speaker (nRF52)',
    description: 'Add an OTA update flow and fix audio dropouts on our consumer Bluetooth speaker.',
    categoryCode: 'embedded-consumer',
    skills: ['C', 'nRF52', 'BLE', 'Zephyr'],
    clientCountry: 'DE',
    talentBudgetUsd: 3200,
    startDate: '2026-11-20',
    endDate: '2027-01-31',
    applied: false,
    milestones: [{ id: 131, seq: 1, title: 'OTA + dropout fix', deliverable: 'Firmware build, test report.', dueDate: '2027-01-20', talentAmountUsd: 3200 }],
  },
]

const LEGAL = {
  terms: { title: '用户协议', body: '# 用户协议\n\n欢迎使用 Qiao。\n\n1. 平台提供**项目撮合**服务，不构成雇佣关系。\n2. 你应如实填写资料。\n\n<script>alert(1)</script> 应显示为文本。' },
  privacy: { title: '隐私政策', body: '# 隐私政策\n\n我们仅收集提供服务所必需的信息。\n\n- 邮箱\n- 个人资料\n\n[返回首页](/m/)' },
  'cross-border': { title: '个人信息跨境处理说明', body: '# 跨境处理说明\n\n你的个人信息将存储于**中国香港**（阿里云香港）。' },
  'engineer-declaration': {
    title: '工程师声明',
    body: '本人声明：\n\n1. 本人**不属于**涉密人员，也不处于脱密期；\n2. 本人与现任或前任雇主之间不存在与本平台项目相冲突的竞业限制或保密义务；\n3. 本人不会在项目中使用或披露任何国家秘密、受出口管制的技术或他人的商业秘密。',
  },
}

const PROFILE = () => ({
  alias: 'QE-7K2P',
  headline: '8 年经验的全栈工程师，擅长 Vue 和 Spring Boot',
  bio: '曾为多家出海企业交付后台管理系统与支付对接。',
  skills: ['Vue', 'TypeScript', 'Spring Boot'],
  yearsExperience: 8,
  region: 'Guangdong',
  englishLevel: 'GOOD',
  spanishLevel: 'NONE',
  hourlyRateUsd: 35,
  availabilityHoursWeek: 25,
  cvText: '2018–至今：某跨境电商公司 前端负责人',
  education: '华南理工大学 计算机科学与技术 本科',
  portfolioLinks: ['github.com/wanglei-dev'],
  declarationNoRestrictions: false,
  status: state.profileStatus,
  reviewNote: state.profileStatus === 'REJECTED' ? '请补充项目经验' : null,
})

const contractDetail = () => ({
  id: 31,
  projectId: 11,
  projectTitle: 'Rebuild admin dashboard for a cross-border e-commerce store (Vue 3)',
  status: 'ACTIVE',
  talentTotalUsd: 4800,
  activatedAt: '2026-11-01T10:00:00Z',
  documents: [
    {
      id: 41,
      kind: 'ENGINEER_DELIVERABLES',
      templateVersion: '2026-10',
      locale: 'zh-CN',
      sha256: 'a3f1c09be4d27d55e0d7c1c9b0d8f9a7d0e2b4c6a8f0e1d3c5b7a9f1e3d5c7b9',
      body: '# 工程师交付协议\n\n**合作方式：** 本协议为项目制交付协议，不构成劳动关系。\n\n## 里程碑\n\n| 序号 | 交付物 | 金额 |\n|---|---|---|\n| 1 | 项目搭建与订单列表 | US$1,600 |\n| 2 | 退款流程 | US$1,800 |\n\n## 付款\n\n报酬由合作公司按月以人民币支付，并依法代扣个人所得税。\n\n![tracking](https://evil.example/pixel.png)\n[恶意链接](javascript:alert(1))',
      acceptedAt: state.docAccepted ? '2026-11-05T02:00:00Z' : null,
    },
    { id: 42, kind: 'CLIENT_SERVICES', templateVersion: '2026-10', locale: 'en', body: '# Client services agreement\n\nAccepted by the client.', acceptedAt: '2026-11-01T09:00:00Z' },
  ],
  milestones: [
    { id: 51, seq: 1, title: 'Project setup & order list', deliverable: 'Repo with CI, login, order list with filters and pagination, deployed to staging.', dueDate: '2026-12-05', talentAmountUsd: 1600, status: state.milestoneSubmitted ? 'SUBMITTED' : 'FUNDED', submittedNote: state.milestoneSubmitted ? 'Staging deployed.' : null },
    { id: 52, seq: 2, title: 'Refunds workflow', deliverable: 'Refund request/approve flow with audit trail, unit tests.', dueDate: '2027-01-15', talentAmountUsd: 1800, status: 'REVISION_REQUESTED', revisionReason: 'Please add the partial-refund case.' },
    { id: 53, seq: 3, title: 'Inventory report & handover', deliverable: 'Inventory report with CSV export.', dueDate: '2027-02-20', talentAmountUsd: 1400, status: 'PENDING_FUNDING' },
  ],
})

// ---------------------------------------------------------------- API mock
function ok(data) {
  return { status: 200, body: { code: 'OK', message: 'OK', data } }
}
function fail(status, code, data = null) {
  return { status, body: { code, message: code, data } }
}

function handleApi(method, pathname, query, body) {
  const m = (re) => re.exec(pathname)
  calls.push({ method, pathname, body, query: query.toString() })
  if (pathname === '/api/public/categories') return ok(CATEGORIES)
  if (pathname === '/api/public/config') return ok({ feePct: 25, paymentProvider: 'fake', locales: ['en', 'es', 'zh-CN'] })
  let r
  if ((r = m(/^\/api\/public\/legal\/([\w-]+)$/))) {
    const doc = LEGAL[r[1]]
    if (!doc) return fail(404, 'NOT_FOUND')
    return ok({ doc: r[1], version: '1.0', locale: query.get('locale') || 'zh-CN', title: doc.title, body: doc.body })
  }
  if (pathname === '/api/auth/otp/request') return ok({ sent: true })
  if (pathname === '/api/auth/otp/verify') {
    if (body.code !== '123456') return fail(400, 'OTP_INVALID')
    if (!body.acceptTerms) return fail(400, 'TERMS_REQUIRED')
    return ok({ token: 'mock-token', user: me() })
  }
  if (pathname === '/api/auth/logout') return ok(null)
  if (pathname === '/api/me' && method === 'GET') return ok(me())
  if (pathname === '/api/me' && method === 'PUT') return ok(null)
  if (pathname === '/api/me/consents' && method === 'GET') {
    const list = Object.entries(state.consents).map(([type, granted]) => ({ type, granted, version: '1.0', scopeRef: null, createdAt: '2026-11-01T08:00:00Z' }))
    for (const [ref, granted] of Object.entries(state.cvConsents)) list.push({ type: 'SHARE_FULL_CV', granted, version: '1.0', scopeRef: ref, createdAt: '2026-11-04T08:00:00Z' })
    return ok(list)
  }
  if (pathname === '/api/me/consents' && method === 'POST') {
    if (body.scopeRef) state.cvConsents[body.scopeRef] = body.granted
    else state.consents[body.type] = body.granted
    return ok(null)
  }
  if (pathname === '/api/me/delete') return fail(409, 'ACCOUNT_HAS_ACTIVE_CONTRACTS')
  if (pathname === '/api/engineer/profile' && method === 'GET') return ok(PROFILE())
  if (pathname === '/api/engineer/profile' && method === 'PUT') return ok({ ...PROFILE(), ...body })
  if (pathname === '/api/engineer/profile/submit') {
    if (!state.consents.CROSS_BORDER_PROCESSING) return fail(409, 'CONSENT_REQUIRED')
    state.profileStatus = 'PENDING_REVIEW'
    return ok({ ...PROFILE(), status: 'PENDING_REVIEW' })
  }
  if (pathname === '/api/engineer/projects') {
    const q = (query.get('q') || '').toLowerCase()
    const cat = query.get('category') || ''
    const records = PROJECTS.filter((p) => (!q || p.title.toLowerCase().includes(q)) && (!cat || p.categoryCode === cat)).map((p) => ({ ...p, applied: p.applied || !!state.applied[p.id] }))
    return ok({ records, total: records.length, page: 1, size: 20 })
  }
  if ((r = m(/^\/api\/engineer\/projects\/(\d+)$/))) {
    const p = PROJECTS.find((x) => String(x.id) === r[1])
    return p ? ok({ ...p, applied: p.applied || !!state.applied[p.id] }) : fail(404, 'NOT_FOUND')
  }
  if ((r = m(/^\/api\/engineer\/projects\/(\d+)\/applications$/))) {
    if (state.profileStatus !== 'APPROVED') return fail(409, 'PROFILE_NOT_APPROVED')
    if (!state.consents.SHARE_PROFILE_PSEUDONYMIZED) return fail(409, 'CONSENT_REQUIRED')
    state.applied[r[1]] = true
    return ok({ id: 77, projectId: Number(r[1]), status: 'SUBMITTED' })
  }
  if (pathname === '/api/engineer/applications') {
    return ok([
      { id: 61, projectId: 12, projectTitle: 'App de reservas para clínicas dentales (Flutter)', status: 'SELECTED', coverNote: '我有 4 年 Flutter 经验，做过两款预约类应用，可以按时交付。', talentTotalUsd: 6200, createdAt: '2026-11-03T08:00:00Z' },
      { id: 62, projectId: 14, projectTitle: 'Data pipeline for marketing analytics (dbt)', status: 'SUBMITTED', talentTotalUsd: 2500, createdAt: '2026-11-02T08:00:00Z' },
      { id: 63, projectId: 15, projectTitle: 'Landing page redesign', status: 'DECLINED', talentTotalUsd: 900, createdAt: '2026-10-20T08:00:00Z' },
    ])
  }
  if ((r = m(/^\/api\/engineer\/applications\/(\d+)\/cv-consent$/))) {
    const projectId = r[1] === '61' ? '12' : r[1]
    state.cvConsents[projectId] = !!body.granted
    return ok(null)
  }
  if ((r = m(/^\/api\/engineer\/applications\/(\d+)\/withdraw$/))) return ok(null)
  if (pathname === '/api/engineer/contracts') {
    const c = contractDetail()
    return ok([{ id: c.id, projectId: c.projectId, projectTitle: c.projectTitle, status: c.status, talentTotalUsd: c.talentTotalUsd, activatedAt: c.activatedAt, milestones: c.milestones }])
  }
  if (pathname === '/api/engineer/contracts/31') return ok(contractDetail())
  if (pathname === '/api/engineer/contracts/31/documents/41/accept') {
    state.docAccepted = true
    return ok(null)
  }
  if (pathname === '/api/contracts/31/attachments') return ok({ id: 801, filename: 'delivery.zip', sizeBytes: 1024 })
  if (pathname === '/api/engineer/milestones/51/submit') {
    state.milestoneSubmitted = true
    return ok(null)
  }
  if (pathname === '/api/engineer/milestones/52/submit') return fail(409, 'MILESTONE_NOT_FUNDED')
  if (pathname === '/api/contracts/31/messages' && method === 'GET') return ok(state.messages)
  if (pathname === '/api/contracts/31/messages' && method === 'POST') {
    const msg = { id: state.messages.length + 1, senderUserId: 101, body: body.body, flagged: /wechat|微信/i.test(body.body), createdAt: new Date().toISOString() }
    state.messages.push(msg)
    return ok(msg)
  }
  if (pathname === '/api/engineer/earnings') {
    return ok([
      { contractMilestoneId: 51, projectTitle: 'Rebuild admin dashboard (Vue 3)', talentAmountUsd: 1600, milestoneStatus: 'ACCEPTED', settlementStatus: 'PAID', paidAt: '2026-10-31' },
      { contractMilestoneId: 47, projectTitle: 'Shopify theme fixes', talentAmountUsd: 650, milestoneStatus: 'ACCEPTED', settlementStatus: 'PENDING', paidAt: null },
      { contractMilestoneId: 52, projectTitle: 'Rebuild admin dashboard (Vue 3)', talentAmountUsd: 1800, milestoneStatus: 'SUBMITTED', settlementStatus: 'NONE', paidAt: null },
    ])
  }
  return fail(404, 'NOT_FOUND')
}

// ---------------------------------------------------------------- harness
const external = []
const consoleErrors = []
const expectedApiErrors = []
const failures = []
let shot = 0

function check(cond, msg) {
  if (!cond) failures.push(msg)
  console.log(`${cond ? '  ok ' : '  FAIL'} ${msg}`)
}

async function newContext(browser, { token = null, locale = 'zh-CN' } = {}) {
  const ctx = await browser.newContext({ ...devices['iPhone 13'], locale })
  await ctx.addInitScript(
    ([t, l]) => {
      try {
        if (t) localStorage.setItem('qiao.h5.token', t)
        localStorage.setItem('qiao.h5.locale', l)
      } catch {
        /* ignore */
      }
    },
    [token, locale],
  )
  await ctx.route('**/*', async (route) => {
    const req = route.request()
    const url = new URL(req.url())
    if (url.hostname !== 'localhost' && url.hostname !== '127.0.0.1') {
      external.push(req.url())
      return route.abort('blockedbyclient')
    }
    if (url.pathname.startsWith('/api/')) {
      let body = {}
      const raw = req.postData()
      if (raw && (req.headers()['content-type'] || '').includes('application/json')) body = JSON.parse(raw)
      const res = handleApi(req.method(), url.pathname, url.searchParams, body)
      return route.fulfill({ status: res.status, contentType: 'application/json', body: JSON.stringify(res.body) })
    }
    return route.continue()
  })
  ctx.on('page', (page) => attach(page))
  return ctx
}

function attach(page) {
  page.on('console', (msg) => {
    if (msg.type() !== 'error') return
    const where = msg.location()?.url || ''
    // Chromium logs every 4xx API response as "Failed to load resource"; the mocks return some on purpose
    // (TERMS_REQUIRED, CONSENT_REQUIRED, ACCOUNT_HAS_ACTIVE_CONTRACTS, 401). Only those are tolerated.
    if (/Failed to load resource: the server responded with a status of 4\d\d/.test(msg.text()) && where.includes('/api/')) {
      expectedApiErrors.push(where)
      return
    }
    consoleErrors.push(`${page.url()} :: ${msg.text()} ${where}`)
  })
  page.on('pageerror', (err) => consoleErrors.push(`${page.url()} :: pageerror ${err.message}`))
  page.on('websocket', (ws) => external.push(`websocket ${ws.url()}`))
}

async function snap(page, name, fullPage = true) {
  await page.waitForTimeout(350)
  const file = path.join(OUT, `${String(++shot).padStart(2, '0')}-${name}.png`)
  await page.screenshot({ path: file, fullPage })
  console.log(`  📷 ${file}`)
}

const tid = (id) => `[data-testid="${id}"]`

async function main() {
  const preview = spawn(process.execPath, [path.join(__dirname, '..', 'node_modules', 'vite', 'bin', 'vite.js'), 'preview', '--port', String(PORT), '--strictPort'], {
    cwd: path.join(__dirname, '..'),
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('vite preview did not start')), 20000)
    preview.stdout.on('data', (d) => {
      if (String(d).includes(String(PORT))) {
        clearTimeout(timer)
        resolve()
      }
    })
    preview.on('exit', (code) => reject(new Error(`vite preview exited ${code}`)))
  })

  const browser = await chromium.launch()
  try {
    // ---------------- landing + login (logged out)
    console.log('Landing & login')
    let ctx = await newContext(browser)
    let page = await ctx.newPage()
    await page.goto(BASE)
    await page.waitForSelector(tid('landing-title'))
    check((await page.locator(tid('landing-prohibited')).count()) === 1, 'landing shows prohibited categories')
    await snap(page, 'landing')
    await page.click(tid('landing-login'))
    await page.waitForSelector(tid('login-email'))
    await page.fill(tid('login-email'), 'wang.lei@example.cn')
    await page.click(tid('login-send-code'))
    await page.waitForSelector(tid('login-code'))
    await page.fill(tid('login-code'), '123456')
    check(!(await page.locator(tid('login-accept-terms')).getAttribute('aria-checked')).includes('true'), 'terms checkbox starts unticked')
    check(!(await page.locator(tid('login-accept-crossborder')).getAttribute('aria-checked')).includes('true'), 'cross-border checkbox starts unticked')
    await page.click(tid('login-submit'))
    await page.waitForSelector(tid('login-new-user-notice'))
    check(true, 'new user without terms gets the registration notice (TERMS_REQUIRED)')
    await snap(page, 'login-new-user', false)
    await page.click(tid('login-link-terms'))
    await page.waitForSelector(tid('legal-doc-terms'))
    check((await page.locator('.q-markdown script').count()) === 0, 'legal markdown does not render raw <script>')
    await snap(page, 'legal-terms', false)
    await page.goBack()
    await page.waitForSelector(tid('login-code'))
    check((await page.inputValue(tid('login-code'))) === '123456', 'login form state survives visiting a legal doc')
    await page.click(tid('login-accept-terms'))
    await page.click(tid('login-accept-crossborder'))
    check((await page.locator(tid('login-accept-terms')).getAttribute('aria-checked')) === 'true', 'terms checkbox toggles on click')
    await page.click(tid('login-submit'))
    await page.waitForURL(/\/m\/profile$/)
    const verify = calls.filter((c) => c.pathname === '/api/auth/otp/verify').pop()
    check(verify.body.role === 'ENGINEER' && verify.body.acceptTerms === true && verify.body.termsVersion === '1.0', 'verify sends role=ENGINEER, acceptTerms, termsVersion')
    check(calls.some((c) => c.pathname === '/api/me/consents' && c.method === 'POST' && c.body.type === 'CROSS_BORDER_PROCESSING' && c.body.granted === true), 'cross-border consent recorded after register')
    await ctx.close()

    // ---------------- profile
    console.log('Profile')
    state.consents.CROSS_BORDER_PROCESSING = false
    ctx = await newContext(browser, { token: 'mock-token' })
    page = await ctx.newPage()
    await page.goto(BASE + 'profile')
    await page.waitForSelector(tid('profile-headline'))
    await snap(page, 'profile')
    await page.fill(tid('profile-headline'), '全栈工程师，8 年经验，Vue / Spring Boot')
    await page.fill(tid('profile-skill-input'), 'Docker')
    await page.click(tid('profile-skill-add'))
    await page.click(tid('profile-region'))
    await page.click(tid('profile-region-confirm'))
    await page.click(tid('profile-english-FLUENT'))
    await page.click(tid('profile-declaration'))
    await page.click(tid('profile-submit'))
    await page.waitForTimeout(300)
    check(!calls.some((c) => c.pathname === '/api/engineer/profile/submit'), 'submit blocked client-side without cross-border consent')
    await page.click(tid('profile-grant-crossborder'))
    await page.waitForSelector(tid('profile-crossborder-missing'), { state: 'detached' })
    await page.click(tid('profile-submit'))
    await page.waitForSelector(`${tid('profile-status')}[data-status="PENDING_REVIEW"]`)
    const put = calls.filter((c) => c.pathname === '/api/engineer/profile' && c.method === 'PUT').pop()
    check(put && put.body.skills.includes('Docker') && put.body.englishLevel === 'FLUENT' && put.body.declarationNoRestrictions === true && typeof put.body.yearsExperience === 'number', 'profile PUT body has spec fields')
    check(calls.some((c) => c.pathname === '/api/engineer/profile/submit'), 'profile submitted for review')
    await snap(page, 'profile-pending', false)

    // ---------------- projects + apply
    console.log('Projects & apply')
    state.profileStatus = 'APPROVED'
    await page.goto(BASE + 'projects')
    await page.waitForSelector(tid('project-card-11'))
    await snap(page, 'projects')
    await page.click(tid('projects-category-mobile-dev'))
    await page.waitForSelector(tid('project-card-12'))
    await page.waitForTimeout(200)
    check((await page.locator(tid('project-card-11')).count()) === 0, 'category filter works')
    check(calls.some((c) => c.pathname === '/api/engineer/projects' && c.query.includes('category=mobile-dev') && c.query.includes('page=1')), 'projects list called with ?category=&page=')
    await page.click(tid('projects-category-all'))
    await page.click(tid('project-card-11'))
    await page.waitForSelector(tid('apply-form'))
    await snap(page, 'project-detail')
    await page.fill(tid('apply-cover-note'), '我做过 3 个类似的跨境电商后台，熟悉退款与库存流程，可以按里程碑准时交付。')
    await page.fill(tid('apply-price-111'), '1500')
    await page.fill(tid('apply-price-112'), '1800')
    await page.fill(tid('apply-price-113'), '1300')
    check((await page.textContent(tid('apply-total'))).includes('4,600'), 'apply total updates')
    await page.click(tid('apply-submit'))
    await page.waitForSelector(tid('consent-pseudonymized-accept'))
    await snap(page, 'consent-sheet', false)
    await page.click(tid('consent-pseudonymized-accept'))
    await page.waitForSelector(tid('project-applied'))
    const apply = calls.filter((c) => c.pathname === '/api/engineer/projects/11/applications').pop()
    check(apply && apply.body.prices.length === 3 && apply.body.prices[0].milestoneId === 111 && apply.body.prices[0].talentAmountUsd === 1500, 'application body has prices per milestone')
    check(calls.some((c) => c.body && c.body.type === 'SHARE_PROFILE_PSEUDONYMIZED' && c.body.granted === true), 'pseudonymized consent recorded before applying')

    // ---------------- applications
    console.log('Applications')
    await page.click(tid('nav-back'))
    await page.waitForSelector(tid('tab-applications'))
    await page.click(tid('tab-applications'))
    await page.waitForSelector(tid('cv-consent-grant-61'))
    await snap(page, 'applications')
    await page.click(tid('cv-consent-grant-61'))
    await page.waitForSelector(tid('cv-consent-granted-61'))
    check(calls.some((c) => c.pathname === '/api/engineer/applications/61/cv-consent' && c.body.granted === true), 'cv-consent granted for selected application')

    // ---------------- contracts
    console.log('Contracts')
    await page.click(tid('tab-contracts'))
    await page.waitForSelector(tid('contract-card-31'))
    await snap(page, 'contracts', false)
    await page.click(tid('contract-card-31'))
    await page.waitForSelector(tid('contract-accept-41'))
    check((await page.locator('.q-markdown img').count()) === 0, 'contract markdown renders no <img> (no remote fetch)')
    check((await page.locator('.q-markdown a[href^="javascript"]').count()) === 0, 'contract markdown drops javascript: links')
    await snap(page, 'contract-detail')
    await page.click(tid('contract-accept-41'))
    await page.waitForSelector(tid('contract-doc-accepted-41'))
    check(true, 'engineer document accepted')
    await page.click(tid('milestone-submit-51'))
    await page.waitForSelector(tid('milestone-note'))
    await page.fill(tid('milestone-note'), '已部署到测试环境，登录账号见附件说明。')
    await page.setInputFiles(tid('milestone-file'), { name: 'delivery.zip', mimeType: 'application/zip', buffer: Buffer.from('PK fake zip') })
    await page.waitForTimeout(300)
    await snap(page, 'milestone-submit-sheet', false)
    await page.click(tid('milestone-submit-confirm'))
    await page.waitForSelector(`${tid('milestone-status-51')}[data-status="SUBMITTED"]`)
    const sub = calls.filter((c) => c.pathname === '/api/engineer/milestones/51/submit').pop()
    check(sub && sub.body.attachmentIds.length === 1 && sub.body.attachmentIds[0] === 801, 'milestone submit sends uploaded attachment ids')
    await page.fill(tid('message-input'), '第一个里程碑已提交，请查收。')
    await page.click(tid('message-send'))
    await page.waitForSelector('text=第一个里程碑已提交，请查收。')
    check(true, 'message sent and shown')
    await page.locator(tid('messages')).scrollIntoViewIfNeeded()
    await snap(page, 'contract-messages', false)

    // ---------------- earnings + me
    console.log('Earnings & me')
    await page.goto(BASE + 'earnings')
    await page.waitForSelector(tid('earning-51'))
    await snap(page, 'earnings', false)
    await page.goto(BASE + 'me')
    await page.waitForSelector(tid('consent-toggle-SHARE_PROFILE_PSEUDONYMIZED'))
    await snap(page, 'me')
    await page.click(tid('consent-toggle-SHARE_PROFILE_PSEUDONYMIZED'))
    await page.waitForSelector(tid('confirm-ok'))
    check((await page.textContent(tid('confirm-dialog'))).includes('不会出现在新的投标中'), 'withdraw pseudonymized consent explains you are hidden from new applications')
    await snap(page, 'me-withdraw-dialog', false)
    await page.click(tid('confirm-ok'))
    await page.waitForTimeout(300)
    check(state.consents.SHARE_PROFILE_PSEUDONYMIZED === false, 'pseudonymized consent withdrawn')
    await page.click(tid('me-delete'))
    await page.click(tid('confirm-ok'))
    await page.waitForSelector(`${tid('confirm-dialog')} >> text=进行中的合同`)
    check(true, 'delete account handles ACCOUNT_HAS_ACTIVE_CONTRACTS')
    await page.click(tid('confirm-ok'))
    await page.click(tid('me-language'))
    await page.click(tid('me-language-en'))
    await page.waitForSelector('text=Privacy & consents')
    check(calls.some((c) => c.pathname === '/api/me' && c.method === 'PUT' && c.body.locale === 'en'), 'language switch saved with PUT /api/me')
    await snap(page, 'me-en', false)
    await page.goto(BASE + 'projects/11')
    await page.waitForSelector(tid('project-applied'))
    await snap(page, 'project-detail-en', false)
    await ctx.close()

    // ---------------- narrow phone (360px): no horizontal scroll; desktop: centered column
    console.log('Layout at 360px and desktop')
    ctx = await browser.newContext({ viewport: { width: 360, height: 740 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2 })
    await ctx.addInitScript(() => localStorage.setItem('qiao.h5.token', 'mock-token'))
    await ctx.route('**/*', async (route) => {
      const url = new URL(route.request().url())
      if (url.hostname !== 'localhost') {
        external.push(route.request().url())
        return route.abort('blockedbyclient')
      }
      if (!url.pathname.startsWith('/api/')) return route.continue()
      const raw = route.request().postData()
      const res = handleApi(route.request().method(), url.pathname, url.searchParams, raw ? JSON.parse(raw) : {})
      return route.fulfill({ status: res.status, contentType: 'application/json', body: JSON.stringify(res.body) })
    })
    page = await ctx.newPage()
    attach(page)
    for (const p of ['', 'login', 'projects', 'projects/13', 'applications', 'contracts/31', 'earnings', 'me', 'profile']) {
      await page.goto(BASE + p)
      await page.waitForLoadState('networkidle')
      await page.waitForTimeout(200)
      const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)
      check(overflow <= 0, `no horizontal scroll at 360px on /m/${p} (overflow ${overflow}px)`)
    }
    await page.goto(BASE + 'projects/13')
    await page.waitForSelector(tid('apply-form'))
    await snap(page, 'project-detail-360', false)
    await ctx.close()
    ctx = await newContext(browser, { token: 'mock-token' })
    page = await ctx.newPage()
    await page.setViewportSize({ width: 1280, height: 800 })
    await page.goto(BASE + 'projects')
    await page.waitForSelector(tid('project-card-11'))
    await snap(page, 'desktop-projects', false)
    await ctx.close()

    // ---------------- 401 → login
    console.log('401 handling')
    ctx = await newContext(browser, { token: 'expired' })
    await ctx.route('**/api/me', (route) => route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ code: 'UNAUTHORIZED', message: 'Unauthorized', data: null }) }))
    page = await ctx.newPage()
    await page.goto(BASE + 'contracts')
    await page.waitForURL(/\/m\/login\?redirect=/)
    check(true, '401 redirects to login with redirect param')
    await ctx.close()
  } finally {
    await browser.close()
    preview.kill()
  }

  console.log('\nExternal (non-localhost) requests:', external.length)
  for (const u of external) console.log('  ', u)
  const errors = consoleErrors
  console.log('Expected 4xx API responses logged by Chromium:', expectedApiErrors.length)
  console.log('Console errors:', errors.length)
  for (const e of errors) console.log('  ', e)
  if (external.length) failures.push('external requests were made')
  if (errors.length) failures.push('console errors')
  console.log(failures.length ? `\nFAILED (${failures.length}):\n - ${failures.join('\n - ')}` : '\nALL CHECKS PASSED')
  process.exit(failures.length ? 1 : 0)
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
