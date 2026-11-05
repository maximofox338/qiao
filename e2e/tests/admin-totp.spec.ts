import { test, expect, devices, type APIRequestContext } from '@playwright/test';
import { createHmac } from 'node:crypto';

/**
 * TOTP del back-office: un administrador de operaciones (creado por API para no tocar al superadmin de los otros
 * flujos) activa la verificación en dos pasos desde «Mi perfil» con el QR dibujado localmente, entra con un código y
 * no puede reutilizarlo; el superadmin la restablece.
 */

const API = process.env.API_URL ?? 'http://127.0.0.1:8080';
const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? 'admin@e2e.qiao';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD ?? 'E2e-Admin-Password-123';
const BASE = process.env.BASE_URL ?? 'http://localhost:8088';

const run = Date.now().toString(36);
const opsEmail = `ops.totp.${run}@e2e.qiao`;
const opsPassword = 'Ops-Totp-Password-2026';

type Envelope<T> = { code: string; message: string; data: T };

async function call<T>(request: APIRequestContext, method: string, path: string, token?: string, body?: unknown): Promise<Envelope<T>> {
  const res = await request.fetch(`${API}${path}`, { method, headers: token ? { Authorization: `Bearer ${token}` } : {}, data: body });
  return (await res.json()) as Envelope<T>;
}

function base32Decode(secret: string): Buffer {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = 0;
  let value = 0;
  const out: number[] = [];
  for (const c of secret.replace(/=+$/, '').toUpperCase()) {
    value = (value << 5) | alphabet.indexOf(c);
    bits += 5;
    if (bits >= 8) {
      out.push((value >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(out);
}

/** RFC 6238 code (SHA-1, 6 digits, 30 s) of a given time-step, as an authenticator app computes it. */
function totp(secret: string, step: number): string {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const h = createHmac('sha1', base32Decode(secret)).update(counter).digest();
  const o = h[h.length - 1] & 0x0f;
  const bin = ((h[o] & 0x7f) << 24) | (h[o + 1] << 16) | (h[o + 2] << 8) | h[o + 3];
  return String(bin % 1_000_000).padStart(6, '0');
}

/** Current step, waiting for a fresh one when less than 10 s remain so step ± 1 stays valid for the whole test. */
async function alignedStep(): Promise<number> {
  const into = Date.now() % 30_000;
  if (into > 20_000) await new Promise((r) => setTimeout(r, 30_000 - into + 300));
  return Math.floor(Date.now() / 30_000);
}

test.describe.serial('Qiao — TOTP del back-office', () => {
  test('el administrador activa TOTP con el QR, entra con un código y no puede reutilizarlo', async ({ browser, request }) => {
    const superadmin = (await call<{ token: string }>(request, 'POST', '/api/admin/auth/login', undefined,
      { email: ADMIN_EMAIL, password: ADMIN_PASSWORD })).data.token;
    const created = await call<{ id: number }>(request, 'POST', '/api/admin/admins', superadmin,
      { email: opsEmail, password: opsPassword, roles: ['OPS'] });
    expect(created.code).toBe('OK');

    const ctx = await browser.newContext({ ...devices['Desktop Chrome'], locale: 'en-US', baseURL: BASE });
    const external: string[] = [];
    ctx.on('request', (req) => {
      const host = new URL(req.url()).hostname;
      if (!['localhost', '127.0.0.1'].includes(host) && !req.url().startsWith('data:') && !req.url().startsWith('blob:')) external.push(req.url());
    });
    const page = await ctx.newPage();
    const login = async (code?: string) => {
      await page.goto('/admin/login');
      await page.getByTestId('admin-login-email').fill(opsEmail);
      await page.getByTestId('admin-login-password').fill(opsPassword);
      if (code) await page.getByTestId('admin-login-totp').fill(code);
      await page.getByTestId('admin-login-submit').click();
    };

    await login();
    await expect(page).toHaveURL(/\/admin\/?$/);
    await page.getByTestId('admin-menu-profile').click();
    await expect(page.getByTestId('admin-totp-status')).toHaveAttribute('data-status', 'DISABLED');
    await page.getByTestId('admin-totp-setup').click();
    await expect(page.getByTestId('admin-totp-qr')).toHaveAttribute('src', /^data:image\/png;base64,/);
    const secret = (await page.getByTestId('admin-totp-secret').textContent())!.trim();
    expect(secret).toMatch(/^[A-Z2-7]{32}$/);
    expect(await page.getByTestId('admin-totp-uri').inputValue()).toContain(`secret=${secret}`);

    const step = await alignedStep();
    await page.getByTestId('admin-totp-code').fill(totp(secret, step + 3));
    await page.getByTestId('admin-totp-enable').click();
    await expect(page.getByTestId('admin-totp-error')).toBeVisible();
    await page.getByTestId('admin-totp-code').fill(totp(secret, step));
    await page.getByTestId('admin-totp-enable').click();
    await expect(page.getByTestId('admin-totp-status')).toHaveAttribute('data-status', 'ENABLED');

    // sin código no entra; el código ya usado (mismo paso de 30 s) tampoco; el siguiente sí
    await page.getByTestId('admin-logout').click();
    const loginAnswer = async () => {
      const res = await page.waitForResponse((r) => r.url().includes('/api/admin/auth/login'));
      return ((await res.json()) as Envelope<unknown>).code;
    };
    let answer = loginAnswer();
    await login();
    expect(await answer).toBe('TOTP_REQUIRED');
    await expect(page.getByTestId('admin-login-error')).toBeVisible();
    answer = loginAnswer();
    await page.getByTestId('admin-login-totp').fill(totp(secret, step));
    await page.getByTestId('admin-login-submit').click();
    expect(await answer).toBe('TOTP_INVALID');
    await expect(page).toHaveURL(/\/admin\/login/);
    await page.getByTestId('admin-login-totp').fill(totp(secret, step + 1));
    await page.getByTestId('admin-login-submit').click();
    await expect(page).toHaveURL(/\/admin\/?$/);

    // el superadmin restablece la verificación (p. ej., teléfono perdido)
    const reset = await call<{ totpEnabled: boolean }>(request, 'POST', `/api/admin/admins/${created.data.id}/totp/reset`, superadmin);
    expect(reset.data.totpEnabled).toBe(false);
    const plain = await call<{ token: string }>(request, 'POST', '/api/admin/auth/login', undefined, { email: opsEmail, password: opsPassword });
    expect(plain.code).toBe('OK');

    expect(external).toEqual([]);
    await ctx.close();
  });
});
