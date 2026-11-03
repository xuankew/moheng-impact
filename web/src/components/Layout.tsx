import { NavLink, Outlet } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Activity, FolderGit2, History, LayoutDashboard, Settings, ShieldCheck } from 'lucide-react'
import { api } from '../lib/api'
import { cn } from '../lib/cn'

const nav = [
  { to: '/', label: '概览', icon: LayoutDashboard, end: true },
  { to: '/projects', label: '项目', icon: FolderGit2 },
  { to: '/history', label: '历史', icon: History },
  { to: '/settings', label: '设置', icon: Settings },
]

function Brand() {
  return (
    <div className="flex items-center gap-2.5">
      <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-indigo-600 text-white shadow-sm">
        <Activity className="h-4 w-4" />
      </div>
      <div className="leading-tight">
        <div className="text-sm font-semibold tracking-tight text-slate-900">墨衡 · 见微</div>
        <div className="text-[10px] tracking-wide text-slate-400">CHANGE IMPACT</div>
      </div>
    </div>
  )
}

export function Layout() {
  const health = useQuery({ queryKey: ['health'], queryFn: api.health, refetchInterval: 30_000 })

  return (
    <div className="flex h-full">
      <aside className="hidden w-60 shrink-0 flex-col border-r border-slate-200 bg-white px-4 py-5 lg:flex">
        <Brand />
        <nav className="mt-8 space-y-1">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition',
                  isActive
                    ? 'bg-indigo-50 text-indigo-700'
                    : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900',
                )
              }
            >
              <item.icon className="h-4 w-4" />
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="mt-auto rounded-lg border border-slate-100 bg-slate-50/60 px-3 py-3">
          <div className="flex items-center gap-1.5 text-[11px] font-medium text-slate-600">
            <ShieldCheck className={cn('h-3.5 w-3.5', health.data?.database ? 'text-emerald-500' : 'text-slate-400')} />
            {health.data ? (health.data.status === 'ok' ? '本地服务正常' : '服务降级') : '连接中…'}
          </div>
          <div className="mt-1.5 truncate font-mono text-[10px] text-slate-400" title={health.data?.workspaceRoot}>
            {health.data?.workspaceRoot ?? '—'}
          </div>
          <div className="mt-1 text-[10px] text-slate-400">
            Worker {health.data?.worker ? '可用' : '不可用'} · v{health.data?.version ?? '—'}
          </div>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-4 py-3 lg:hidden">
          <Brand />
          <nav className="flex items-center gap-1 overflow-x-auto">
            {nav.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  cn(
                    'rounded-md px-2.5 py-1.5 text-xs font-medium',
                    isActive ? 'bg-indigo-50 text-indigo-700' : 'text-slate-600',
                  )
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </header>
        <main className="min-h-0 flex-1 overflow-y-auto">
          <div className="mx-auto w-full max-w-[1440px] px-4 py-6 sm:px-6 lg:px-8">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}
