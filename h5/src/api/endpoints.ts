// Every endpoint the engineer app uses, exactly as listed in docs/SPEC.md §7.
import { asList, download, http } from './client'
import type {
  Application,
  Attachment,
  Category,
  ConsentRecord,
  ConsentType,
  ContractDetail,
  ContractSummary,
  Earning,
  EngineerProfile,
  EngineerProfileInput,
  Id,
  LegalDoc,
  LegalDocName,
  Me,
  Message,
  Page,
  Project,
  PublicConfig,
  ReviewSummary,
  Team,
  TeamInvitation,
} from './types'

export const publicApi = {
  categories: () => http.get<Category[]>('/public/categories'),
  legal: (doc: LegalDocName | string, locale: string) =>
    http.get<LegalDoc>(`/public/legal/${encodeURIComponent(doc)}`, { locale }),
  config: () => http.get<PublicConfig>('/public/config'),
}

export interface VerifyOtpBody {
  email: string
  code: string
  role?: 'ENGINEER'
  locale?: string
  acceptTerms?: boolean
  termsVersion?: string
  privacyVersion?: string
}

export const authApi = {
  requestOtp: (email: string, locale: string) =>
    http.post<{ sent: boolean }>('/auth/otp/request', { email, locale }, { skipAuthRedirect: true }),
  verifyOtp: (body: VerifyOtpBody) =>
    http.post<{ token: string; user: Me }>('/auth/otp/verify', body, { skipAuthRedirect: true }),
  logout: () => http.post<unknown>('/auth/logout', undefined, { skipAuthRedirect: true }),
}

export const meApi = {
  get: () => http.get<Me>('/me'),
  setLocale: (locale: string) => http.put<unknown>('/me', { locale }),
  consents: async () => asList(await http.get<ConsentRecord[] | Page<ConsentRecord>>('/me/consents')),
  setConsent: (type: ConsentType, granted: boolean, scopeRef?: Id) =>
    http.post<unknown>('/me/consents', scopeRef === undefined ? { type, granted } : { type, granted, scopeRef: String(scopeRef) }),
  exportData: () => download('/me/export', 'qiao-my-data.json'),
  deleteAccount: () => http.post<{ deleted: boolean }>('/me/delete'),
}

export interface ProjectQuery {
  q?: string
  category?: string
  page?: number
  size?: number
}

export const engineerApi = {
  profile: () => http.get<EngineerProfile | null>('/engineer/profile'),
  saveProfile: (body: EngineerProfileInput) => http.put<EngineerProfile>('/engineer/profile', body),
  submitProfile: () => http.post<EngineerProfile | null>('/engineer/profile/submit'),

  projects: (q: ProjectQuery) => http.get<Page<Project> | Project[]>('/engineer/projects', { ...q }),
  project: (id: Id) => http.get<Project>(`/engineer/projects/${id}`),
  apply: (projectId: Id, body: { coverNote: string; prices: { milestoneId: Id; talentAmountUsd: number }[] }) =>
    http.post<Application>(`/engineer/projects/${projectId}/applications`, body),

  applications: async () => asList(await http.get<Application[] | Page<Application>>('/engineer/applications')),
  withdraw: (id: Id) => http.post<unknown>(`/engineer/applications/${id}/withdraw`),
  cvConsent: (id: Id, granted: boolean) => http.post<unknown>(`/engineer/applications/${id}/cv-consent`, { granted }),

  contracts: async () => asList(await http.get<ContractSummary[] | Page<ContractSummary>>('/engineer/contracts')),
  contract: (id: Id) => http.get<ContractDetail>(`/engineer/contracts/${id}`),
  acceptDocument: (contractId: Id, docId: Id) =>
    http.post<unknown>(`/engineer/contracts/${contractId}/documents/${docId}/accept`),
  submitMilestone: (id: Id, note: string, attachmentIds: Id[]) =>
    http.post<unknown>(`/engineer/milestones/${id}/submit`, { note, attachmentIds }),

  earnings: async () => asList(await http.get<Earning[] | Page<Earning>>('/engineer/earnings')),
  review: (contractId: Id, stars: number, comment: string) =>
    http.post<ReviewSummary>(`/engineer/contracts/${contractId}/review`, { stars, comment }),

  applyAsTeam: (
    projectId: Id,
    body: {
      teamId: Id
      coverNote: string
      prices: { milestoneId: Id; talentAmountUsd: number }[]
      shares: { alias: string; sharePct: number }[]
    },
  ) => http.post<Application>(`/engineer/projects/${projectId}/team-applications`, body),
}

/** Teams (docs/SPEC.md §12). */
export const teamApi = {
  list: async () => asList(await http.get<Team[] | Page<Team>>('/engineer/teams')),
  get: (id: Id) => http.get<Team>(`/engineer/teams/${id}`),
  create: (body: { name: string; description: string }) => http.post<Team>('/engineer/teams', body),
  update: (id: Id, body: { name: string; description: string }) => http.put<Team>(`/engineer/teams/${id}`, body),
  invite: (id: Id, alias: string) => http.post<Team>(`/engineer/teams/${id}/invitations`, { alias }),
  invitations: async () => asList(await http.get<TeamInvitation[] | Page<TeamInvitation>>('/engineer/team-invitations')),
  accept: (id: Id) => http.post<Team>(`/engineer/teams/${id}/invitations/accept`),
  decline: (id: Id) => http.post<unknown>(`/engineer/teams/${id}/invitations/decline`),
  remove: (id: Id, memberId: Id) => http.post<Team>(`/engineer/teams/${id}/members/${memberId}/remove`),
  leave: (id: Id) => http.post<unknown>(`/engineer/teams/${id}/leave`),
  dissolve: (id: Id) => http.post<unknown>(`/engineer/teams/${id}/dissolve`),
}

export const contractApi = {
  messages: async (id: Id) =>
    asList(await http.get<Message[] | Page<Message>>(`/contracts/${id}/messages`, { page: 1, size: 100 })),
  sendMessage: (id: Id, body: string) => http.post<Message>(`/contracts/${id}/messages`, { body }),
  upload: (id: Id, file: File, milestoneId?: Id) => {
    const form = new FormData()
    form.append('file', file)
    if (milestoneId !== undefined) form.append('milestoneId', String(milestoneId))
    return http.upload<Attachment>(`/contracts/${id}/attachments`, form)
  },
}
