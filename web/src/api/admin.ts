import { api, asList, asPage } from './http'
import type {
  AdminAccount,
  AdminCategory,
  AdminCompany,
  AdminDashboard,
  AdminEngineer,
  AdminInfo,
  AdminLoginResponse,
  AdminRole,
  AuditEntry,
  Dispute,
  OutboxMail,
  Page,
  Project,
  SettlementBatch,
  TransferSummary,
} from './types'

type ListOrPage<T> = T[] | Page<T>

export const adminApi = {
  login: (email: string, password: string, totp?: string) =>
    api.post<AdminLoginResponse>(
      '/api/admin/auth/login',
      { email, password, ...(totp ? { totp } : {}) },
      { skipAuthRedirect: true },
    ),
  me: () => api.get<AdminInfo>('/api/admin/me'),
  dashboard: () => api.get<AdminDashboard>('/api/admin/dashboard'),

  companies: async (status: string, page: number, size: number) =>
    asPage(await api.get<ListOrPage<AdminCompany>>('/api/admin/companies', { status, page, size }), page, size),
  company: (id: number) => api.get<AdminCompany>(`/api/admin/companies/${id}`),
  reviewCompany: (id: number, body: { decision: 'APPROVE' | 'REJECT'; sanctionsStatus: 'CLEAR' | 'HIT'; note: string }) =>
    api.post<AdminCompany>(`/api/admin/companies/${id}/review`, body),

  engineers: async (status: string, page: number, size: number) =>
    asPage(await api.get<ListOrPage<AdminEngineer>>('/api/admin/engineers', { status, page, size }), page, size),
  engineer: (id: number) => api.get<AdminEngineer>(`/api/admin/engineers/${id}`),
  reviewEngineer: (id: number, body: { decision: 'APPROVE' | 'REJECT'; note: string; partnerRef?: string }) =>
    api.post<AdminEngineer>(`/api/admin/engineers/${id}/review`, body),

  projects: async (status: string, page: number, size: number) =>
    asPage(await api.get<ListOrPage<Project>>('/api/admin/projects', { status, page, size }), page, size),
  project: (id: number) => api.get<Project>(`/api/admin/projects/${id}`),
  reviewProject: (id: number, body: { decision: 'APPROVE' | 'REJECT' | 'REQUEST_CHANGES'; note: string }) =>
    api.post<Project>(`/api/admin/projects/${id}/review`, body),

  categories: async () => asList(await api.get<ListOrPage<AdminCategory>>('/api/admin/categories')),
  updateCategory: (code: string, body: { allowed: boolean; nameEn: string; nameEs: string; nameZh: string }) =>
    api.put<AdminCategory>(`/api/admin/categories/${encodeURIComponent(code)}`, body),

  disputes: async () => asList(await api.get<ListOrPage<Dispute>>('/api/admin/disputes')),
  resolveDispute: (milestoneId: number, resolution: 'ACCEPT' | 'REFUND', note: string) =>
    api.post<unknown>(`/api/admin/milestones/${milestoneId}/resolve`, { resolution, note }),

  createSettlement: (period: string) => api.post<SettlementBatch>('/api/admin/settlements', { period }),
  settlements: async () => asList(await api.get<ListOrPage<SettlementBatch>>('/api/admin/settlements')),
  settlement: (id: number) => api.get<SettlementBatch>(`/api/admin/settlements/${id}`),
  settlementCsvUrl: (id: number) => `/api/admin/settlements/${id}/export.csv`,
  markExported: (id: number) => api.post<SettlementBatch>(`/api/admin/settlements/${id}/mark-exported`),

  audit: async (filters: { action?: string; actorType?: string }, page: number, size: number) =>
    asPage(await api.get<ListOrPage<AuditEntry>>('/api/admin/audit', { ...filters, page, size }), page, size),
  transfers: (year: number) => api.get<TransferSummary>('/api/admin/transfers/summary', { year }),

  outbox: async (page: number, size: number) =>
    asPage(await api.get<ListOrPage<OutboxMail>>('/api/admin/outbox', { page, size }), page, size),

  admins: async () => asList(await api.get<ListOrPage<AdminAccount>>('/api/admin/admins')),
  createAdmin: (body: { email: string; password: string; roles: AdminRole[] }) =>
    api.post<AdminAccount>('/api/admin/admins', body),
}
