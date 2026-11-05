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

export type TeamRole = 'LEAD' | 'MEMBER'
export type TeamMemberStatus = 'INVITED' | 'ACTIVE' | 'DECLINED' | 'LEFT' | 'REMOVED'

/** Teammates are shown by alias, headline and skills only (spec §12). */
export interface TeamMember {
  id: Id
  alias: string
  headline?: string | null
  skills?: string[]
  role: TeamRole
  status: TeamMemberStatus
}

export interface Team {
  id: Id
  name: string
  description?: string | null
  status: 'ACTIVE' | 'DISSOLVED'
  leadAlias?: string | null
  myRole?: TeamRole | null
  myStatus?: TeamMemberStatus | null
  members: TeamMember[]
  activeMembers: number
  canApply: boolean
  createdAt?: string
}

export interface TeamInvitation {
  teamId: Id
  teamName: string
  description?: string | null
  leadAlias?: string | null
  memberAliases: string[]
  invitedAt?: string
}

export interface TeamShare {
  alias: string
  role?: TeamRole
  sharePct: number | string
}

export interface ApplicationTeam {
  id: Id
  name?: string | null
  myRole?: TeamRole | null
  mySharePct?: number | string | null
  members: TeamShare[]
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
  /** Set for team applications. */
  team?: ApplicationTeam | null
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
  /** The viewer's own part of the milestone (equals talentAmountUsd for individual contracts). */
  myAmountUsd?: number | string | null
}

export interface ContractTeamMember {
  alias: string
  role: TeamRole
  sharePct: number | string
  accepted: boolean
}

export interface ContractTeam {
  id: Id
  name?: string | null
  myRole?: TeamRole | null
  mySharePct?: number | string | null
  myTalentTotalUsd?: number | string | null
  members: ContractTeamMember[]
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
  /** Set for team contracts. */
  team?: ContractTeam | null
}

export interface ReviewSummary {
  stars: number
  comment?: string | null
  createdAt?: string | null
}

export interface ContractDetail extends ContractSummary {
  /** The engineer side's review of the client (written by the lead in a team), once COMPLETED. */
  myReview?: ReviewSummary | null
  /** True for the engineer (or the team lead) while the review is missing on a COMPLETED contract. */
  canReview?: boolean
  /** Each party only receives its own document; the other party's acceptance comes as a timestamp. */
  clientAcceptedAt?: string | null
  engineerAcceptedAt?: string | null
  documents: ContractDocument[]
  milestones: ContractMilestone[]
}

export interface Message {
  id: Id
  senderUserId: Id
  senderSide?: 'CLIENT' | 'ENGINEER'
  /** Engineer messages carry the sender's alias; client messages carry none for engineers. */
  senderAlias?: string | null
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
