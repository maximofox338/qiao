import type { ProjectFlags } from '@/api/types'

/** Keyword lists from docs/SPEC.md §1. Used only for client-side hints; the backend is authoritative. */
export const EMPLOYMENT_KEYWORDS = [
  'full-time', 'full time', 'fulltime', 'permanent', 'salary', 'monthly salary', '9-to-5', 'office hours',
  '8 hours a day', '40 hours a week', '全职', '月薪', '工资', '坐班', '朝九晚五', '雇佣', 'tiempo completo',
  'sueldo', 'jornada laboral', 'horario de oficina',
]

export const SENSITIVE_KEYWORDS = [
  'crypto', 'blockchain', 'token sale', 'semiconductor', 'chip design', 'lithography', 'drone', 'uav', 'lidar',
  'military', 'defense', 'defence', 'government data', 'state-owned', 'survey', 'mapping', 'geodata', 'gis',
  'rare earth', 'lithium', 'battery cathode', 'genome', 'biometric', 'research assistant', 'expert network',
  'data collection', '军', '国防', '无人机', '测绘', '地理信息', '芯片', '半导体', '稀土', '加密货币', '区块链', '调研', '数据采集',
]

function hasCjk(s: string) {
  return /[㐀-鿿]/.test(s)
}

function matches(text: string, keyword: string): boolean {
  const lower = text.toLowerCase()
  const k = keyword.toLowerCase()
  if (hasCjk(k)) return lower.includes(k)
  const escaped = k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  return new RegExp(`(^|[^a-z0-9])${escaped}([^a-z0-9]|$)`, 'i').test(lower)
}

export function detectKeywords(text: string): { employment: string[]; sensitive: string[] } {
  return {
    employment: EMPLOYMENT_KEYWORDS.filter((k) => matches(text, k)),
    sensitive: SENSITIVE_KEYWORDS.filter((k) => matches(text, k)),
  }
}

/** Flattens the backend `flags` JSON (shape not fixed by the spec) into display keywords. */
export function flagKeywords(flags: ProjectFlags | undefined): string[] {
  if (!flags) return []
  const out = new Set<string>()
  const visit = (v: unknown) => {
    if (v === null || v === undefined) return
    if (typeof v === 'string') out.add(v)
    else if (Array.isArray(v)) v.forEach(visit)
    else if (typeof v === 'object') {
      const o = v as Record<string, unknown>
      const k = o.keyword ?? o.term ?? o.word ?? o.match
      if (typeof k === 'string') out.add(k)
      else Object.values(o).forEach(visit)
    }
  }
  visit(flags)
  return [...out]
}

export function flagCount(flags: ProjectFlags | undefined): number {
  return flagKeywords(flags).length
}
