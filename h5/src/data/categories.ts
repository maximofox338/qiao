// Seed categories from docs/SPEC.md §9 (used when /api/public/categories is unavailable and on the landing page).
export const PROHIBITED_CATEGORIES = [
  'crypto',
  'semiconductor',
  'drones',
  'geodata',
  'defense',
  'gov-data',
  'expert-network',
  'critical-minerals',
  'biometric-genomic',
] as const

export const ALLOWED_CATEGORIES = [
  'web-dev',
  'mobile-dev',
  'backend-api',
  'data-bi',
  'ml-eng',
  'qa',
  'devops',
  'ui-ux',
  'embedded-consumer',
  'cad-mech',
  'pcb-consumer',
  'tech-writing',
] as const
