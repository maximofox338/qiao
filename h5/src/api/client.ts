// Thin fetch wrapper: same-origin `/api` only (never an absolute URL), unwraps the
// `{code, message, data}` envelope from docs/SPEC.md §3 and throws ApiError otherwise.
import type { Envelope, Page } from './types'
import { storage } from '@/utils/storage'

const BASE = '/api'
const TOKEN_KEY = 'qiao.h5.token'

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
  /** `data.fields` of a VALIDATION_ERROR: { field: code } */
  get fields(): Record<string, string> {
    const d = this.data as { fields?: Record<string, string> } | null
    return d && typeof d === 'object' && d.fields ? d.fields : {}
  }
}

let token: string | null = storage.get(TOKEN_KEY)
let unauthorizedHandler: (() => void) | null = null
let localeProvider: () => string = () => 'zh-CN'

export function getToken() {
  return token
}
export function setToken(value: string | null) {
  token = value
  if (value) storage.set(TOKEN_KEY, value)
  else storage.remove(TOKEN_KEY)
}
export function onUnauthorized(handler: () => void) {
  unauthorizedHandler = handler
}
export function setLocaleProvider(fn: () => string) {
  localeProvider = fn
}

type Query = Record<string, string | number | boolean | null | undefined>

export interface RequestOptions {
  query?: Query
  body?: unknown
  form?: FormData
  /** Do not trigger the global 401 → login redirect (auth endpoints). */
  skipAuthRedirect?: boolean
  signal?: AbortSignal
}

function buildUrl(path: string, query?: Query) {
  let url = BASE + path
  if (query) {
    const qs = new URLSearchParams()
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined && v !== null && v !== '') qs.append(k, String(v))
    }
    const s = qs.toString()
    if (s) url += (url.includes('?') ? '&' : '?') + s
  }
  return url
}

async function send(method: string, path: string, opts: RequestOptions = {}): Promise<Response> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
    'Accept-Language': localeProvider(),
  }
  if (token) headers.Authorization = `Bearer ${token}`
  let body: BodyInit | undefined
  if (opts.form) {
    body = opts.form
  } else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(opts.body)
  }
  try {
    return await fetch(buildUrl(path, opts.query), {
      method,
      headers,
      body,
      credentials: 'same-origin',
      signal: opts.signal,
    })
  } catch (e) {
    if ((e as Error)?.name === 'AbortError') throw e
    throw new ApiError('NETWORK_ERROR', 'Network error', 0)
  }
}

function handleFailure(res: Response, env: Partial<Envelope<unknown>> | null, opts: RequestOptions): never {
  const code = env?.code && env.code !== 'OK' ? env.code : statusToCode(res.status)
  if ((res.status === 401 || code === 'UNAUTHORIZED') && !opts.skipAuthRedirect) {
    setToken(null)
    unauthorizedHandler?.()
  }
  throw new ApiError(code, env?.message ?? res.statusText, res.status, env?.data ?? null)
}

function statusToCode(status: number) {
  switch (status) {
    case 400:
      return 'VALIDATION_ERROR'
    case 401:
      return 'UNAUTHORIZED'
    case 403:
      return 'FORBIDDEN'
    case 404:
      return 'NOT_FOUND'
    case 413:
      return 'FILE_TOO_LARGE'
    case 429:
      return 'RATE_LIMITED'
    default:
      return status >= 500 ? 'SERVER_ERROR' : 'UNKNOWN_ERROR'
  }
}

async function parseEnvelope(res: Response): Promise<Partial<Envelope<unknown>> | null> {
  const text = await res.text()
  if (!text) return null
  try {
    return JSON.parse(text) as Envelope<unknown>
  } catch {
    return null
  }
}

export async function request<T>(method: string, path: string, opts: RequestOptions = {}): Promise<T> {
  const res = await send(method, path, opts)
  const env = await parseEnvelope(res)
  if (!res.ok || !env || env.code !== 'OK') handleFailure(res, env, opts)
  return env.data as T
}

/** Binary/attachment download (e.g. GET /api/me/export). Errors still come as an envelope. */
export async function download(path: string, fallbackName: string): Promise<{ blob: Blob; filename: string }> {
  const res = await send('GET', path)
  if (!res.ok) handleFailure(res, await parseEnvelope(res), {})
  const disposition = res.headers.get('Content-Disposition') || ''
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(disposition)
  const filename = match ? decodeURIComponent(match[1]) : fallbackName
  return { blob: await res.blob(), filename }
}

export const http = {
  get: <T>(path: string, query?: Query, opts?: RequestOptions) => request<T>('GET', path, { ...opts, query }),
  post: <T>(path: string, body?: unknown, opts?: RequestOptions) => request<T>('POST', path, { ...opts, body }),
  put: <T>(path: string, body?: unknown, opts?: RequestOptions) => request<T>('PUT', path, { ...opts, body }),
  upload: <T>(path: string, form: FormData) => request<T>('POST', path, { form }),
}

/** Lists in the spec are sometimes paginated (`{records,...}`) and sometimes plain arrays; accept both. */
export function asList<T>(data: T[] | Page<T> | null | undefined): T[] {
  if (!data) return []
  if (Array.isArray(data)) return data
  return Array.isArray(data.records) ? data.records : []
}
