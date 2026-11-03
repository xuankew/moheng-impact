import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { History, Search, ShieldAlert, Trash2 } from 'lucide-react'
import { PageHeader } from '../components/PageHeader'
import {
  Button,
  Card,
  EmptyState,
  InlineError,
  RiskBadge,
  Spinner,
  Stat,
  StatusBadge,
} from '../components/ui'
import { api } from '../lib/api'
import { cn } from '../lib/cn'
import { fmtDate, fmtTime } from '../lib/format'
import type { RunStatus } from '../lib/types'

const statusOptions: { value: 'ALL' | RunStatus; label: string }[] = [
  { value: 'ALL', label: '全部状态' },
  { value: 'QUEUED', label: '排队中' },
  { value: 'RUNNING', label: '分析中' },
  { value: 'SUCCEEDED', label: '已完成' },
  { value: 'FAILED', label: '失败' },
  { value: 'CANCELLED', label: '已取消' },
]

function duration(startedAt: string, finishedAt: string | null): string {
  if (!finishedAt) return '—'
  const start = new Date(startedAt).getTime()
  const end = new Date(finishedAt).getTime()
  if (Number.isNaN(start) || Number.isNaN(end) || end < start) return '—'
  const seconds = Math.round((end - start) / 1000)
  if (seconds < 60) return `${seconds}s`
  return `${Math.floor(seconds / 60)}m ${seconds % 60}s`
}

