// Mainland China province-level divisions. `value` is what the API stores in engineer_profile.region
// (English name, readable by the client portal in en/es without a lookup table; see docs/DECISIONS.md).
export interface Province {
  value: string
  zh: string
}

export const PROVINCES: Province[] = [
  { value: 'Beijing', zh: '北京市' },
  { value: 'Tianjin', zh: '天津市' },
  { value: 'Hebei', zh: '河北省' },
  { value: 'Shanxi', zh: '山西省' },
  { value: 'Inner Mongolia', zh: '内蒙古自治区' },
  { value: 'Liaoning', zh: '辽宁省' },
  { value: 'Jilin', zh: '吉林省' },
  { value: 'Heilongjiang', zh: '黑龙江省' },
  { value: 'Shanghai', zh: '上海市' },
  { value: 'Jiangsu', zh: '江苏省' },
  { value: 'Zhejiang', zh: '浙江省' },
  { value: 'Anhui', zh: '安徽省' },
  { value: 'Fujian', zh: '福建省' },
  { value: 'Jiangxi', zh: '江西省' },
  { value: 'Shandong', zh: '山东省' },
  { value: 'Henan', zh: '河南省' },
  { value: 'Hubei', zh: '湖北省' },
  { value: 'Hunan', zh: '湖南省' },
  { value: 'Guangdong', zh: '广东省' },
  { value: 'Guangxi', zh: '广西壮族自治区' },
  { value: 'Hainan', zh: '海南省' },
  { value: 'Chongqing', zh: '重庆市' },
  { value: 'Sichuan', zh: '四川省' },
  { value: 'Guizhou', zh: '贵州省' },
  { value: 'Yunnan', zh: '云南省' },
  { value: 'Tibet', zh: '西藏自治区' },
  { value: 'Shaanxi', zh: '陕西省' },
  { value: 'Gansu', zh: '甘肃省' },
  { value: 'Qinghai', zh: '青海省' },
  { value: 'Ningxia', zh: '宁夏回族自治区' },
  { value: 'Xinjiang', zh: '新疆维吾尔自治区' },
]

export function provinceLabel(value: string | null | undefined, locale: string): string {
  if (!value) return ''
  const p = PROVINCES.find((x) => x.value === value || x.zh === value)
  if (!p) return value
  return locale === 'en' ? p.value : p.zh
}
