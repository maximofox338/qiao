// Types mirror docs/SPEC.md §5 and §7 (camelCase JSON field names).

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

export type UserRole = 'CLIENT' | 'ENGINEER'
export type AdminRole = 'SUPERADMIN' | 'OPS' | 'FINANCE' | 'COMPLIANCE'

export type CompanyStatus = 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'BLOCKED'
export type SanctionsStatus = 'PENDING' | 'CLEAR' | 'HIT'
export type EngineerProfileStatus = 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED'
export type ProjectStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'CHANGES_REQUESTED'
  | 'REJECTED'
  | 'OPEN'
  | 'IN_CONTRACT'
  | 'COMPLETED'
  | 'CANCELLED'
export type ApplicationStatus = 'SUBMITTED' | 'SHORTLISTED' | 'SELECTED' | 'HIRED' | 'DECLINED' | 'WITHDRAWN'
export type ContractStatus = 'PENDING_SIGNATURES' | 'ACTIVE' | 'COMPLETED' | 'TERMINATED'
export type MilestoneStatus =
  | 'PENDING_FUNDING'
  | 'FUNDED'
  | 'SUBMITTED'
  | 'REVISION_REQUESTED'
  | 'ACCEPTED'
  | 'DISPUTED'
  | 'REFUNDED'
export type PaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED'
export type LanguageLevel = 'NONE' | 'BASIC' | 'GOOD' | 'FLUENT'
export type TransferLevel = 'OK' | 'WARN' | 'CRITICAL' | 'LIMIT'
export type LegalDoc = 'terms' | 'privacy' | 'client-declarations' | 'engineer-declaration' | 'cross-border'

export interface Category {
  code: string
  name: { en: string; es: string; zh: string }
  allowed: boolean
}

export interface PublicConfig {
  feePct: number
  paymentProvider: string
  locales: string[]
}

export interface LegalText {
  doc: string
  version: string
  locale: string
  title: string
  body: string
}

export interface Me {
  id: number
  email: string
  role: UserRole
  status: string
  locale: string
  engineerProfileStatus?: EngineerProfileStatus | null
  companyStatus?: CompanyStatus | null
  consents: Record<string, boolean>
}

export interface VerifyResponse {
  token: string
  user: Me
}

