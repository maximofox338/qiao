import { test, expect, devices, type BrowserContext, type Page, type APIRequestContext } from '@playwright/test';

/**
 * Equipos (docs/SPEC.md §12) con cuatro navegadores: cliente (escritorio, inglés), líder y miembro del equipo
 * (iPhone, chino) y, por API, administración y socio. El alta y la aprobación (ya cubiertas por full-flow) se hacen
 * por API; la interfaz recorre: crear equipo → invitar por alias → aceptar → postular con reparto → consentimientos
 * por miembro → tarjeta de equipo → contrato con un acuerdo por miembro → entrega del líder → liquidación por miembro.
 */

const API = process.env.API_URL ?? 'http://127.0.0.1:8080';
const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? 'admin@e2e.qiao';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD ?? 'E2e-Admin-Password-123';
const PARTNER_KEY = process.env.PARTNER_API_KEY ?? 'e2e-partner-key';
const BASE = process.env.BASE_URL ?? 'http://localhost:8088';

const run = Date.now().toString(36);
const companyName = `Fjord Robotics ${run}`;
const teamName = `Equipo Hangzhou ${run}`;
const iso = (days: number) => new Date(Date.now() + days * 86_400_000).toISOString().slice(0, 10);
const period = new Date().toISOString().slice(0, 7);

type Envelope<T> = { code: string; message: string; data: T };
type Engineer = { email: string; token: string; alias: string; partnerRef: string; cvText: string };

async function call<T>(request: APIRequestContext, method: string, path: string, token?: string, body?: unknown,
  extraHeaders: Record<string, string> = {}): Promise<Envelope<T>> {
  const res = await request.fetch(`${API}${path}`, {
    method,
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...extraHeaders },
    data: body,
  });
  return (await res.json()) as Envelope<T>;
}

async function api<T>(request: APIRequestContext, method: string, path: string, token?: string, body?: unknown,
  extraHeaders: Record<string, string> = {}): Promise<T> {
  const json = await call<T>(request, method, path, token, body, extraHeaders);
  expect(json.code, `${method} ${path} → ${json.code} ${json.message}`).toBe('OK');
  return json.data;
}

