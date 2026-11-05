// Shapes from docs/SPEC.md §3, §5 and §7. Ids are BIGINT on the server; accept number or string.
export type Id = number | string

export interface Envelope<T> {
  code: string
  message: string
  data: T
}

export interface Page<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export type Role = 'CLIENT' | 'ENGINEER'
export type ProfileStatus = 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED'
export type ConsentType =
  | 'TERMS'
  | 'PRIVACY'
  | 'CROSS_BORDER_PROCESSING'
  | 'SHARE_PROFILE_PSEUDONYMIZED'
  | 'SHARE_FULL_CV'

export interface Me {
  id: Id
  email: string
  role: Role
  status: string
  locale?: string | null
  engineerProfileStatus?: ProfileStatus | null
  companyStatus?: string | null
  consents?: Partial<Record<ConsentType, boolean>>
}

export interface ConsentRecord {
  type: ConsentType
  version?: string | null
  granted: boolean
  scopeRef?: string | number | null
  createdAt?: string | null
}

export interface Category {
  code: string
  name: { en: string; es: string; zh: string }
  allowed: boolean
}

export type LegalDocName = 'terms' | 'privacy' | 'client-declarations' | 'engineer-declaration' | 'cross-border'

export interface LegalDoc {
  doc: string
  version: string
  locale: string
  title: string
  body: string
}

export interface PublicConfig {
  feePct: number
  paymentProvider: string
  locales: string[]
}

export type LanguageLevel = 'NONE' | 'BASIC' | 'GOOD' | 'FLUENT'

export interface EngineerProfileInput {
  headline: string
  bio: string
  skills: string[]
  yearsExperience: number | null
  region: string
  englishLevel: LanguageLevel
  spanishLevel: LanguageLevel
  hourlyRateUsd: number | null
  availabilityHoursWeek: number | null
  cvText: string
  education: string
  portfolioLinks: string[]
  declarationNoRestrictions: boolean
}

export interface EngineerProfile extends Partial<EngineerProfileInput> {
  id?: Id
  alias?: string | null
  status?: ProfileStatus | null
  reviewNote?: string | null
  declarationAt?: string | null
  ratingAvg?: number | null
  ratingCount?: number | null
}

export interface ProjectMilestone {
  id: Id
  seq: number
  title: string
  deliverable: string
  dueDate: string
  talentAmountUsd: number | string
}

export interface Project {
  id: Id
  title: string
  description: string
  categoryCode: string
  skills: string[]
  clientCountry: string
  talentBudgetUsd: number | string
  startDate: string
  endDate: string
  milestones: ProjectMilestone[]
  applied: boolean
}

export type ApplicationStatus = 'SUBMITTED' | 'SHORTLISTED' | 'SELECTED' | 'HIRED' | 'DECLINED' | 'WITHDRAWN'

export interface ApplicationPrice {
  milestoneId: Id
  talentAmountUsd: number | string
}

export interface Application {
  id: Id
  projectId: Id
  projectTitle?: string
  status: ApplicationStatus
  coverNote?: string
  talentTotalUsd?: number | string
  prices?: ApplicationPrice[]
  createdAt?: string
}

export type ContractStatus = 'PENDING_SIGNATURES' | 'ACTIVE' | 'COMPLETED' | 'TERMINATED'
export type MilestoneStatus =
  | 'PENDING_FUNDING'
  | 'FUNDED'
  | 'SUBMITTED'
  | 'REVISION_REQUESTED'
  | 'ACCEPTED'
  | 'DISPUTED'
  | 'REFUNDED'

export interface ContractDocument {
  id: Id
  kind: 'CLIENT_SERVICES' | 'ENGINEER_DELIVERABLES' | string
  templateVersion?: string | null
  locale?: string | null
  body: string
  sha256?: string | null
  acceptedByUserId?: Id | null
  acceptedAt?: string | null
}

export interface ContractMilestone {
  id: Id
  seq: number
  title: string
  deliverable: string
  dueDate: string
  talentAmountUsd: number | string
  status: MilestoneStatus
  submittedNote?: string | null
  submittedAt?: string | null
  acceptedAt?: string | null
  disputeReason?: string | null
  revisionReason?: string | null
}

export interface ContractSummary {
  id: Id
  projectId?: Id
  projectTitle?: string
  status: ContractStatus
  talentTotalUsd?: number | string
  activatedAt?: string | null
  completedAt?: string | null
  createdAt?: string | null
  milestones?: ContractMilestone[]
}

export interface ContractDetail extends ContractSummary {
  documents: ContractDocument[]
  milestones: ContractMilestone[]
}

export interface Message {
  id: Id
  senderUserId: Id
  body: string
  flagged?: boolean
  createdAt?: string
}

export interface Attachment {
  id: Id
  filename: string
  sizeBytes: number
}

export type SettlementStatus = 'NONE' | 'PENDING' | 'PAID'

export interface Earning {
  contractMilestoneId: Id
  projectTitle: string
  talentAmountUsd: number | string
  milestoneStatus: MilestoneStatus
  settlementStatus: SettlementStatus
  paidAt?: string | null
}
