import { test, expect, devices, type APIRequestContext, type Page } from '@playwright/test';

/**
 * Ciclo de vida de los administradores en el back-office: un administrador de operaciones (creado por API para no
 * tocar al superadmin de los otros flujos) cambia su contraseña desde «Mi perfil» (su otra sesión se cierra, ésta
 * sigue), y el superadmin lo desactiva (su sesión abierta deja de valer y no puede entrar) y lo reactiva.
 */

const API = process.env.API_URL ?? 'http://127.0.0.1:8080';
const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? 'admin@e2e.qiao';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD ?? 'E2e-Admin-Password-123';
const BASE = process.env.BASE_URL ?? 'http://localhost:8088';

const run = Date.now().toString(36);
const opsEmail = `ops.lifecycle.${run}@e2e.qiao`;
const firstPassword = 'Ops-Lifecycle-Password-1';
const secondPassword = 'Ops-Lifecycle-Password-2';

type Envelope<T> = { code: string; message: string; data: T };

async function call<T>(request: APIRequestContext, method: string, path: string, token?: string, body?: unknown): Promise<Envelope<T>> {
  const res = await request.fetch(`${API}${path}`, { method, headers: token ? { Authorization: `Bearer ${token}` } : {}, data: body });
  return (await res.json()) as Envelope<T>;
}

async function uiLogin(page: Page, email: string, password: string) {
  await page.goto('/admin/login');
  await page.getByTestId('admin-login-email').fill(email);
  await page.getByTestId('admin-login-password').fill(password);
  await page.getByTestId('admin-login-submit').click();
}

test.describe.serial('Qiao — ciclo de vida de administradores', () => {
  test('cambio de contraseña, desactivación y reactivación', async ({ browser, request }) => {
    const superToken = (await call<{ token: string }>(request, 'POST', '/api/admin/auth/login', undefined,
      { email: ADMIN_EMAIL, password: ADMIN_PASSWORD })).data.token;
    const created = await call<{ id: number }>(request, 'POST', '/api/admin/admins', superToken,
      { email: opsEmail, password: firstPassword, roles: ['OPS'] });
    expect(created.code).toBe('OK');
    const opsId = created.data.id;
    // otra sesión del mismo administrador (otro dispositivo)
    const otherSession = (await call<{ token: string }>(request, 'POST', '/api/admin/auth/login', undefined,
      { email: opsEmail, password: firstPassword })).data.token;

    // ---- el administrador cambia su contraseña desde «Mi perfil»
    const opsCtx = await browser.newContext({ ...devices['Desktop Chrome'], locale: 'es-ES', baseURL: BASE });
    const ops = await opsCtx.newPage();
    await uiLogin(ops, opsEmail, firstPassword);
    await expect(ops).toHaveURL(/\/admin\/?$/);
    await ops.getByTestId('admin-menu-profile').click();
    await expect(ops.getByTestId('admin-password-form')).toBeVisible();

    // validación local: confirmación distinta
    await ops.getByTestId('admin-password-current').fill(firstPassword);
    await ops.getByTestId('admin-password-new').fill(secondPassword);
    await ops.getByTestId('admin-password-confirm').fill(secondPassword + 'x');
    await ops.getByTestId('admin-password-submit').click();
    await expect(ops.getByTestId('admin-password-form').locator('.el-form-item__error')).toHaveCount(1);

    // contraseña actual incorrecta: error del backend, la sesión sigue abierta
    await ops.getByTestId('admin-password-current').fill('Not-my-password-123');
    await ops.getByTestId('admin-password-confirm').fill(secondPassword);
    await ops.getByTestId('admin-password-submit').click();
    await expect(ops.getByTestId('admin-password-error')).toBeVisible();
    await expect(ops).toHaveURL(/\/admin\/profile/);

    await ops.getByTestId('admin-password-current').fill(firstPassword);
    await ops.getByTestId('admin-password-submit').click();
    await expect(ops.getByTestId('admin-password-success')).toBeVisible();
    await expect(ops.getByTestId('admin-password-current')).toHaveValue('');

    // esta sesión sigue (token nuevo guardado); la otra se cerró; la contraseña vieja ya no entra
    await ops.reload();
    await expect(ops.getByTestId('admin-profile-title')).toBeVisible();
    await expect(ops.getByTestId('admin-profile-email')).toHaveText(opsEmail);
    expect((await call(request, 'GET', '/api/admin/me', otherSession)).code).toBe('UNAUTHORIZED');
    expect((await call(request, 'POST', '/api/admin/auth/login', undefined, { email: opsEmail, password: firstPassword })).code)
      .toBe('INVALID_CREDENTIALS');

    // ---- el superadmin lo desactiva desde «Administradores»
    const superCtx = await browser.newContext({ ...devices['Desktop Chrome'], locale: 'es-ES', baseURL: BASE });
    const admin = await superCtx.newPage();
    await uiLogin(admin, ADMIN_EMAIL, ADMIN_PASSWORD);
    await expect(admin).toHaveURL(/\/admin\/?$/);
    await admin.getByTestId('admin-menu-admins').click();
    await expect(admin.getByTestId(`admin-admins-status-${opsId}`)).toHaveAttribute('data-status', 'ACTIVE');
    const superId = (await call<{ id: number }>(request, 'GET', '/api/admin/me', superToken)).data.id;
    // nadie puede desactivarse a sí mismo: no hay botón en su propia fila
    await expect(admin.getByTestId(`admin-admins-row-${superId}`)).toBeVisible();
    await expect(admin.getByTestId(`admin-admins-disable-${superId}`)).toHaveCount(0);

    await admin.getByTestId(`admin-admins-disable-${opsId}`).click();
    await admin.locator('.el-message-box__btns .el-button--primary').click();
    await expect(admin.getByTestId(`admin-admins-status-${opsId}`)).toHaveAttribute('data-status', 'DISABLED');
    await expect(admin.getByTestId(`admin-admins-enable-${opsId}`)).toBeVisible();

    // la sesión abierta del administrador desactivado deja de valer en la siguiente solicitud
    await ops.getByTestId('admin-menu-dashboard').click();
    await expect(ops).toHaveURL(/\/admin\/login/);
    const answer = ops.waitForResponse((r) => r.url().includes('/api/admin/auth/login'));
    await ops.getByTestId('admin-login-email').fill(opsEmail);
    await ops.getByTestId('admin-login-password').fill(secondPassword);
    await ops.getByTestId('admin-login-submit').click();
    expect(((await (await answer).json()) as Envelope<unknown>).code).toBe('ACCOUNT_DISABLED');
    await expect(ops.getByTestId('admin-login-error')).toBeVisible();

    // ---- reactivado: vuelve a entrar con la contraseña nueva
    await admin.getByTestId(`admin-admins-enable-${opsId}`).click();
    await expect(admin.getByTestId(`admin-admins-status-${opsId}`)).toHaveAttribute('data-status', 'ACTIVE');
    await uiLogin(ops, opsEmail, secondPassword);
    await expect(ops).toHaveURL(/\/admin\/?$/);

    const audit = await call<{ records: { action: string; entityId: string }[] }>(request, 'GET',
      '/api/admin/audit?size=50', superToken);
    const actions = audit.data.records.filter((r) => r.entityId === String(opsId)).map((r) => r.action);
    expect(actions).toEqual(expect.arrayContaining(['ADMIN_PASSWORD_CHANGED', 'ADMIN_PASSWORD_CHANGE_FAILED', 'ADMIN_DISABLED', 'ADMIN_ENABLED']));

    await opsCtx.close();
    await superCtx.close();
  });
});
