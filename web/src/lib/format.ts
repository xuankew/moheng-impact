export function fmtTime(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

export function fmtDate(value: string | null | undefined): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

export function shortPath(path: string, max = 64): string {
  if (!path || path.length <= max) return path
  const parts = path.split('/')
  const file = parts.pop() ?? ''
  return `…/${file}`
}

export function shortOwner(owner: string): string {
  const parts = owner.split('.')
  return parts.length <= 2 ? owner : parts.slice(-2).join('.')
}

export function symbolLabel(key: { kind: string; owner: string; name: string; descriptor: string }): string {
  if (key.kind === 'CLASS') return key.owner
  return `${shortOwner(key.owner)}#${key.name}`
}

export function verbTone(verb: string): string {
  switch (verb.toUpperCase()) {
    case 'GET':
      return 'bg-sky-50 text-sky-700 ring-sky-200'
    case 'POST':
      return 'bg-emerald-50 text-emerald-700 ring-emerald-200'
    case 'PUT':
    case 'PATCH':
      return 'bg-amber-50 text-amber-700 ring-amber-200'
    case 'DELETE':
      return 'bg-rose-50 text-rose-700 ring-rose-200'
    default:
      return 'bg-slate-100 text-slate-600 ring-slate-200'
  }
}

export function pct(value: number): string {
  return `${Math.round(value * 100)}%`
}