export function HistoryPage() {
  const navigate = useNavigate()
  const client = useQueryClient()
  const [projectId, setProjectId] = useState('')
  const [status, setStatus] = useState<'ALL' | RunStatus>('ALL')
  const [query, setQuery] = useState('')

  const projects = useQuery({ queryKey: ['projects'], queryFn: api.projects })
  const analyses = useQuery({
    queryKey: ['analyses', 'history', projectId],
    queryFn: () => api.analyses(projectId || undefined, 200),
    refetchInterval: (value) => {
      const rows = value.state.data ?? []
      return rows.some((run) => run.status === 'RUNNING' || run.status === 'QUEUED') ? 2_000 : false
    },
  })

  const remove = useMutation({
    mutationFn: (id: string) => api.deleteAnalysis(id),
    onSuccess: () => client.invalidateQueries({ queryKey: ['analyses'] }),
  })

  const projectNames = useMemo(
    () => new Map((projects.data ?? []).map((project) => [project.id, project.name])),
    [projects.data],
  )

  const rows = useMemo(() => {
    const needle = query.trim().toLowerCase()
    return (analyses.data ?? []).filter((run) => {
      if (status !== 'ALL' && run.status !== status) return false
      if (!needle) return true
      return `${run.base} ${run.head} ${projectNames.get(run.projectId) ?? ''}`.toLowerCase().includes(needle)
    })
  }, [analyses.data, status, query, projectNames])

  const all = analyses.data ?? []
  const failed = all.filter((run) => run.status === 'FAILED').length
  const safeMode = all.filter((run) => run.summary?.safeMode).length

  return (
    <div>
      <PageHeader
        title="历史"
        description="所有分析记录都保存在本地 SQLite。进行中的任务会自动刷新状态。"
        actions={
          <Link to="/projects">
            <Button variant="primary">新建分析</Button>
          </Link>
        }
      />

      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <Stat label="全部记录" value={all.length} hint="当前筛选项目" />
        <Stat label="已完成" value={all.filter((run) => run.status === 'SUCCEEDED').length} />
        <Stat label="失败" value={failed} tone={failed > 0 ? 'danger' : undefined} />
        <Stat label="Safe Mode" value={safeMode} tone={safeMode > 0 ? 'warning' : undefined} />
      </div>

      <Card className="mt-4">
        <div className="flex flex-wrap items-center gap-2 border-b border-slate-100 px-4 py-3">
          <select
            value={projectId}
            onChange={(event) => setProjectId(event.target.value)}
            className="h-8 rounded-lg border border-slate-200 bg-white px-2 text-xs text-slate-700 outline-none focus:border-indigo-400"
          >
            <option value="">全部项目</option>
            {(projects.data ?? []).map((project) => (
              <option key={project.id} value={project.id}>
                {project.name}
              </option>
            ))}
          </select>
          <select
            value={status}
            onChange={(event) => setStatus(event.target.value as 'ALL' | RunStatus)}
            className="h-8 rounded-lg border border-slate-200 bg-white px-2 text-xs text-slate-700 outline-none focus:border-indigo-400"
          >
            {statusOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <div className="relative ml-auto">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-400" />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="搜索 base / head / 项目"
              className="h-8 w-56 rounded-lg border border-slate-200 pl-8 pr-3 text-xs outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
            />
          </div>
        </div>

        {analyses.error ? (
          <div className="p-4">
            <InlineError error={analyses.error} />
          </div>
        ) : null}
        {analyses.isLoading ? (
          <div className="flex items-center gap-2 px-5 py-10 text-sm text-slate-500">
            <Spinner /> 加载历史…
          </div>
        ) : null}

        {!analyses.isLoading && rows.length === 0 ? (
          <EmptyState
            icon={<History className="h-6 w-6" />}
            title={all.length === 0 ? '还没有分析记录' : '没有匹配记录'}
            description={
              all.length === 0
                ? '选择一个项目并运行一次分析，结果会保存在本地。'
                : '调整项目、状态或搜索条件后重试。'
            }
            action={
              all.length === 0 ? (
                <Link to="/projects">
                  <Button variant="primary">去选择项目</Button>
                </Link>
              ) : undefined
            }
          />
        ) : null}

        {rows.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50/80 text-[11px] text-slate-500">
                <tr>
                  <th className="px-5 py-2.5 font-medium">项目 / 范围</th>
                  <th className="px-3 py-2.5 font-medium">状态</th>
                  <th className="px-3 py-2.5 font-medium">风险</th>
                  <th className="px-3 py-2.5 font-medium">受影响 API</th>
                  <th className="px-3 py-2.5 font-medium">耗时</th>
                  <th className="px-3 py-2.5 font-medium">开始时间</th>
                  <th className="px-5 py-2.5 text-right font-medium">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {rows.map((run) => (
                  <tr
                    key={run.id}
                    className="cursor-pointer transition hover:bg-slate-50/60"
                    onClick={() => navigate(`/analyses/${run.id}`)}
                  >
                    <td className="px-5 py-3">
                      <div className="flex items-center gap-1.5 font-medium text-slate-800">
                        {projectNames.get(run.projectId) ?? run.projectId.slice(0, 8)}
                        {run.summary?.safeMode ? (
                          <span className="inline-flex items-center gap-0.5 rounded bg-rose-50 px-1 py-0.5 text-[9px] font-medium text-rose-600">
                            <ShieldAlert className="h-2.5 w-2.5" />
                            Safe
                          </span>
                        ) : null}
                      </div>
                      <div className="mt-0.5 font-mono text-[10px] text-slate-400">
                        {run.base} → {run.head}
                      </div>
                    </td>
                    <td className="px-3 py-3">
                      <StatusBadge status={run.status} />
                      {run.status === 'RUNNING' ? (
                        <div className="mt-1 text-[10px] text-slate-400">
                          {run.phase ?? ''} {run.progress}%
                        </div>
                      ) : null}
                    </td>
                    <td className="px-3 py-3">
                      {run.riskLevel ? (
                        <RiskBadge level={run.riskLevel} score={run.riskScore} />
                      ) : (
                        <span className="text-slate-400">—</span>
                      )}
                    </td>
                    <td className="px-3 py-3 font-mono text-slate-600">{run.summary?.impactedApis ?? '—'}</td>
                    <td className="px-3 py-3 font-mono text-[11px] text-slate-500">
                      {duration(run.startedAt, run.finishedAt)}
                    </td>
                    <td className="px-3 py-3">
                      <div className="text-slate-600">{fmtTime(run.startedAt)}</div>
                      <div className="mt-0.5 text-[10px] text-slate-400">{fmtDate(run.startedAt)}</div>
                    </td>
                    <td className="px-5 py-3 text-right">
                      <span
                        className={cn(
                          'inline-flex items-center gap-1',
                          run.status === 'RUNNING' || run.status === 'QUEUED' ? 'opacity-40' : '',
                        )}
                      >
                        <Button
                          variant="ghost"
                          size="sm"
                          aria-label="删除"
                          disabled={run.status === 'RUNNING' || run.status === 'QUEUED'}
                          onClick={(event) => {
                            event.stopPropagation()
                            if (window.confirm('确认删除这条分析记录？')) remove.mutate(run.id)
                          }}
                        >
                          <Trash2 className="h-3.5 w-3.5 text-rose-500" />
                        </Button>
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </Card>

      {remove.error ? (
        <div className="mt-4">
          <InlineError error={remove.error} />
        </div>
      ) : null}
    </div>
  )
}
