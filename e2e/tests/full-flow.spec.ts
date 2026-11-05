import { test, expect, devices, type Browser, type BrowserContext, type Page, type APIRequestContext } from '@playwright/test';

/**
 * Flujo completo del MVP con tres navegadores a la vez:
 * cliente extranjero (escritorio), administrador (escritorio) e ingeniero en China (iPhone, chino).
 * Recorre: alta → revisión → proyecto → postulación → consentimientos → contrato → pago → entrega → liquidación.
 */

const API = process.env.API_URL ?? 'http://127.0.0.1:8080';
const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? 'admin@e2e.qiao';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD ?? 'E2e-Admin-Password-123';
const PARTNER_KEY = process.env.PARTNER_API_KEY ?? 'e2e-partner-key';
const BASE = process.env.BASE_URL ?? 'http://localhost:8088';

const run = Date.now().toString(36);
const clientEmail = `client.${run}@e2e.qiao`;
const engineerEmail = `eng.${run}@e2e.qiao`;
const companyName = `Nordwerk Analytics ${run}`;
const cvText = `CV-${run}: 5 years building Vue 3 and Spring Boot systems for logistics.`;

const iso = (days: number) => new Date(Date.now() + days * 86_400_000).toISOString().slice(0, 10);
const period = new Date().toISOString().slice(0, 7);

type Envelope<T> = { code: string; message: string; data: T };

async function api<T>(request: APIRequestContext, method: string, path: string, token?: string, body?: unknown, extraHeaders: Record<string, string> = {}): Promise<T> {
  const res = await request.fetch(`${API}${path}`, {
    method,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...extraHeaders },
    data: body,
  });
  const json = (await res.json()) as Envelope<T>;
  expect(json.code, `${method} ${path} → ${json.code} ${json.message}`).toBe('OK');
  return json.data;
}

const records = <T>(data: T[] | { records: T[] }): T[] => (Array.isArray(data) ? data : data.records);

