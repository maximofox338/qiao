import { api, asList } from './http'
import type {
  Application,
  Category,
  CheckoutResponse,
  Company,
  Contract,
  EngineerCv,
  LegalDoc,
  LegalText,
  Me,
  Message,
  Attachment,
  Page,
  Payment,
  Project,
  ProjectInput,
  PublicConfig,
  ReviewSummary,
  VerifyResponse,
} from './types'

/* Public */
export const publicApi = {
  categories: () => api.get<Category[]>('/api/public/categories'),
  legal: (doc: LegalDoc | string, locale: string) => api.get<LegalText>(`/api/public/legal/${encodeURIComponent(doc)}`, { locale }),
  config: () => api.get<PublicConfig>('/api/public/config'),
}

/* Auth (users) */
export interface VerifyInput {
  email: string
  code: string
  role?: 'CLIENT'
  locale?: string
  acceptTerms?: boolean
  termsVersion?: string
  privacyVersion?: string
}

export const authApi = {
  requestOtp: (email: string, locale: string) =>
    api.post<{ sent: boolean }>('/api/auth/otp/request', { email, locale }, { skipAuthRedirect: true }),
  verifyOtp: (input: VerifyInput) => api.post<VerifyResponse>('/api/auth/otp/verify', input, { skipAuthRedirect: true }),
  logout: () => api.post<unknown>('/api/auth/logout', {}, { skipAuthRedirect: true }),
}

/* Me */
export const meApi = {
  get: (opts?: { skipAuthRedirect?: boolean }) => api.get<Me>('/api/me', undefined, opts),
  update: (locale: string) => api.put<Me>('/api/me', { locale }),
  consents: () => api.get<unknown[]>('/api/me/consents'),
  delete: () => api.post<{ deleted: boolean }>('/api/me/delete'),
}

/* Client */
export const clientApi = {
  company: () => api.get<Company | null>('/api/client/company'),
  saveCompany: (c: Pick<Company, 'name' | 'country' | 'website' | 'size'>) => api.put<Company>('/api/client/company', c),
  acceptDeclarations: (version: string) =>
    api.post<Company>('/api/client/company/declarations', { version, accepted: true }),

  projects: async () => asList(await api.get<Project[] | Page<Project>>('/api/client/projects', { page: 1, size: 100 })),
  project: (id: number | string) => api.get<Project>(`/api/client/projects/${id}`),
  createProject: (p: ProjectInput) => api.post<Project>('/api/client/projects', p),
  updateProject: (id: number | string, p: ProjectInput) => api.put<Project>(`/api/client/projects/${id}`, p),
  submitProject: (id: number | string) => api.post<Project>(`/api/client/projects/${id}/submit`),

  applications: async (projectId: number | string) =>
    asList(await api.get<Application[] | Page<Application>>(`/api/client/projects/${projectId}/applications`)),
  shortlist: (id: number) => api.post<Application>(`/api/client/applications/${id}/shortlist`),
  decline: (id: number) => api.post<Application>(`/api/client/applications/${id}/decline`),
  select: (id: number) => api.post<Application>(`/api/client/applications/${id}/select`),
  hire: (id: number) => api.post<Contract & { contractId?: number }>(`/api/client/applications/${id}/hire`),
  /** For a team application `alias` picks the member (each member consents for their own CV). */
  cv: (id: number, alias?: string) => api.get<EngineerCv>(`/api/client/applications/${id}/cv`, alias ? { alias } : undefined),

  contracts: async () => asList(await api.get<Contract[] | Page<Contract>>('/api/client/contracts', { page: 1, size: 100 })),
  contract: (id: number | string) => api.get<Contract>(`/api/client/contracts/${id}`),
  acceptDocument: (contractId: number | string, docId: number) =>
    api.post<unknown>(`/api/client/contracts/${contractId}/documents/${docId}/accept`),

  review: (contractId: number | string, stars: number, comment: string) =>
    api.post<ReviewSummary>(`/api/client/contracts/${contractId}/review`, { stars, comment }),

  checkout: (milestoneId: number) => api.post<CheckoutResponse>(`/api/client/milestones/${milestoneId}/checkout`),
  acceptMilestone: (milestoneId: number) => api.post<unknown>(`/api/client/milestones/${milestoneId}/accept`),
  requestRevision: (milestoneId: number, reason: string) =>
    api.post<unknown>(`/api/client/milestones/${milestoneId}/request-revision`, { reason }),
  dispute: (milestoneId: number, reason: string) =>
    api.post<unknown>(`/api/client/milestones/${milestoneId}/dispute`, { reason }),
}

/* Contract participants */
export const contractApi = {
  messages: async (contractId: number | string) =>
    asList(await api.get<Message[] | Page<Message>>(`/api/contracts/${contractId}/messages`)),
  sendMessage: (contractId: number | string, body: string) =>
    api.post<Message>(`/api/contracts/${contractId}/messages`, { body }),
  upload: (contractId: number | string, file: File, milestoneId?: number | null) => {
    const form = new FormData()
    form.append('file', file)
    if (milestoneId) form.append('milestoneId', String(milestoneId))
    return api.upload<Attachment>(`/api/contracts/${contractId}/attachments`, form)
  },
  attachmentUrl: (id: number) => `/api/attachments/${id}`,
}

/* Payments */
export const paymentApi = {
  get: (id: number | string) => api.get<Payment>(`/api/payments/${id}`),
  confirmFake: (id: number | string) => api.post<Payment>(`/api/payments/fake/${id}/confirm`),
}