async function lastOtp(request: APIRequestContext, email: string): Promise<string> {
  for (let i = 0; i < 20; i++) {
    const res = await request.get(`${API}/api/dev/last-otp?email=${encodeURIComponent(email)}`);
    if (res.ok()) {
      const code = (await res.json()).data?.code;
      if (code) return String(code);
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`no OTP for ${email}`);
}

async function register(request: APIRequestContext, email: string, role: 'CLIENT' | 'ENGINEER'): Promise<string> {
  const locale = role === 'ENGINEER' ? 'zh-CN' : 'en';
  await api(request, 'POST', '/api/auth/otp/request', undefined, { email, locale });
  const [terms, privacy] = await Promise.all(['terms', 'privacy'].map((d) => api<{ version: string }>(request, 'GET', `/api/public/legal/${d}`)));
  const verified = await api<{ token: string }>(request, 'POST', '/api/auth/otp/verify', undefined, {
    email, code: await lastOtp(request, email), role, locale, acceptTerms: true,
    termsVersion: terms.version, privacyVersion: privacy.version,
  });
  return verified.token;
}

async function approvedEngineer(request: APIRequestContext, adminToken: string, label: string): Promise<Engineer> {
  const email = `${label}.${run}@e2e.qiao`;
  const token = await register(request, email, 'ENGINEER');
  const cvText = `CV-${label}-${run}: Vue 3 + Spring Boot, logística y comercio electrónico.`;
  await api(request, 'PUT', '/api/engineer/profile', token, {
    headline: label === 'lead' ? '全栈工程师 · 团队负责人' : '前端工程师 · Vue 3', bio: '多年项目交付经验。',
    skills: ['Vue', 'Spring Boot'], yearsExperience: 6, region: 'Zhejiang', englishLevel: 'GOOD', spanishLevel: 'NONE',
    hourlyRateUsd: 30, availabilityHoursWeek: 30, cvText, education: 'B.Eng.', portfolioLinks: [], declarationNoRestrictions: true,
  });
  await api(request, 'POST', '/api/me/consents', token, { type: 'CROSS_BORDER_PROCESSING', granted: true });
  const profile = await api<{ id: number; alias: string }>(request, 'POST', '/api/engineer/profile/submit', token);
  const partnerRef = `PARTNER-${label.toUpperCase()}-${run}`;
  await api(request, 'POST', `/api/admin/engineers/${profile.id}/review`, adminToken, { decision: 'APPROVE', note: 'ok', partnerRef });
  return { email, token, alias: profile.alias, partnerRef, cvText };
}

const records = <T>(data: T[] | { records: T[] }): T[] => (Array.isArray(data) ? data : data.records);

async function confirmIfAsked(page: Page) {
  const h5 = page.getByTestId('confirm-ok');
  const el = page.locator('.el-message-box__btns .el-button--primary');
  await page.waitForTimeout(300);
  if (await h5.isVisible().catch(() => false)) await h5.click();
  else if (await el.isVisible().catch(() => false)) await el.click();
}

/** Opens a context already signed in (token in localStorage before any script runs). */
async function signedIn(ctx: BrowserContext, key: string, token: string) {
  await ctx.addInitScript(([k, v]) => window.localStorage.setItem(k, v), [key, token]);
  return ctx.newPage();
}

let clientCtx: BrowserContext, leadCtx: BrowserContext, memberCtx: BrowserContext;
let client: Page, leadPage: Page, memberPage: Page;
const engineerExternalRequests: string[] = [];

test.describe.serial('Qiao — equipos', () => {
  let adminToken: string;
  let clientToken: string;
  let lead: Engineer;
  let member: Engineer;
  let projectId: number;
  let teamId: number;
  let applicationId: number;
  let contractId: number;
  let milestoneId: number;

  test.afterAll(async () => {
    await Promise.all([clientCtx?.close(), leadCtx?.close(), memberCtx?.close()]);
  });

  test('1. preparación por API: cliente aprobado, proyecto abierto y dos ingenieros aprobados', async ({ browser, request }) => {
    adminToken = (await api<{ token: string }>(request, 'POST', '/api/admin/auth/login', undefined,
      { email: ADMIN_EMAIL, password: ADMIN_PASSWORD })).token;

    clientToken = await register(request, `client.team.${run}@e2e.qiao`, 'CLIENT');
    await api(request, 'PUT', '/api/client/company', clientToken, { name: companyName, country: 'NO', website: 'https://fjord.example', size: '11-50' });
    const decl = await api<{ version: string }>(request, 'GET', '/api/public/legal/client-declarations');
    const company = await api<{ id: number }>(request, 'POST', '/api/client/company/declarations', clientToken, { version: decl.version, accepted: true });
    await api(request, 'POST', `/api/admin/companies/${company.id}/review`, adminToken, { decision: 'APPROVE', sanctionsStatus: 'CLEAR', note: 'ok' });

    const project = await api<{ id: number }>(request, 'POST', '/api/client/projects', clientToken, {
      title: `Warehouse robots dashboard ${run}`,
      description: 'Vue 3 dashboard and Spring Boot API for our warehouse robots. Two milestones with demos.',
      categoryCode: 'web-dev', skills: ['Vue', 'Spring Boot'], budgetUsd: 3000, startDate: iso(3), endDate: iso(60),
      milestones: [
        { title: 'API and data model', deliverable: 'REST API deployed to staging with tests.', dueDate: iso(30), amountUsd: 1500 },
        { title: 'Dashboard', deliverable: 'Dashboard views and handover document.', dueDate: iso(55), amountUsd: 1500 },
      ],
      declarations: { noControlledTech: true, noBulkUsSensitiveData: true, noEmployment: true, noProhibitedCategory: true },
      submit: true,
    });
    projectId = project.id;
    await api(request, 'POST', `/api/admin/projects/${projectId}/review`, adminToken, { decision: 'APPROVE' });

    lead = await approvedEngineer(request, adminToken, 'lead');
    member = await approvedEngineer(request, adminToken, 'member');

    clientCtx = await browser.newContext({ ...devices['Desktop Chrome'], locale: 'en-US', baseURL: BASE });
    leadCtx = await browser.newContext({ ...devices['iPhone 13'], locale: 'zh-CN', baseURL: BASE });
    memberCtx = await browser.newContext({ ...devices['iPhone 13'], locale: 'zh-CN', baseURL: BASE });
    for (const ctx of [leadCtx, memberCtx]) {
      ctx.on('request', (req) => {
        const url = req.url();
        const host = new URL(url).hostname;
        if (!['localhost', '127.0.0.1'].includes(host) && !url.startsWith('data:') && !url.startsWith('blob:')) engineerExternalRequests.push(url);
      });
    }
    client = await signedIn(clientCtx, 'qiao.user.token', clientToken);
    leadPage = await signedIn(leadCtx, 'qiao.h5.token', lead.token);
    memberPage = await signedIn(memberCtx, 'qiao.h5.token', member.token);
  });

  test('2. el líder crea el equipo e invita al miembro por su alias', async () => {
    await leadPage.goto('/m/teams');
    await expect(leadPage.getByTestId('teams-explain')).toBeVisible();
    await leadPage.getByTestId('team-create-open').click();
    await leadPage.getByTestId('team-create-name').fill(teamName);
    await leadPage.getByTestId('team-create-description').fill('两名全栈工程师，按里程碑交付。');
    await leadPage.getByTestId('team-create-submit').click();
    await expect(leadPage).toHaveURL(/\/m\/teams\/\d+$/);
    teamId = Number(leadPage.url().match(/teams\/(\d+)/)![1]);
    await expect(leadPage.getByTestId('team-name')).toHaveText(teamName);
    await expect(leadPage.getByTestId('team-can-apply')).toHaveAttribute('data-status', 'NOT_READY');

    await leadPage.getByTestId('team-invite-alias').fill(member.alias);
    await leadPage.getByTestId('team-invite-submit').click();
    await expect(leadPage.getByTestId(`team-member-${member.alias}`)).toHaveAttribute('data-status', 'INVITED');
  });

  test('3. el miembro acepta la invitación en el móvil', async () => {
    await memberPage.goto('/m/me');
    await memberPage.getByTestId('me-teams').click();
    await expect(memberPage).toHaveURL(/\/m\/teams$/);
    await memberPage.getByTestId(`team-invitation-accept-${teamId}`).click();
    await expect(memberPage.getByTestId(`team-card-${teamId}`)).toBeVisible();
    await memberPage.getByTestId(`team-card-${teamId}`).click();
    // entre compañeros sólo alias, titular y habilidades: nunca email, CV ni referencia del socio
    await expect(memberPage.getByTestId(`team-member-${lead.alias}`)).toBeVisible();
    await expect(memberPage.getByTestId('team-page')).not.toContainText(lead.email);
    await expect(memberPage.getByTestId('team-page')).not.toContainText(lead.partnerRef);

    await leadPage.reload();
    await expect(leadPage.getByTestId(`team-member-${member.alias}`)).toHaveAttribute('data-status', 'ACTIVE');
    await expect(leadPage.getByTestId('team-can-apply')).toHaveAttribute('data-status', 'READY');
  });

  test('4. el líder se postula como equipo con un reparto 60/40; falta el consentimiento del miembro', async () => {
    await leadPage.goto(`/m/projects/${projectId}`);
    await expect(leadPage.getByTestId('apply-form')).toBeVisible();
    await leadPage.getByTestId('apply-mode-team').click();
    await expect(leadPage.getByTestId('apply-share-total')).toHaveAttribute('data-valid', 'true');
    await leadPage.getByTestId(`apply-share-${lead.alias}`).fill('60');
    await leadPage.getByTestId(`apply-share-${member.alias}`).fill('30');
    await expect(leadPage.getByTestId('apply-share-total')).toHaveAttribute('data-valid', 'false');
    await leadPage.getByTestId(`apply-share-${member.alias}`).fill('40');
    await expect(leadPage.getByTestId('apply-share-total')).toHaveAttribute('data-valid', 'true');
    await expect(leadPage.getByTestId(`apply-share-amount-${member.alias}`)).toContainText('960');
    await leadPage.getByTestId('apply-cover-note').fill('我们两人分工：后端接口与前端看板各负责一个里程碑。');
    await leadPage.getByTestId('apply-submit').click();
    // primero el propio consentimiento del líder…
    await expect(leadPage.getByTestId('consent-pseudonymized-sheet')).toBeVisible();
    await leadPage.getByTestId('consent-pseudonymized-accept').click();
    // …y la API exige el de cada miembro
    await expect(leadPage.locator('.van-toast')).toContainText(member.alias);
    await expect(leadPage.getByTestId('apply-form')).toBeVisible();
  });

  test('5. el miembro concede su propio consentimiento y el líder envía la postulación', async ({ request }) => {
    await memberPage.goto('/m/me');
    const toggle = memberPage.getByTestId('consent-toggle-SHARE_PROFILE_PSEUDONYMIZED');
    await expect(toggle).toHaveAttribute('aria-checked', 'false');
    await toggle.click();
    await expect(toggle).toHaveAttribute('aria-checked', 'true');

    await leadPage.getByTestId('apply-submit').click();
    await expect(leadPage.getByTestId('project-applied')).toBeVisible();

    const apps = await api<any[]>(request, 'GET', '/api/engineer/applications', member.token);
    const app = apps.find((a) => a.projectId === projectId);
    applicationId = app.id;
    expect(app.team.myRole).toBe('MEMBER');
    expect(Number(app.team.mySharePct)).toBe(40);
    await memberPage.goto('/m/applications');
    await expect(memberPage.getByTestId(`application-team-${applicationId}`)).toHaveAttribute('data-role', 'MEMBER');
    await expect(memberPage.getByTestId(`application-withdraw-${applicationId}`)).toHaveCount(0);
  });

  test('6. el cliente ve una tarjeta de equipo con perfiles seudonimizados y pide CV por miembro', async ({ request }) => {
    const apps = records(await api<any>(request, 'GET', `/api/client/projects/${projectId}/applications`, clientToken));
    const json = JSON.stringify(apps);
    for (const secret of [lead.email, member.email, lead.cvText, member.cvText, lead.partnerRef, member.partnerRef, 'sharePct']) {
      expect(json).not.toContain(secret);
    }
    expect(apps[0].team.members.map((m: any) => m.alias)).toEqual([lead.alias, member.alias]);

    await client.goto(`/client/projects/${projectId}`);
    await expect(client.getByTestId(`application-team-${applicationId}`)).toBeVisible();
    await expect(client.getByTestId(`application-team-name-${applicationId}`)).toContainText(teamName);
    await expect(client.getByTestId(`application-${applicationId}-member-${lead.alias}-alias`)).toHaveText(lead.alias);
    await expect(client.getByTestId(`application-${applicationId}-member-${member.alias}-alias`)).toHaveText(member.alias);
    await client.getByTestId(`application-select-${applicationId}`).click();
    await confirmIfAsked(client);
    await expect(client.getByTestId(`application-status-${applicationId}`)).toHaveAttribute('data-status', 'SELECTED');
    await client.getByTestId(`application-${applicationId}-member-${member.alias}-cv`).click();
    await expect(client.getByTestId('cv-consent-required')).toBeVisible();
    await client.keyboard.press('Escape');
  });

  test('7. cada miembro decide sobre su propio CV', async () => {
    await memberPage.goto('/m/applications');
    await memberPage.getByTestId(`cv-consent-grant-${applicationId}`).click();
    await confirmIfAsked(memberPage);
    await expect(memberPage.getByTestId(`cv-consent-granted-${applicationId}`)).toBeVisible();

    await client.reload();
    await client.getByTestId(`application-${applicationId}-member-${member.alias}-cv`).click();
    await expect(client.getByTestId('cv-text')).toContainText(member.cvText);
    await client.getByTestId('cv-close').click();
    await client.getByTestId(`application-${applicationId}-member-${lead.alias}-cv`).click();
    await expect(client.getByTestId('cv-consent-required')).toBeVisible();
    await client.keyboard.press('Escape');
  });

  test('8. contratación: un contrato con el cliente y un acuerdo por miembro', async ({ request }) => {
    await client.getByTestId(`application-hire-${applicationId}`).click();
    await expect(client.getByTestId('hire-dialog')).toContainText(teamName);
    await client.getByTestId('hire-confirm').click();
    await expect(client).toHaveURL(/\/client\/contracts\/\d+/);
    contractId = Number(client.url().match(/contracts\/(\d+)/)![1]);

    const detail = await api<any>(request, 'GET', `/api/client/contracts/${contractId}`, clientToken);
    const leadView = await api<any>(request, 'GET', `/api/engineer/contracts/${contractId}`, lead.token);
    const memberView = await api<any>(request, 'GET', `/api/engineer/contracts/${contractId}`, member.token);
    expect(detail.documents.map((d: any) => d.kind)).toEqual(['CLIENT_SERVICES']);
    expect(leadView.documents).toHaveLength(1);
    expect(memberView.documents).toHaveLength(1);
    expect(leadView.documents[0].id).not.toBe(memberView.documents[0].id);
    expect(memberView.documents[0].body).toContain(member.partnerRef);
    // confidencialidad: ni precio del cliente ni nombre de la empresa; nada privado de otros miembros
    for (const view of [leadView, memberView]) {
      const s = JSON.stringify(view);
      for (const secret of [String(detail.clientTotalUsd), companyName, lead.email, member.email]) expect(s).not.toContain(secret);
    }
    expect(JSON.stringify(memberView)).not.toContain(lead.partnerRef);
    expect(JSON.stringify(leadView)).not.toContain(member.partnerRef);
    expect(JSON.stringify(detail)).not.toContain(member.partnerRef);
    milestoneId = detail.milestones.sort((a: any, b: any) => a.seq - b.seq)[0].id;

    await expect(client.getByTestId('contract-team-name')).toContainText(teamName);
    await client.getByTestId(`contract-doc-accept-${detail.documents[0].id}`).click();
    await confirmIfAsked(client);
    await expect(client.getByTestId(`contract-doc-accepted-${detail.documents[0].id}`)).toBeVisible();
    await expect(client.getByTestId(`contract-team-member-${member.alias}`)).toHaveAttribute('data-status', 'PENDING');

    await leadPage.goto(`/m/contracts/${contractId}`);
    await expect(leadPage.getByTestId('contract-team')).toBeVisible();
    await leadPage.getByTestId(`contract-accept-${leadView.documents[0].id}`).click();
    await confirmIfAsked(leadPage);
    await expect(leadPage.getByTestId(`contract-doc-accepted-${leadView.documents[0].id}`)).toBeVisible();
    await expect(leadPage.getByTestId('contract-status')).toHaveAttribute('data-status', 'PENDING_SIGNATURES');

    await memberPage.goto(`/m/contracts/${contractId}`);
    await expect(memberPage.getByTestId('contract-my-share')).toContainText('40');
    await expect(memberPage.getByTestId(`contract-team-member-${lead.alias}`)).toHaveAttribute('data-status', 'ACCEPTED');
    await memberPage.getByTestId(`contract-accept-${memberView.documents[0].id}`).click();
    await confirmIfAsked(memberPage);
    await expect(memberPage.getByTestId('contract-status')).toHaveAttribute('data-status', 'ACTIVE');

    await client.reload();
    await expect(client.getByTestId(`contract-team-member-${member.alias}`)).toHaveAttribute('data-status', 'ACCEPTED');
    await expect(client.getByTestId('contract-status')).toHaveAttribute('data-status', 'ACTIVE');
  });

  test('9. pago del primer hito; entrega el líder y todos ven los mensajes', async () => {
    await client.getByTestId(`milestone-fund-${milestoneId}`).click();
    await expect(client).toHaveURL(/\/pay\/fake\/\d+/);
    await client.getByTestId('pay-confirm').click();
    await expect(client.getByTestId(`milestone-status-${milestoneId}`)).toHaveAttribute('data-status', 'FUNDED');

    await memberPage.reload();
    await expect(memberPage.getByTestId(`milestone-lead-only-${milestoneId}`)).toBeVisible();
    await expect(memberPage.getByTestId(`milestone-submit-${milestoneId}`)).toHaveCount(0);
    await expect(memberPage.getByTestId(`milestone-amount-${milestoneId}`)).toContainText('480');
    await memberPage.getByTestId('message-input').fill('前端部分已完成，等待队长提交。');
    await memberPage.getByTestId('message-send').click();
    await expect(memberPage.getByTestId('message-list')).toContainText('前端部分已完成');

    await leadPage.reload();
    await expect(leadPage.getByTestId('message-list')).toContainText(member.alias);
    await leadPage.getByTestId(`milestone-submit-${milestoneId}`).click();
    await leadPage.getByTestId('milestone-note').fill('接口已部署到测试环境，附说明文档。');
    await leadPage.getByTestId('milestone-file').setInputFiles({ name: 'equipo-hito-1.txt', mimeType: 'text/plain', buffer: Buffer.from('Staging ready') });
    await leadPage.waitForTimeout(800);
    await leadPage.getByTestId('milestone-submit-confirm').click();
    await expect(leadPage.getByTestId(`milestone-status-${milestoneId}`)).toHaveAttribute('data-status', 'SUBMITTED');

    await client.reload();
    await expect(client.getByTestId('message-list')).toContainText('前端部分已完成');
    await expect(client.getByTestId('attachment-list')).toContainText('equipo-hito-1.txt');
    await client.getByTestId(`milestone-accept-${milestoneId}`).click();
    await confirmIfAsked(client);
    await expect(client.getByTestId(`milestone-status-${milestoneId}`)).toHaveAttribute('data-status', 'ACCEPTED');
  });

  test('10. la liquidación tiene una línea por miembro y el socio paga a cada uno', async ({ request }) => {
    const batch = await api<any>(request, 'POST', '/api/admin/settlements', adminToken, { period });
    const lines = batch.lines.filter((l: any) => l.contractMilestoneId === milestoneId);
    expect(lines.map((l: any) => l.partnerRef).sort()).toEqual([lead.partnerRef, member.partnerRef].sort());
    expect(Number(lines.find((l: any) => l.partnerRef === lead.partnerRef).talentAmountUsd)).toBe(720);
    expect(Number(lines.find((l: any) => l.partnerRef === member.partnerRef).talentAmountUsd)).toBe(480);
    await api(request, 'POST', `/api/admin/settlements/${batch.id}/mark-exported`, adminToken);

    const partner = { 'X-Partner-Key': PARTNER_KEY };
    const forPartner = await api<any>(request, 'GET', `/api/partner/settlements/${batch.id}`, undefined, undefined, partner);
    for (const line of forPartner.lines) {
      await api(request, 'POST', `/api/partner/settlements/${batch.id}/lines/${line.lineId}/paid`, undefined,
        { partnerPaymentRef: `ICBC-${line.lineId}`, iitWithheldCny: 300.0, netPaidCny: 3100.0, paidAt: new Date().toISOString() }, partner);
    }

    await memberPage.goto('/m/earnings');
    await expect(memberPage.getByTestId(`earning-settlement-${milestoneId}`)).toHaveAttribute('data-status', 'PAID');
    await expect(memberPage.getByTestId(`earning-${milestoneId}`)).toContainText('480');
    const earnings = await api<any[]>(request, 'GET', '/api/engineer/earnings', lead.token);
    expect(Number(earnings.find((e) => e.contractMilestoneId === milestoneId).talentAmountUsd)).toBe(720);
  });

  test('11. se completa el segundo hito y el contrato queda COMPLETED', async ({ request }) => {
    const detail = await api<any>(request, 'GET', `/api/client/contracts/${contractId}`, clientToken);
    const second = detail.milestones.find((m: any) => m.id !== milestoneId);
    const checkout = await api<{ paymentId: number }>(request, 'POST', `/api/client/milestones/${second.id}/checkout`, clientToken);
    await api(request, 'POST', `/api/payments/fake/${checkout.paymentId}/confirm`, clientToken);
    const blocked = await call(request, 'POST', `/api/engineer/milestones/${second.id}/submit`, member.token, { note: '我来提交' });
    expect(blocked.code).toBe('TEAM_LEAD_ONLY');
    await api(request, 'POST', `/api/engineer/milestones/${second.id}/submit`, lead.token, { note: '看板已完成并交接。' });
    await api(request, 'POST', `/api/client/milestones/${second.id}/accept`, clientToken);
    expect((await api<any>(request, 'GET', `/api/client/contracts/${contractId}`, clientToken)).status).toBe('COMPLETED');
  });

  test('12. valoraciones: el cliente puntúa al equipo y el líder al cliente; los textos sólo los ve el back-office', async ({ browser, request }) => {
    await client.goto(`/client/contracts/${contractId}`);
    await client.getByTestId('review-star-4').click();
    await client.getByTestId('review-comment').fill('Great teamwork and a clean handover.');
    await client.getByTestId('review-submit').click();
    await expect(client.getByTestId('review-done')).toHaveAttribute('data-stars', '4');
    // la puntuación llega al perfil seudonimizado de cada miembro
    for (const e of [lead, member]) {
      const p = await api<any>(request, 'GET', '/api/engineer/profile', e.token);
      expect(p.ratingCount).toBe(1);
      expect(Number(p.ratingAvg)).toBe(4);
    }

    await memberPage.goto(`/m/contracts/${contractId}`);
    await expect(memberPage.getByTestId('review-lead-only')).toBeVisible();
    await leadPage.goto(`/m/contracts/${contractId}`);
    await leadPage.getByTestId('review-star-5').click();
    await leadPage.getByTestId('review-comment').fill('需求清晰，反馈及时。');
    await leadPage.getByTestId('review-submit').click();
    await expect(leadPage.getByTestId('review-done')).toHaveAttribute('data-stars', '5');
    await memberPage.goto('/m/profile');
    await expect(memberPage.getByTestId('profile-rating')).toBeVisible();

    const clientView = await api<any>(request, 'GET', `/api/client/contracts/${contractId}`, clientToken);
    expect(JSON.stringify(clientView)).not.toContain('需求清晰');
    const memberView = await api<any>(request, 'GET', `/api/engineer/contracts/${contractId}`, member.token);
    expect(JSON.stringify(memberView)).not.toContain('Great teamwork');

    const adminCtx = await browser.newContext({ ...devices['Desktop Chrome'], locale: 'es-ES', baseURL: BASE });
    const adminPage = await signedIn(adminCtx, 'qiao.admin.token', adminToken);
    await adminPage.goto('/admin/reviews');
    await expect(adminPage.getByTestId('admin-reviews-table')).toContainText('需求清晰');
    await expect(adminPage.getByTestId('admin-reviews-table')).toContainText('Great teamwork');
    await adminCtx.close();
  });

  test('13. la app de ingenieros no hizo ninguna solicitud a servidores externos', async () => {
    expect(engineerExternalRequests).toEqual([]);
  });
});
