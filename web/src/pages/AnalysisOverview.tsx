import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle,
  ArrowRight,
  Ban,
  Code2,
  FileDiff,
  GitCompareArrows,
  Network,
  ShieldAlert,
  Trash2,
  XCircle,
} from 'lucide-react'
import { PageHeader } from '../components/PageHeader'
import {
  Button,
  Card,
  CardHeader,
  InlineError,
  Progress,
  RiskBadge,
  Spinner,
  Stat,
  StatusBadge,
  VerBadge,
} from '../components/ui'
import { api } from '../lib/api'
import { cn } from '../lib/cn'
import { fmtDate, shortOwner, symbolLabel, pct } from '../lib/format'
import type { RiskLevel } from '../lib/types'

const levelBanner: Record<RiskLevel, string> = {
  CRITICAL: 'border-rose-200 bg-gradient-to-r from-rose-50 to-white',
  HIGH: 'border-orange-200 bg-gradient-to-r from-orange-50 to-white',
  MEDIUM: 'border-amber-200 bg-gradient-to-r from-amber-50 to-white',
  LOW: 'border-emerald-200 bg-gradient-to-r from-emerald-50 to-white',
}

export function AnalysisOverview() {
  const { analysisId = '' } = useParams()
  const navigate = useNavigate()
  const client = useQueryClient()
  const [query, setQuery] = useState('')
  const [level, setLevel] = useState<'ALL' | RiskLevel>('ALL')

  const status = useQuery({
    queryKey: ['analysis', analysisId, 'status'],
    queryFn: () => api.analysisStatus(analysisId),
    refetchInterval: (query) => {
      const value = query.state.data?.status
      return value === 'QUEUED' || value === 'RUNNING' ? 1_000 : false
    },
  })

  const succeeded = status.data?.status === 'SUCCEEDED'
  const result = useQuery({
    queryKey: ['analysis', analysisId, 'result'],
    queryFn: () => api.analysisResult(analysisId),
    enabled: succeeded,
  })
  const project = useQuery({
    queryKey: ['project', status.data?.projectId],
    queryFn: () => api.project(status.data!.projectId),
    enabled: Boolean(status.data?.projectId),
  })

  const cancel = useMutation({
    mutationFn: () => api.cancelAnalysis(analysisId),
    onSuccess: () => client.invalidateQueries({ queryKey: ['analysis', analysisId] }),
  })
  const remove = useMutation({
    mutationFn: () => api.deleteAnalysis(analysisId),
    onSuccess: () => navigate('/history'),
  })

  const impacts = useMemo(() => {
    const list = result.data?.apiImpacts ?? []
    return list.filter((impact) => {
      if (level !== 'ALL' && impact.level !== level) return false
      if (!query.trim()) return true
      const text = `${impact.api.verb} ${impact.api.path} ${impact.api.controller} ${impact.api.handlerName}`.toLowerCase()
      return text.includes(query.trim().toLowerCase())
    })
  }, [result.data, level, query])

  const riskReasonChips = useMemo(
    () =>
      result.data
        ? [...new Map(result.data.risk.reasons.map((reason) => [reason.message, reason])).values()]
        : [],
    [result.data],
  )

  if (status.isLoading) {
    return (
      <div className="flex items-center gap-2 py-16 text-sm text-slate-500">
        <Spinner /> 正在读取分析状态…
      </div>
    )
  }

  if (status.error) return <InlineError error={status.error} />
  const run = status.data

  return (
    <div>
      <PageHeader
        back={{ to: '/history', label: '返回历史' }}
        title={project.data ? project.data.name : '分析报告'}
        description={
          <span className="flex flex-wrap items-center gap-2">
            <StatusBadge status={run?.status ?? 'QUEUED'} />
            <span className="font-mono text-[11px] text-slate-500">
              {run?.base} → {run?.head}
            </span>
            <span className="text-slate-300">·</span>
            <span className="text-[11px] text-slate-400">{fmtDate(run?.startedAt)}</span>
          </span>
        }
        actions={
          <div className="flex items-center gap-2">
            {succeeded ? (
              <>
                <Link to={`/analyses/${analysisId}/changes`}>
                  <Button variant="secondary" size="sm">
                    <FileDiff className="h-3.5 w-3.5" />
                    变更
                  </Button>
                </Link>
                <Link to={`/analyses/${analysisId}/graph`}>
                  <Button variant="primary" size="sm">
                    <Network className="h-3.5 w-3.5" />
                    影响图
                  </Button>
                </Link>
              </>
            ) : null}
            {run?.status === 'RUNNING' || run?.status === 'QUEUED' ? (
              <Button variant="danger" size="sm" onClick={() => cancel.mutate()} disabled={cancel.isPending}>
                <Ban className="h-3.5 w-3.5" />
                取消
              </Button>
            ) : (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  if (window.confirm('确认删除这次分析记录？')) remove.mutate()
                }}
                disabled={remove.isPending}
              >
                <Trash2 className="h-3.5 w-3.5 text-rose-500" />
              </Button>
            )}
          </div>
        }
      />

      {run && (run.status === 'RUNNING' || run.status === 'QUEUED') ? (
        <Card className="border-indigo-200 bg-indigo-50/30">
          <div className="px-5 py-5">
            <div className="flex items-center justify-between gap-4">
              <div className="flex items-center gap-2 text-sm font-semibold text-indigo-900">
                <Spinner className="text-indigo-500" />
                {run.status === 'QUEUED' ? '排队中' : `分析中 · ${run.phase ?? ''}`}
              </div>
              <span className="font-mono text-xs text-indigo-600">{run.progress}%</span>
            </div>
            <div className="mt-3">
              <Progress value={run.progress} />
            </div>
            <div className="mt-2 text-[11px] text-indigo-700/60">
              首次分析需要解析全部 Java 源码，通常在数秒到数十秒内完成。
            </div>
          </div>
        </Card>
      ) : null}

      {run && (run.status === 'FAILED' || run.status === 'CANCELLED') ? (
        <Card className="border-rose-200">
          <div className="flex items-start gap-3 px-5 py-5">
            <XCircle className="mt-0.5 h-5 w-5 text-rose-500" />
            <div>
              <div className="text-sm font-semibold text-rose-800">
                {run.status === 'CANCELLED' ? '分析已取消' : '分析失败'}
              </div>
              <div className="mt-1 text-xs text-rose-700">
                {run.error?.code ? <span className="font-mono">[{run.error.code}] </span> : null}
                {run.error?.message ?? '未知错误'}
              </div>
              <div className="mt-3">
                <Link to={`/projects/${run.projectId}/analyses/new`}>
                  <Button variant="secondary" size="sm">
                    重新分析
                  </Button>
                </Link>
              </div>
            </div>
          </div>
        </Card>
      ) : null}

      {result.error ? <InlineError error={result.error} /> : null}
      {result.isLoading ? (
        <div className="flex items-center gap-2 py-10 text-sm text-slate-500">
          <Spinner /> 加载报告…
        </div>
      ) : null}

      {result.data ? (
        <>
          <Card className={cn('overflow-hidden border', levelBanner[result.data.risk.level])}>
            <div className="flex flex-wrap items-center justify-between gap-4 px-5 py-5">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <RiskBadge level={result.data.risk.level} score={result.data.risk.score} />
                  {result.data.risk.safeMode.enabled ? (
                    <span className="inline-flex items-center gap-1 rounded-md bg-rose-600 px-2 py-0.5 text-[11px] font-semibold text-white">
                      <ShieldAlert className="h-3.5 w-3.5" />
                      Safe Mode 已启用
                    </span>
                  ) : null}
                  <span className="text-xs text-slate-500">
                    {result.data.summary.impactedApis} 个 API 受影响
                  </span>
                </div>
                <div className="mt-2 text-sm font-semibold text-slate-900">
                  {result.data.risk.level === 'CRITICAL'
                    ? '建议暂缓发布并执行全量回归'
                    : result.data.risk.level === 'HIGH'
                      ? '建议扩大回归范围并重点验证受影响 API'
                      : result.data.risk.level === 'MEDIUM'
                        ? '建议覆盖受影响 API 与相关契约'
                        : '风险可控，按常规回归执行'}
                </div>
                {result.data.risk.reasons.length ? (
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {riskReasonChips.map((reason, index) => (
                      <span
                        key={`${reason.code}-${index}`}
                        className="rounded-md bg-white/70 px-2 py-0.5 text-[11px] text-slate-600 ring-1 ring-inset ring-white"
                      >
                        {reason.message}
                      </span>
                    ))}
                  </div>
                ) : null}
              </div>
              <Link to={`/analyses/${analysisId}/graph`}>
                <Button variant="secondary" size="sm">
                  查看影响路径 <ArrowRight className="h-3.5 w-3.5" />
                </Button>
              </Link>
            </div>
          </Card>

          <div className="mt-4 grid grid-cols-2 gap-3 md:grid-cols-5">
            <Stat label="变更文件" value={result.data.summary.changedFiles} hint={`Java ${result.data.summary.changedJavaFiles}`} />
            <Stat label="变更符号" value={result.data.summary.changedSymbols} />
            <Stat label="受影响方法" value={result.data.summary.impactedMethods} />
            <Stat label="受影响 API" value={result.data.summary.impactedApis} />
            <Stat
              label="风险分"
              value={result.data.risk.score}
              tone={result.data.risk.score >= 60 ? 'danger' : result.data.risk.score >= 40 ? 'warning' : undefined}
            />
          </div>

          <div className="mt-4 grid items-start gap-4 xl:grid-cols-3">
            <Card className="xl:col-span-2">
              <CardHeader
                title="受影响 API"
                description="按风险分排序；点击行查看证据路径与源码上下文。"
                action={
                  <div className="flex items-center gap-2">
                    <input
                      value={query}
                      onChange={(event) => setQuery(event.target.value)}
                      placeholder="搜索路径或 Controller"
                      className="h-8 w-44 rounded-lg border border-slate-200 px-2.5 text-xs outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
                    />
                    <select
                      value={level}
                      onChange={(event) => setLevel(event.target.value as 'ALL' | RiskLevel)}
                      className="h-8 rounded-lg border border-slate-200 bg-white px-2 text-xs text-slate-700 outline-none focus:border-indigo-400"
                    >
                      <option value="ALL">全部等级</option>
                      <option value="CRITICAL">CRITICAL</option>
                      <option value="HIGH">HIGH</option>
                      <option value="MEDIUM">MEDIUM</option>
                      <option value="LOW">LOW</option>
                    </select>
                  </div>
                }
              />
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-50/80 text-[11px] text-slate-500">
                    <tr>
                      <th className="px-5 py-2.5 font-medium">API</th>
                      <th className="px-3 py-2.5 font-medium">Controller</th>
                      <th className="px-3 py-2.5 font-medium">风险</th>
                      <th className="px-3 py-2.5 text-right font-medium">分数</th>
                      <th className="px-3 py-2.5 text-right font-medium">深度</th>
                      <th className="px-5 py-2.5 text-right font-medium">置信度</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {impacts.map((impact) => (
                      <tr
                        key={impact.api.id}
                        className="cursor-pointer transition hover:bg-slate-50/70"
                        onClick={() => navigate(`/analyses/${analysisId}/apis/${impact.api.id}`)}
                      >
                        <td className="px-5 py-3">
                          <div className="flex items-center gap-2">
                            <VerBadge verb={impact.api.verb} />
                            <span className="font-mono text-slate-800">{impact.api.path}</span>
                            {impact.api.inherited ? (
                              <span className="rounded bg-slate-100 px-1 py-0.5 text-[9px] text-slate-500">继承</span>
                            ) : null}
                          </div>
                        </td>
                        <td className="px-3 py-3">
                          <div className="text-slate-700">{shortOwner(impact.api.handlerOwner)}</div>
                          <div className="mt-0.5 font-mono text-[10px] text-slate-400">{impact.api.handlerName}</div>
                        </td>
                        <td className="px-3 py-3">
                          <RiskBadge level={impact.level} />
                        </td>
                        <td className="px-3 py-3 text-right font-mono font-semibold text-slate-700">{impact.score}</td>
                        <td className="px-3 py-3 text-right font-mono text-slate-500">{impact.depth}</td>
                        <td className="px-5 py-3 text-right font-mono text-slate-500">{pct(impact.confidence)}</td>
                      </tr>
                    ))}
                    {impacts.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="px-5 py-10 text-center text-slate-400">
                          没有匹配的受影响 API
                        </td>
                      </tr>
                    ) : null}
                  </tbody>
                </table>
              </div>
            </Card>

            <div className="space-y-4">
              {result.data.risk.safeMode.enabled ? (
                <Card className="border-rose-200">
                  <CardHeader
                    title={
                      <span className="flex items-center gap-1.5 text-rose-800">
                        <ShieldAlert className="h-4 w-4" /> Safe Mode
                      </span>
                    }
                    description={result.data.risk.safeMode.recommendation}
                  />
                  <div className="divide-y divide-rose-100/70">
                    {result.data.risk.safeMode.triggers.map((trigger, index) => (
                      <div key={`${trigger.code}-${index}`} className="px-5 py-2.5 text-xs">
                        <div className="font-medium text-rose-800">{trigger.message}</div>
                        <div className="mt-0.5 font-mono text-[10px] text-rose-500/80">{trigger.code}</div>
                      </div>
                    ))}
                  </div>
                </Card>
              ) : null}

              <Card>
                <CardHeader title="变更符号" description={`${result.data.changedSymbols.length} 个`} />
                <div className="max-h-[360px] divide-y divide-slate-100 overflow-y-auto">
                  {result.data.changedSymbols.map((symbol) => (
                    <div key={`${symbol.key.kind}-${symbol.file}-${symbol.key.name}-${symbol.line}`} className="px-5 py-2.5">
                      <div className="flex items-center gap-1.5">
                        <span className="rounded bg-slate-100 px-1 py-0.5 font-mono text-[9px] text-slate-500">
                          {symbol.changeType}
                        </span>
                        <span className="truncate font-mono text-[11px] text-slate-800" title={symbolLabel(symbol.key)}>
                          {symbolLabel(symbol.key)}
                        </span>
                      </div>
                      <div className="mt-1 flex items-center gap-2 text-[10px] text-slate-400">
                        <span className="truncate">{symbol.file.split('/').slice(-2).join('/')}:{symbol.line}</span>
                        {symbol.publicDto ? <span className="text-indigo-500">DTO</span> : null}
                        {symbol.controllerHandler ? <span className="text-sky-500">Handler</span> : null}
                      </div>
                    </div>
                  ))}
                  {result.data.changedSymbols.length === 0 ? (
                    <div className="px-5 py-8 text-center text-xs text-slate-400">没有变更符号</div>
                  ) : null}
                </div>
              </Card>

              {result.data.apiContractChanges.length ? (
                <Card>
                  <CardHeader title="API 契约变更" />
                  <div className="divide-y divide-slate-100">
                    {result.data.apiContractChanges.map((change) => (
                      <div key={`${change.type}-${change.apiKey}`} className="flex items-center gap-2 px-5 py-2.5 text-xs">
                        <span
                          className={cn(
                            'rounded px-1.5 py-0.5 text-[10px] font-semibold',
                            change.type === 'REMOVED'
                              ? 'bg-rose-50 text-rose-700'
                              : change.type === 'ADDED'
                                ? 'bg-emerald-50 text-emerald-700'
                                : 'bg-amber-50 text-amber-700',
                          )}
                        >
                          {change.type}
                        </span>
                        <span className="truncate font-mono text-slate-700">{change.apiKey}</span>
                      </div>
                    ))}
                  </div>
                </Card>
              ) : null}

              {result.data.warnings.length ? (
                <Card className="border-amber-200">
                  <CardHeader
                    title={
                      <span className="flex items-center gap-1.5 text-amber-800">
                        <AlertTriangle className="h-4 w-4" /> 警告
                      </span>
                    }
                  />
                  <div className="divide-y divide-amber-100/70">
                    {result.data.warnings.map((warning, index) => (
                      <div key={`${warning.code}-${index}`} className="px-5 py-2.5 text-xs">
                        <div className="font-mono text-[10px] text-amber-600">{warning.code}</div>
                        <div className="mt-0.5 text-amber-800">{warning.message}</div>
                      </div>
                    ))}
                  </div>
                </Card>
              ) : null}

              <Card>
                <CardHeader title="源码入口" action={<Code2 className="h-4 w-4 text-slate-300" />} />
                <div className="px-5 py-4 text-[11px] leading-5 text-slate-500">
                  报告中的每个节点与证据路径都可以打开对应源码片段。源码只在本地读取，最多返回 200 行。
                </div>
              </Card>

              <Link
                to={`/analyses/${analysisId}/graph`}
                className="flex items-center justify-between rounded-xl border border-slate-200 bg-white px-5 py-4 text-xs font-medium text-slate-700 transition hover:border-indigo-200 hover:bg-indigo-50/40"
              >
                <span className="flex items-center gap-2">
                  <GitCompareArrows className="h-4 w-4 text-slate-400" />
                  打开影响图
                </span>
                <ArrowRight className="h-3.5 w-3.5 text-slate-400" />
              </Link>
            </div>
          </div>
        </>
      ) : null}
    </div>
  )
}