async function lastOtp(request: APIRequestContext, email: string): Promise<string> {
  for (let i = 0; i < 20; i++) {
    const res = await request.get(`${API}/api/dev/last-otp?email=${encodeURIComponent(email)}`);
    if (res.ok()) {
      const json = await res.json();
      const d = json.data;
      const code = typeof d === 'string' ? d : d?.code ?? d?.otp;
      if (code) return String(code);
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`no OTP for ${email}`);
}

const token = (page: Page, key: string) => page.evaluate((k) => localStorage.getItem(k), key) as Promise<string>;

/** Clicks the app's own confirmation dialog if one shows up (h5: confirm-ok; web: Element Plus MessageBox). */
async function confirmIfAsked(page: Page) {
  const h5 = page.getByTestId('confirm-ok');
  const el = page.locator('.el-message-box__btns .el-button--primary');
  await page.waitForTimeout(300);
  if (await h5.isVisible().catch(() => false)) await h5.click();
  else if (await el.isVisible().catch(() => false)) await el.click();
}

let browserRef: Browser;
let clientCtx: BrowserContext, adminCtx: BrowserContext, engCtx: BrowserContext;
let client: Page, admin: Page, eng: Page;
const engineerExternalRequests: string[] = [];

test.describe.serial('Qiao MVP — flujo completo', () => {
  test.beforeAll(async ({ browser }) => {
    browserRef = browser;
    clientCtx = await browser.newContext({ ...devices['Desktop Chrome'], locale: 'en-US', baseURL: BASE });
    adminCtx = await browser.newContext({ ...devices['Desktop Chrome'], locale: 'es-ES', baseURL: BASE, acceptDownloads: true });
    engCtx = await browser.newContext({ ...devices['iPhone 13'], locale: 'zh-CN', baseURL: BASE });
    engCtx.on('request', (req) => {
      const host = new URL(req.url()).hostname;
      if (!['localhost', '127.0.0.1'].includes(host) && !req.url().startsWith('data:') && !req.url().startsWith('blob:')) {
        engineerExternalRequests.push(req.url());
      }
    });
    client = await clientCtx.newPage();
    admin = await adminCtx.newPage();
    eng = await engCtx.newPage();
  });

  test.afterAll(async () => {
    await Promise.all([clientCtx?.close(), adminCtx?.close(), engCtx?.close()]);
    void browserRef;
  });

  let projectId: number;
  let applicationId: number;
  let contractId: number;
  let milestoneId: number;

  test('1. el cliente se registra con código por email y acepta los términos', async ({ request }) => {
    await client.goto('/login');
    await client.getByTestId('login-email').fill(clientEmail);
    await client.getByTestId('login-send-code').click();
    await expect(client.getByTestId('login-code')).toBeVisible();
    await client.getByTestId('login-code').fill(await lastOtp(request, clientEmail));
    await client.getByTestId('login-submit').click();
    await expect(client.getByTestId('login-accept-terms')).toBeVisible();
    await client.getByTestId('login-accept-terms').check();
    await client.getByTestId('login-submit').click();
    await expect(client).toHaveURL(/\/client$/);
  });

  test('2. el cliente completa la empresa y firma las declaraciones', async () => {
    await client.goto('/client/company');
    await client.getByTestId('company-name').fill(companyName);
    await client.getByTestId('company-country').click();
    await client.getByTestId('company-country-option-DE').click();
    await client.getByTestId('company-website').fill('https://nordwerk.example');
    await client.getByTestId('company-size').click();
    await client.getByTestId('company-size-option-11-50').click();
    await expect(client.getByTestId('company-declarations-text')).toContainText(/\S/);
    await client.getByTestId('company-accept-declarations').check();
    await client.getByTestId('company-submit').click();
    await expect(client).toHaveURL(/\/client$/);
    await expect(client.getByTestId('company-status')).toHaveAttribute('data-status', 'PENDING_REVIEW');
  });

  test('3. el administrador revisa sanciones y aprueba la empresa', async ({ request }) => {
    await admin.goto('/admin/login');
    await admin.getByTestId('admin-login-email').fill(ADMIN_EMAIL);
    await admin.getByTestId('admin-login-password').fill(ADMIN_PASSWORD);
    await admin.getByTestId('admin-login-submit').click();
    await expect(admin).toHaveURL(/\/admin\/?$/);

    const company = await api<{ id: number }>(request, 'GET', '/api/client/company', await token(client, 'qiao.user.token'));
    await admin.goto('/admin/companies');
    await admin.getByTestId(`admin-company-open-${company.id}`).click();
    await admin.getByTestId('admin-company-sanctions-clear').click();
    await admin.getByTestId('admin-company-approve').click();
    await expect.poll(async () => (await api<{ status: string }>(request, 'GET', '/api/client/company', await token(client, 'qiao.user.token'))).status).toBe('APPROVED');
  });

  test('4. el cliente publica un proyecto por hitos; las categorías prohibidas no se pueden elegir', async () => {
    await client.goto('/client/projects/new');
    await client.getByTestId('project-category').click();
    await expect(client.getByTestId('project-category-option-crypto')).toHaveClass(/is-disabled/);
    await client.getByTestId('project-category-option-web-dev').click();
    await client.getByTestId('project-title').fill('Inventory dashboard for EU warehouses');
    await client.getByTestId('project-description').fill('Build a Vue 3 dashboard on top of our REST API: stock levels, alerts and CSV export. Two milestones with demos.');
    await client.getByTestId('project-skills').fill('Vue');
    await client.getByTestId('project-skills').press('Enter');
    await client.getByTestId('project-budget').fill('3000');
    await client.getByTestId('project-start-date').fill(iso(3));
    await client.getByTestId('project-end-date').fill(iso(60));
    await client.getByTestId('project-milestone-0-title').fill('Stock views');
    await client.getByTestId('project-milestone-0-deliverable').fill('Stock and alerts views connected to the API, deployed to staging.');
    await client.getByTestId('project-milestone-0-due-date').fill(iso(30));
    await client.getByTestId('project-milestone-0-amount').fill('1500');
    await client.getByTestId('project-add-milestone').click();
    await client.getByTestId('project-milestone-1-title').fill('Exports and handover');
    await client.getByTestId('project-milestone-1-deliverable').fill('CSV export, tests and handover document.');
    await client.getByTestId('project-milestone-1-due-date').fill(iso(55));
    await client.getByTestId('project-milestone-1-amount').fill('1500');
    await expect(client.getByTestId('project-milestone-sum')).toHaveAttribute('data-match', 'true');
    for (const d of ['no-controlled-tech', 'no-bulk-us-sensitive-data', 'no-employment', 'no-prohibited-category']) {
      await client.getByTestId(`project-decl-${d}`).check();
    }
    await client.getByTestId('project-submit').click();
    await expect(client).toHaveURL(/\/client\/projects\/\d+$/);
    projectId = Number(client.url().match(/projects\/(\d+)/)![1]);
    await expect(client.getByTestId('project-status')).toHaveAttribute('data-status', 'SUBMITTED');
  });

  test('5. operaciones aprueba el proyecto', async ({ request }) => {
    await admin.goto('/admin/projects');
    await admin.getByTestId(`admin-project-open-${projectId}`).click();
    await admin.getByTestId('admin-project-approve').click();
    await expect.poll(async () => (await api<{ status: string }>(request, 'GET', `/api/client/projects/${projectId}`, await token(client, 'qiao.user.token'))).status).toBe('OPEN');
  });

  test('6. el ingeniero se registra en el móvil, con consentimiento transfronterizo separado', async ({ request }) => {
    await eng.goto('/m/login');
    await eng.getByTestId('login-email').fill(engineerEmail);
    await eng.getByTestId('login-send-code').click();
    await expect(eng.getByTestId('login-code')).toBeVisible();
    await eng.getByTestId('login-code').fill(await lastOtp(request, engineerEmail));
    await eng.getByTestId('login-accept-terms').click();
    await eng.getByTestId('login-accept-crossborder').click();
    await eng.getByTestId('login-submit').click();
    await expect(eng).toHaveURL(/\/m\/profile/);

    await eng.getByTestId('profile-headline').fill('全栈工程师 · Vue 3 / Spring Boot');
    await eng.getByTestId('profile-bio').fill('5 年物流与电商系统开发经验，熟悉前后端与部署。');
    for (const s of ['Vue', 'Spring Boot']) {
      await eng.getByTestId('profile-skill-input').fill(s);
      await eng.getByTestId('profile-skill-add').click();
    }
    await eng.getByTestId('profile-region').click();
    await eng.getByTestId('profile-region-confirm').click();
    await eng.getByTestId('profile-years').fill('5');
    await eng.getByTestId('profile-rate').fill('25');
    await eng.getByTestId('profile-hours').fill('30');
    await eng.getByTestId('profile-english-GOOD').click();
    await eng.getByTestId('profile-cv').fill(cvText);
    await eng.getByTestId('profile-education').fill('B.Eng. Software Engineering');
    await eng.getByTestId('profile-declaration').click();
    await eng.getByTestId('profile-submit').click();
    await confirmIfAsked(eng);
    await expect(eng.getByTestId('profile-status')).toHaveAttribute('data-status', 'PENDING_REVIEW');
  });

  test('7. operaciones aprueba al ingeniero con la referencia del socio', async ({ request }) => {
    const adminToken = await token(admin, 'qiao.admin.token');
    const list = records(await api<any>(request, 'GET', '/api/admin/engineers?status=PENDING_REVIEW&size=100', adminToken));
    const row = list.find((e: any) => JSON.stringify(e).includes(engineerEmail));
    expect(row, 'engineer in review queue').toBeTruthy();
    await admin.goto('/admin/engineers');
    await admin.getByTestId(`admin-engineer-open-${row.id}`).click();
    await expect(admin.getByTestId('admin-engineer-cv')).toContainText(cvText);
    await admin.getByTestId('admin-engineer-partner-ref').fill(`PARTNER-${run}`);
    await admin.getByTestId('admin-engineer-approve').click();
    await expect.poll(async () => (await api<{ status: string }>(request, 'GET', '/api/engineer/profile', await token(eng, 'qiao.h5.token'))).status).toBe('APPROVED');
  });

  test('8. el ingeniero se postula y acepta mostrar su perfil seudonimizado', async () => {
    await eng.goto(`/m/projects/${projectId}`);
    await expect(eng.getByTestId('apply-form')).toBeVisible();
    await eng.getByTestId('apply-cover-note').fill('我做过三个类似的库存看板，可以按里程碑交付。');
    await eng.getByTestId('apply-submit').click();
    await expect(eng.getByTestId('consent-pseudonymized-sheet')).toBeVisible();
    await eng.getByTestId('consent-pseudonymized-accept').click();
    await expect(eng.getByTestId('project-applied')).toBeVisible();
  });

  test('9. el cliente ve un perfil seudonimizado y necesita consentimiento para el CV', async ({ request }) => {
    const apps = records(await api<any>(request, 'GET', `/api/client/projects/${projectId}/applications`, await token(client, 'qiao.user.token')));
    applicationId = apps[0].id;
    expect(JSON.stringify(apps)).not.toContain(engineerEmail);
    expect(JSON.stringify(apps)).not.toContain(cvText);

    await client.goto(`/client/projects/${projectId}`);
    await expect(client.getByTestId(`application-card-${applicationId}`)).toBeVisible();
    await client.getByTestId(`application-select-${applicationId}`).click();
    await confirmIfAsked(client);
    await expect(client.getByTestId(`application-status-${applicationId}`)).toHaveAttribute('data-status', 'SELECTED');
    await client.getByTestId(`application-cv-${applicationId}`).click();
    await expect(client.getByTestId('cv-consent-required')).toBeVisible();
    await client.keyboard.press('Escape');

    await eng.goto('/m/applications');
    await eng.getByTestId(`cv-consent-grant-${applicationId}`).click();
    await confirmIfAsked(eng);
    await expect(eng.getByTestId(`cv-consent-granted-${applicationId}`)).toBeVisible();

    await client.reload();
    await client.getByTestId(`application-cv-${applicationId}`).click();
    await expect(client.getByTestId('cv-text')).toContainText(cvText);
    await client.getByTestId('cv-close').click();
  });

  test('10. contratación: cada parte acepta su contrato y queda activo', async ({ request }) => {
    await client.getByTestId(`application-hire-${applicationId}`).click();
    await client.getByTestId('hire-confirm').click();
    await expect(client).toHaveURL(/\/client\/contracts\/\d+/);
    contractId = Number(client.url().match(/contracts\/(\d+)/)![1]);

    const detail = await api<any>(request, 'GET', `/api/client/contracts/${contractId}`, await token(client, 'qiao.user.token'));
    const engDetail = await api<any>(request, 'GET', `/api/engineer/contracts/${contractId}`, await token(eng, 'qiao.h5.token'));
    // Confidencialidad: cada parte recibe sólo su propio documento; el ingeniero nunca ve el precio del cliente.
    expect(detail.documents.map((d: any) => d.kind)).toEqual(['CLIENT_SERVICES']);
    expect(engDetail.documents.map((d: any) => d.kind)).toEqual(['ENGINEER_DELIVERABLES']);
    expect(JSON.stringify(engDetail)).not.toContain(String(detail.clientTotalUsd));
    expect(JSON.stringify(detail)).not.toContain(`PARTNER-${run}`);
    const clientDoc = detail.documents[0];
    const engDoc = engDetail.documents[0];
    milestoneId = detail.milestones.sort((a: any, b: any) => a.seq - b.seq)[0].id;

    await client.getByTestId(`contract-doc-accept-${clientDoc.id}`).click();
    await confirmIfAsked(client);
    await expect(client.getByTestId(`contract-doc-accepted-${clientDoc.id}`)).toBeVisible();

    await eng.goto(`/m/contracts/${contractId}`);
    await eng.getByTestId(`contract-accept-${engDoc.id}`).click();
    await confirmIfAsked(eng);
    await expect(eng.getByTestId(`contract-doc-accepted-${engDoc.id}`)).toBeVisible();
    await expect(eng.getByTestId('contract-status')).toHaveAttribute('data-status', 'ACTIVE');
  });

  test('11. el cliente prepaga el primer hito (checkout de prueba)', async () => {
    await client.goto(`/client/contracts/${contractId}`);
    await client.getByTestId(`milestone-fund-${milestoneId}`).click();
    await expect(client).toHaveURL(/\/pay\/fake\/\d+/);
    await client.getByTestId('pay-confirm').click();
    await expect(client).toHaveURL(new RegExp(`/client/contracts/${contractId}`));
    await expect(client.getByTestId(`milestone-status-${milestoneId}`)).toHaveAttribute('data-status', 'FUNDED');
  });

  test('12. el ingeniero entrega el hito con un archivo y escribe al cliente', async () => {
    await eng.goto(`/m/contracts/${contractId}`);
    await eng.getByTestId(`milestone-submit-${milestoneId}`).click();
    await eng.getByTestId('milestone-note').fill('已部署到测试环境，附上说明文档。');
    await eng.getByTestId('milestone-file').setInputFiles({ name: 'entrega-hito-1.txt', mimeType: 'text/plain', buffer: Buffer.from('Staging: https://staging.example\nUser: demo') });
    await eng.waitForTimeout(800);
    await eng.getByTestId('milestone-submit-confirm').click();
    await expect(eng.getByTestId(`milestone-status-${milestoneId}`)).toHaveAttribute('data-status', 'SUBMITTED');
    await eng.getByTestId('message-input').fill('Milestone 1 is on staging, please review.');
    await eng.getByTestId('message-send').click();
    await expect(eng.getByTestId('message-list')).toContainText('Milestone 1 is on staging');
  });

  test('13. el cliente ve el mensaje y el archivo, y acepta el hito', async () => {
    await client.goto(`/client/contracts/${contractId}`);
    await expect(client.getByTestId('message-list')).toContainText('Milestone 1 is on staging');
    await expect(client.getByTestId('attachment-list')).toContainText('entrega-hito-1.txt');
    await client.getByTestId(`milestone-accept-${milestoneId}`).click();
    await confirmIfAsked(client);
    await expect(client.getByTestId(`milestone-status-${milestoneId}`)).toHaveAttribute('data-status', 'ACCEPTED');
  });

  test('14. finanzas arma la liquidación del mes y el socio confirma el pago con retención', async ({ request }) => {
    await admin.goto('/admin/settlements');
    await admin.getByTestId('admin-settlement-period').fill(period);
    await admin.getByTestId('admin-settlement-create').click();
    const adminToken = await token(admin, 'qiao.admin.token');
    let batch: any;
    await expect.poll(async () => {
      const batches = records(await api<any>(request, 'GET', '/api/admin/settlements', adminToken));
      batch = batches.find((b: any) => b.period === period && b.status === 'DRAFT');
      return Boolean(batch);
    }).toBe(true);
    await expect(admin.getByTestId(`admin-settlement-row-${batch.id}`)).toBeVisible();

    const csv = await request.get(`${API}/api/admin/settlements/${batch.id}/export.csv`, { headers: { Authorization: `Bearer ${adminToken}` } });
    expect(await csv.text()).toContain(`PARTNER-${run}`);

    // Al crear el lote, la UI abre su detalle: se marca como exportado desde ese panel.
    await expect(admin.getByTestId('admin-settlement-drawer')).toBeVisible();
    await expect(admin.getByTestId('admin-settlement-lines')).toContainText(`PARTNER-${run}`);
    await admin.getByTestId('admin-settlement-drawer-export').click();
    await confirmIfAsked(admin);
    await admin.keyboard.press('Escape');
    await expect(admin.getByTestId(`admin-settlement-status-${batch.id}`)).toHaveAttribute('data-status', 'EXPORTED');

    const partner = { 'X-Partner-Key': PARTNER_KEY };
    const forPartner = await api<any>(request, 'GET', `/api/partner/settlements/${batch.id}`, undefined, undefined, partner);
    const line = forPartner.lines.find((l: any) => l.partnerRef === `PARTNER-${run}`);
    await api(request, 'POST', `/api/partner/settlements/${batch.id}/lines/${line.lineId ?? line.id}/paid`, undefined,
      { partnerPaymentRef: `CMB-${run}`, iitWithheldCny: 1600.0, netPaidCny: 7044.0, paidAt: new Date().toISOString() }, partner);

    await eng.goto('/m/earnings');
    await expect(eng.getByTestId(`earning-settlement-${milestoneId}`)).toHaveAttribute('data-status', 'PAID');
  });

  test('15. cumplimiento: el contador de transferencias registró los datos mostrados al cliente', async () => {
    await admin.goto('/admin/compliance');
    await expect(admin.getByTestId('admin-transfers-any')).toContainText(/[1-9]/);
    await expect(admin.getByTestId('admin-transfers-level')).toHaveAttribute('data-status', 'OK');
  });

  test('16. la app del ingeniero no hizo ninguna solicitud a servidores externos', async () => {
    expect(engineerExternalRequests).toEqual([]);
  });
});
