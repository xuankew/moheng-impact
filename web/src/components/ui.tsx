import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from 'react'
import { AlertOctagon, AlertTriangle, CheckCircle2, Info, Loader2, ShieldAlert, X } from 'lucide-react'
import { cn } from '../lib/cn'
import type { RiskLevel, RunStatus } from '../lib/types'

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return (
    <div className={cn('rounded-xl border border-slate-200/80 bg-white shadow-[0_1px_2px_rgba(15,23,42,0.04)]', className)}>
      {children}
    </div>
  )
}

export function CardHeader({
  title,
  description,
  action,
  className,
}: {
  title: ReactNode
  description?: ReactNode
  action?: ReactNode
  className?: string
}) {
  return (
    <div className={cn('flex items-start justify-between gap-4 border-b border-slate-100 px-5 py-4', className)}>
      <div className="min-w-0">
        <div className="text-sm font-semibold text-slate-900">{title}</div>
        {description ? <div className="mt-1 text-xs leading-5 text-slate-500">{description}</div> : null}
      </div>
      {action ? <div className="shrink-0">{action}</div> : null}
    </div>
  )
}

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger'

export function Button({
  variant = 'secondary',
  size = 'md',
  className,
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant
  size?: 'sm' | 'md'
}) {
  const variants: Record<ButtonVariant, string> = {
    primary:
      'bg-indigo-600 text-white shadow-sm hover:bg-indigo-700 focus-visible:outline-indigo-600 disabled:bg-indigo-300',
    secondary:
      'border border-slate-200 bg-white text-slate-700 shadow-sm hover:bg-slate-50 focus-visible:outline-slate-400 disabled:text-slate-400',
    ghost: 'text-slate-600 hover:bg-slate-100 hover:text-slate-900 focus-visible:outline-slate-400',
    danger:
      'border border-rose-200 bg-white text-rose-600 shadow-sm hover:bg-rose-50 focus-visible:outline-rose-500 disabled:text-rose-300',
  }
  const sizes = {
    sm: 'h-8 px-3 text-xs',
    md: 'h-9 px-4 text-sm',
  }
  return (
    <button
      className={cn(
        'inline-flex items-center justify-center gap-1.5 rounded-lg font-medium transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 disabled:cursor-not-allowed',
        variants[variant],
        sizes[size],
        className,
      )}
      {...props}
    >
      {children}
    </button>
  )
}

export function Input({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      className={cn(
        'h-9 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-900 shadow-sm outline-none transition placeholder:text-slate-400 focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100 disabled:bg-slate-50 disabled:text-slate-500',
        className,
      )}
      {...props}
    />
  )
}

export function Select({ className, children, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      className={cn(
        'h-9 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-900 shadow-sm outline-none transition focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100',
        className,
      )}
      {...props}
    >
      {children}
    </select>
  )
}

export function Field({
  label,
  hint,
  children,
  className,
}: {
  label: ReactNode
  hint?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <label className={cn('block', className)}>
      <div className="mb-1.5 text-xs font-medium text-slate-600">{label}</div>
      {children}
      {hint ? <div className="mt-1.5 text-[11px] leading-4 text-slate-400">{hint}</div> : null}
    </label>
  )
}

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn('h-4 w-4 animate-spin text-slate-400', className)} />
}

export function Progress({ value, tone = 'indigo' }: { value: number; tone?: 'indigo' | 'rose' }) {
  return (
    <div className="h-1.5 w-full overflow-hidden rounded-full bg-slate-100">
      <div
        className={cn('h-full rounded-full transition-all', tone === 'indigo' ? 'bg-indigo-500' : 'bg-rose-500')}
        style={{ width: `${Math.max(0, Math.min(100, value))}%` }}
      />
    </div>
  )
}

const riskStyles: Record<RiskLevel, { className: string; Icon: typeof ShieldAlert }> = {
  CRITICAL: { className: 'bg-rose-50 text-rose-700 ring-rose-200', Icon: AlertOctagon },
  HIGH: { className: 'bg-orange-50 text-orange-700 ring-orange-200', Icon: AlertTriangle },
  MEDIUM: { className: 'bg-amber-50 text-amber-700 ring-amber-200', Icon: Info },
  LOW: { className: 'bg-emerald-50 text-emerald-700 ring-emerald-200', Icon: CheckCircle2 },
}

