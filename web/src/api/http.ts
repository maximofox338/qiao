import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import type { Envelope, Page } from './types'

/* ------------------------------------------------------------------ */
/* Tokens: user (client portal) and admin are stored separately.       */
/* ------------------------------------------------------------------ */

const USER_TOKEN_KEY = 'qiao.user.token'
const ADMIN_TOKEN_KEY = 'qiao.admin.token'

function readStorage(key: string): string | null {
  try {
    return window.localStorage.getItem(key)
  } catch {
    return null
  }
}

function writeStorage(key: string, value: string | null) {
  try {
    if (value) window.localStorage.setItem(key, value)
    else window.localStorage.removeItem(key)
  } catch {
    /* storage unavailable: session keeps the in-memory token only */
  }
}

export type AuthArea = 'user' | 'admin'

export const tokens = {
  get(area: AuthArea): string | null {
    return readStorage(area === 'admin' ? ADMIN_TOKEN_KEY : USER_TOKEN_KEY)
  },
  set(area: AuthArea, token: string | null) {
    writeStorage(area === 'admin' ? ADMIN_TOKEN_KEY : USER_TOKEN_KEY, token)
  },
}

/** Admin endpoints use the admin token; everything else uses the user token. */
export function areaForUrl(url: string): AuthArea {
  return url.startsWith('/api/admin') ? 'admin' : 'user'
}

/* ------------------------------------------------------------------ */
/* Errors                                                             */
/* ------------------------------------------------------------------ */

export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly data: unknown

  constructor(code: string, message: string, status: number, data: unknown = null) {
    super(message || code)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.data = data
  }

  /** Field errors of a VALIDATION_ERROR (`data.fields`), keys normalised to dot paths (`milestones.0.amountUsd`). */
  get fields(): Record<string, string> {
    const raw = (this.data as { fields?: Record<string, string> } | null)?.fields
    if (!raw || typeof raw !== 'object') return {}
    const out: Record<string, string> = {}
    for (const [k, v] of Object.entries(raw)) out[normaliseFieldPath(k)] = String(v)
    return out
  }
}

export function normaliseFieldPath(path: string): string {
  return path.replace(/\[(\d+)\]/g, '.$1').replace(/^\./, '')
}

export function isApiError(e: unknown, code?: string): e is ApiError {
  return e instanceof ApiError && (code === undefined || e.code === code)
}

function isEnvelope(body: unknown): body is Envelope<unknown> {
  return !!body && typeof body === 'object' && 'code' in body && typeof (body as Envelope<unknown>).code === 'string'
}

/* ------------------------------------------------------------------ */
/* 401 handling                                                        */
/* ------------------------------------------------------------------ */

type UnauthorizedHandler = (area: AuthArea) => void
let unauthorizedHandler: UnauthorizedHandler = () => {}

export function setUnauthorizedHandler(fn: UnauthorizedHandler) {
  unauthorizedHandler = fn
}

/* ------------------------------------------------------------------ */
/* Axios instance                                                      */
/* ------------------------------------------------------------------ */

export interface RequestOptions {
  /** Do not redirect to login on 401 (used by login endpoints themselves). */
  skipAuthRedirect?: boolean
  /** Force the token area (defaults to the URL prefix). */
  area?: AuthArea
}

const http = axios.create({
  baseURL: '',
  timeout: 60_000,
  headers: { Accept: 'application/json' },
})