export interface Company {
  id?: number
  name: string
  country: string
  website: string
  size: string
  status: CompanyStatus
  sanctionsStatus?: SanctionsStatus
  sanctionsCheckedAt?: string | null
  declarationsVersion?: string | null
  declarationsHash?: string | null
  declarationsAt?: string | null
  reviewNote?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface ProjectDeclarations {
  noControlledTech: boolean
  noBulkUsSensitiveData: boolean
  noEmployment: boolean
  noProhibitedCategory: boolean
}

export interface ProjectMilestone {
  id?: number
  seq?: number
  title: string
  deliverable: string
  dueDate: string
  amountUsd: number | null
}

export interface ProjectInput {
  title: string
  description: string
  categoryCode: string
  skills: string[]
  budgetUsd: number | null
  startDate: string
  endDate: string
  milestones: ProjectMilestone[]
  declarations: ProjectDeclarations
  submit?: boolean
}

/** Keyword hits stored by the backend. The exact shape is not fixed in the spec; the UI accepts several. */
export type ProjectFlags =
  | string[]
  | Array<{ keyword?: string; term?: string; kind?: string; type?: string; field?: string; [k: string]: unknown }>
  | Record<string, unknown>
  | null

export interface Project {
  id: number
  companyId?: number
  title: string
  description: string
  categoryCode: string
  skills: string[]
  budgetUsd: number
  startDate: string
  endDate: string
  status: ProjectStatus
  flags?: ProjectFlags
  declarations?: ProjectDeclarations
  reviewNote?: string | null
  reviewedAt?: string | null
  milestones: ProjectMilestone[]
  createdAt?: string
  updatedAt?: string
  // Admin views may include company info
  companyName?: string
  companyCountry?: string
  applicationsCount?: number
}

export interface PseudonymizedEngineer {
  alias: string
  headline: string
  skills: string[]
  yearsExperience: number
  region: string
  englishLevel?: LanguageLevel
  spanishLevel?: LanguageLevel
  languages?: string[]
  hourlyRateUsd: number
  availabilityHoursWeek: number
  ratingAvg?: number | null
  ratingCount?: number | null
}

export interface ApplicationPrice {
  milestoneId: number
  talentAmountUsd?: number
  clientAmountUsd?: number
}

export interface Application {
  id: number
  status: ApplicationStatus
  coverNote: string
  talentTotalUsd?: number
  clientTotalUsd: number
  prices: ApplicationPrice[]
  engineer: PseudonymizedEngineer
  cvAvailable: boolean
  createdAt?: string
}

export interface EngineerCv {
  cvText: string
  education: string
  portfolioLinks: string[]
}

export interface ContractDocument {
  id: number
  kind: 'CLIENT_SERVICES' | 'ENGINEER_DELIVERABLES'
  templateVersion: string
  locale: string
  body: string
  sha256: string
  acceptedByUserId?: number | null
  acceptedAt?: string | null
}

export interface ContractMilestone {
  id: number
  contractId?: number
  seq: number
  title: string
  deliverable: string
  dueDate: string
  talentAmountUsd?: number
  clientAmountUsd: number
  status: MilestoneStatus
  submittedNote?: string | null
  submittedAt?: string | null
  acceptedAt?: string | null
  disputeReason?: string | null
  attachments?: Attachment[]
}

export interface Attachment {
  id: number
  filename: string
  sizeBytes: number
  contentType?: string
  milestoneId?: number | null
  uploaderUserId?: number
  createdAt?: string
}

export interface Contract {
  id: number
  projectId: number
  projectTitle?: string
  applicationId?: number
  companyId?: number
  engineerAlias?: string
  status: ContractStatus
  feePct?: number
  talentTotalUsd?: number
  clientTotalUsd: number
  activatedAt?: string | null
  completedAt?: string | null
  documents?: ContractDocument[]
  milestones?: ContractMilestone[]
  attachments?: Attachment[]
  createdAt?: string
}

export interface Message {
  id: number
  contractId?: number
  senderUserId: number
  senderRole?: UserRole
  senderAlias?: string
  body: string
  flagged?: boolean
  createdAt: string
}

export interface CheckoutResponse {
  paymentId: number
  url: string
}

export interface Payment {
  id: number
  status: PaymentStatus
  amountUsd: number
  contractId?: number
  contractMilestoneId?: number
}

/* ----------------------------- Admin ----------------------------- */

export interface AdminInfo {
  id: number
  email: string
  roles: AdminRole[]
}

export interface AdminLoginResponse {
  token: string
  admin: AdminInfo
}

export interface AdminDashboard {
  companies: { byStatus: Record<string, number> }
  engineers: { byStatus: Record<string, number> }
  projects: { byStatus: Record<string, number> }
  contracts: { byStatus: Record<string, number> }
  gmvUsd: number
  feesUsd: number
  transfers: { year: number; uniqueEngineers: number; level: TransferLevel }
}

export interface AdminCompany extends Company {
  id: number
  members?: Array<{ userId: number; email?: string }>
  memberEmails?: string[]
}

export interface AdminEngineer {
  id: number
  userId?: number
  email?: string
  alias: string
  headline: string
  bio?: string
  skills: string[]
  yearsExperience: number
  region: string
  englishLevel?: LanguageLevel
  spanishLevel?: LanguageLevel
  hourlyRateUsd: number
  availabilityHoursWeek: number
  cvText?: string
  education?: string
  portfolioLinks?: string[]
  declarationNoRestrictions?: boolean
  declarationAt?: string | null
  status: EngineerProfileStatus
  reviewNote?: string | null
  partnerRef?: string | null
  ratingAvg?: number | null
  ratingCount?: number | null
  createdAt?: string
  updatedAt?: string
}

export interface Dispute {
  id: number
  milestoneId?: number
  contractId: number
  projectTitle?: string
  seq?: number
  title: string
  clientAmountUsd?: number
  talentAmountUsd?: number
  disputeReason: string
  status?: MilestoneStatus
  updatedAt?: string
}

export interface SettlementBatch {
  id: number
  period: string
  status: 'DRAFT' | 'EXPORTED' | 'PAID'
  createdByAdminId?: number
  exportedAt?: string | null
  createdAt?: string
  lineCount?: number
  totalUsd?: number
  lines?: SettlementLine[]
}

export interface SettlementLine {
  id?: number
  lineId?: number
  contractMilestoneId: number
  engineerUserId?: number
  partnerRef: string
  alias: string
  engineerEmail?: string
  projectTitle?: string
  talentAmountUsd: number
  status: 'PENDING' | 'PAID'
  partnerPaymentRef?: string | null
  iitWithheldCny?: number | null
  netPaidCny?: number | null
  paidAt?: string | null
}

export interface TransferSummary {
  year: number
  uniqueEngineers: { PSEUDONYMIZED_PROFILE: number; FULL_CV: number; ANY: number }
  thresholds: { warn: number; critical: number; limit: number }
  level: TransferLevel
}

export interface AuditEntry {
  id: number
  actorType: 'USER' | 'ADMIN' | 'PARTNER' | 'SYSTEM'
  actorId?: number | null
  action: string
  entityType?: string | null
  entityId?: string | number | null
  detail?: unknown
  ip?: string | null
  createdAt: string
}

export interface AdminCategory {
  code: string
  nameEn?: string
  nameEs?: string
  nameZh?: string
  name?: { en: string; es: string; zh: string }
  allowed: boolean
  sort?: number
}

export interface OutboxMail {
  id: number
  toEmail: string
  subject: string
  body: string
  status: 'LOGGED' | 'SENT' | 'FAILED'
  createdAt: string
}

export interface AdminAccount {
  id: number
  email: string
  roles: AdminRole[] | string
  status?: string
  totpEnabled?: boolean
  createdAt?: string
}