export function RiskBadge({ level, score }: { level: RiskLevel; score?: number | null }) {
  const style = riskStyles[level] ?? riskStyles.LOW
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-[11px] font-semibold ring-1 ring-inset',
        style.className,
      )}
    >
      <style.Icon className="h-3.5 w-3.5" />
      {level}
      {typeof score === 'number' ? <span className="font-mono text-[10px] opacity-80">{score}</span> : null}
    </span>
  )
}

const statusStyles: Record<RunStatus, { label: string; className: string; dot: string }> = {
  QUEUED: { label: '排队中', className: 'bg-slate-100 text-slate-600 ring-slate-200', dot: 'bg-slate-400' },
  RUNNING: { label: '分析中', className: 'bg-indigo-50 text-indigo-700 ring-indigo-200', dot: 'bg-indigo-500' },
  SUCCEEDED: { label: '已完成', className: 'bg-emerald-50 text-emerald-700 ring-emerald-200', dot: 'bg-emerald-500' },
  FAILED: { label: '失败', className: 'bg-rose-50 text-rose-700 ring-rose-200', dot: 'bg-rose-500' },
  CANCELLED: { label: '已取消', className: 'bg-slate-100 text-slate-500 ring-slate-200', dot: 'bg-slate-400' },
}

export function StatusBadge({ status }: { status: RunStatus }) {
  const style = statusStyles[status] ?? statusStyles.QUEUED
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 whitespace-nowrap rounded-md px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset',
        style.className,
      )}
    >
      <span className={cn('h-1.5 w-1.5 rounded-full', style.dot)} />
      {style.label}
    </span>
  )
}

export function VerBadge({ verb }: { verb: string }) {
  return (
    <span className="inline-flex items-center rounded-md bg-slate-900 px-1.5 py-0.5 font-mono text-[10px] font-semibold tracking-wide text-white">
      {verb.toUpperCase()}
    </span>
  )
}

export function Drawer({
  open,
  title,
  description,
  onClose,
  children,
  width = 'max-w-xl',
}: {
  open: boolean
  title: ReactNode
  description?: ReactNode
  onClose: () => void
  children: ReactNode
  width?: string
}) {
  if (!open) return null
  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div className="absolute inset-0 bg-slate-900/20 backdrop-blur-[1px]" onClick={onClose} />
      <aside
        className={cn(
          'relative flex h-full w-full flex-col border-l border-slate-200 bg-white shadow-2xl',
          width,
        )}
      >
        <header className="flex items-start justify-between gap-4 border-b border-slate-100 px-5 py-4">
          <div className="min-w-0">
            <div className="truncate text-sm font-semibold text-slate-900">{title}</div>
            {description ? <div className="mt-1 text-xs text-slate-500">{description}</div> : null}
          </div>
          <button
            onClick={onClose}
            className="rounded-md p-1 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
            aria-label="关闭"
          >
            <X className="h-4 w-4" />
          </button>
        </header>
        <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">{children}</div>
      </aside>
    </div>
  )
}

export function EmptyState({
  icon,
  title,
  description,
  action,
}: {
  icon?: ReactNode
  title: ReactNode
  description?: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center justify-center px-6 py-16 text-center">
      {icon ? <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-slate-100 text-slate-400">{icon}</div> : null}
      <div className="text-sm font-semibold text-slate-800">{title}</div>
      {description ? <div className="mt-2 max-w-md text-xs leading-5 text-slate-500">{description}</div> : null}
      {action ? <div className="mt-5">{action}</div> : null}
    </div>
  )
}

export function Stat({
  label,
  value,
  hint,
  tone,
  className,
}: {
  label: ReactNode
  value: ReactNode
  hint?: ReactNode
  tone?: 'danger' | 'warning' | 'success'
  className?: string
}) {
  const tones = {
    danger: 'text-rose-600',
    warning: 'text-amber-600',
    success: 'text-emerald-600',
  }
  return (
    <div className={cn('rounded-xl border border-slate-200/80 bg-white px-4 py-3', className)}>
      <div className="text-[11px] font-medium tracking-wide text-slate-500">{label}</div>
      <div className={cn('mt-1.5 text-2xl font-semibold tracking-tight text-slate-900', tone && tones[tone])}>
        {value}
      </div>
      {hint ? <div className="mt-1 text-[11px] text-slate-400">{hint}</div> : null}
    </div>
  )
}

export function InlineError({ error }: { error: unknown }) {
  const message = error instanceof Error ? error.message : String(error)
  const code = (error as { code?: string })?.code
  return (
    <div className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-xs text-rose-700">
      <div className="font-semibold">{code ? `${code} · ` : ''}请求失败</div>
      <div className="mt-1 leading-5">{message}</div>
    </div>
  )
}