function authHeader(area: AuthArea): Record<string, string> {
  const token = tokens.get(area)
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function toApiError(err: unknown): ApiError {
  if (err instanceof ApiError) return err
  if (err instanceof AxiosError) {
    const status = err.response?.status ?? 0
    const body = err.response?.data
    if (isEnvelope(body)) return new ApiError(body.code, body.message, status, body.data)
    if (!err.response) return new ApiError('NETWORK_ERROR', err.message, 0)
    const fallback =
      status === 401
        ? 'UNAUTHORIZED'
        : status === 403
          ? 'FORBIDDEN'
          : status === 404
            ? 'NOT_FOUND'
            : status === 429
              ? 'RATE_LIMITED'
              : status >= 500
                ? 'INTERNAL_ERROR'
                : 'UNKNOWN_ERROR'
    return new ApiError(fallback, err.message, status)
  }
  return new ApiError('UNKNOWN_ERROR', err instanceof Error ? err.message : String(err), 0)
}

function handleAuthFailure(e: ApiError, area: AuthArea, opts: RequestOptions) {
  if ((e.status === 401 || e.code === 'UNAUTHORIZED') && !opts.skipAuthRedirect) {
    tokens.set(area, null)
    unauthorizedHandler(area)
  }
}

export async function request<T>(config: AxiosRequestConfig, opts: RequestOptions = {}): Promise<T> {
  const url = config.url ?? ''
  const area = opts.area ?? areaForUrl(url)
  try {
    const res = await http.request({
      ...config,
      headers: { ...authHeader(area), ...(config.headers as Record<string, string> | undefined) },
    })
    const body = res.data
    if (isEnvelope(body)) {
      if (body.code !== 'OK') throw new ApiError(body.code, body.message, res.status, body.data)
      return body.data as T
    }
    return body as T
  } catch (err) {
    const e = toApiError(err)
    handleAuthFailure(e, area, opts)
    throw e
  }
}

export const api = {
  get<T>(url: string, params?: Record<string, unknown>, opts?: RequestOptions) {
    return request<T>({ method: 'GET', url, params: cleanParams(params) }, opts)
  },
  post<T>(url: string, data?: unknown, opts?: RequestOptions) {
    return request<T>({ method: 'POST', url, data: data ?? {} }, opts)
  },
  put<T>(url: string, data?: unknown, opts?: RequestOptions) {
    return request<T>({ method: 'PUT', url, data: data ?? {} }, opts)
  },
  upload<T>(url: string, form: FormData, opts?: RequestOptions) {
    return request<T>({ method: 'POST', url, data: form }, opts)
  },
}

function cleanParams(params?: Record<string, unknown>) {
  if (!params) return undefined
  const out: Record<string, unknown> = {}
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== null && v !== '') out[k] = v
  return out
}

/* ------------------------------------------------------------------ */
/* Authenticated downloads (CSV export, attachments, data export)      */
/* ------------------------------------------------------------------ */

function filenameFromDisposition(header: string | null): string | null {
  if (!header) return null
  const star = /filename\*=(?:UTF-8'')?([^;]+)/i.exec(header)
  if (star?.[1]) {
    try {
      return decodeURIComponent(star[1].trim().replace(/^"|"$/g, ''))
    } catch {
      /* ignore */
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(header)
  return plain?.[1]?.trim() ?? null
}

export async function downloadFile(url: string, fallbackName: string, opts: RequestOptions = {}): Promise<void> {
  const area = opts.area ?? areaForUrl(url)
  let res: Response
  try {
    res = await fetch(url, { headers: { ...authHeader(area) }, credentials: 'same-origin' })
  } catch (err) {
    throw new ApiError('NETWORK_ERROR', err instanceof Error ? err.message : String(err), 0)
  }
  const type = res.headers.get('Content-Type') ?? ''
  if (!res.ok || type.includes('application/json')) {
    let e: ApiError | null = null
    if (type.includes('application/json')) {
      const body = await res.json().catch(() => null)
      if (isEnvelope(body) && body.code !== 'OK') e = new ApiError(body.code, body.message, res.status, body.data)
      else if (res.ok) {
        // A JSON payload that is a file (e.g. data export): save it as-is.
        const payload = isEnvelope(body) ? body.data : body
        saveBlob(new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' }), filenameFromDisposition(res.headers.get('Content-Disposition')) ?? fallbackName)
        return
      }
    }
    if (!e) e = new ApiError(res.status === 401 ? 'UNAUTHORIZED' : res.status === 404 ? 'NOT_FOUND' : 'UNKNOWN_ERROR', res.statusText, res.status)
    handleAuthFailure(e, area, opts)
    throw e
  }
  const blob = await res.blob()
  saveBlob(blob, filenameFromDisposition(res.headers.get('Content-Disposition')) ?? fallbackName)
}

export function saveBlob(blob: Blob, filename: string) {
  const href = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = href
  a.download = filename
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(href), 1000)
}

/* ------------------------------------------------------------------ */
/* Helpers                                                             */
/* ------------------------------------------------------------------ */

/** Lists may come as a plain array or as a page; normalise to an array. */
export function asList<T>(data: T[] | Page<T> | null | undefined): T[] {
  if (!data) return []
  if (Array.isArray(data)) return data
  return Array.isArray(data.records) ? data.records : []
}

export function asPage<T>(data: T[] | Page<T> | null | undefined, page = 1, size = 20): Page<T> {
  if (!data) return { records: [], total: 0, page, size }
  if (Array.isArray(data)) return { records: data, total: data.length, page: 1, size: data.length || size }
  return { records: data.records ?? [], total: data.total ?? 0, page: data.page ?? page, size: data.size ?? size }
}
