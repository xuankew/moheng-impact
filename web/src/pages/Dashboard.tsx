import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  ArrowRight,
  FolderGit2,
  GitCompareArrows,
  Plus,
  ShieldAlert,
  Sparkles,
} from 'lucide-react'
import { PageHeader } from '../components/PageHeader'
import {
  Button,
  Card,
  CardHeader,
  EmptyState,
  InlineError,
  Progress,
  RiskBadge,
  Spinner,
  Stat,
  StatusBadge,
  VerBadge,
} from '../components/ui'
import { api } from '../lib/api'
import { fmtTime } from '../lib/format'

export function Dashboard() {
  const projects = useQuery({ queryKey: ['projects'], queryFn: api.projects })
  const analyses = useQuery({
    queryKey: ['analyses', 'recent'],
    queryFn: () => api.analyses(undefined, 8),
    refetchInterval: 5_000,
  })
  const latestSucceeded = analyses.data?.find((run) => run.status === 'SUCCEEDED')
  const highRisk = useQuery({
    queryKey: ['apis', latestSucceeded?.id, 'HIGH'],
    queryFn: () => api.apiPage(latestSucceeded!.id, { risk: 'HIGH', size: 5 }),
    enabled: Boolean(latestSucceeded?.id),
  })

  const projectNames = new Map((projects.data ?? []).map((project) => [project.id, project.name]))
  const safeModeRuns = (analyses.data ?? []).filter((run) => run.summary?.safeMode).length
  const activeRun = analyses.data?.find((run) => run.status === 'RUNNING' || run.status === 'QUEUED')
  const latestProject = latestSucceeded ? projectNames.get(latestSucceeded.projectId) : undefined

  return (
    <div>
      <PageHeader
        title="概览"
        description="基于 Git 变更与静态调用图的本地影响分析。数据不出本机，报告可追溯。"
        actions={
          projects.data && projects.data.length > 0 ? (
            <Link to="/projects">
              <Button variant="primary">
                <Plus className="h-4 w-4" />
                新建分析
              </Button>
            </Link>
          ) : null
        }
      />

      {projects.isLoading || analyses.isLoading ? (
        <div className="flex items-center gap-2 py-10 text-sm text-slate-500">
          <Spinner /> 加载中…
        </div>
      ) : null}

      {projects.error ? <InlineError error={projects.error} /> : null}
      {analyses.error ? <InlineError error={analyses.error} /> : null}

      {projects.data && projects.data.length === 0 ? (
        <Card>
          <EmptyState
            icon={<FolderGit2 className="h-6 w-6" />}
            title="还没有登记项目"
            description="添加一个本地 Git 仓库，首次分析只需选择 base 与 head。"
            action={
              <Link to="/projects">
                <Button variant="primary">
                  <Plus className="h-4 w-4" />
                  添加项目
                </Button>
              </Link>
            }
          />
        </Card>
      ) : null}

      {projects.data && projects.data.length > 0 ? (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <Stat label="项目" value={projects.data.length} hint="本地挂载" />
            <Stat
              label="最近分析"
              value={analyses.data?.length ?? 0}
              hint={activeRun ? `进行中：${activeRun.phase ?? activeRun.status}` : '最近 8 次'}
            />
            <Stat
              label="高风险 API"
              value={highRisk.data?.total ?? '—'}
              hint={latestSucceeded ? `来自 ${fmtTime(latestSucceeded.startedAt)} 的报告` : '暂无已完成分析'}
              tone={(highRisk.data?.total ?? 0) > 0 ? 'danger' : undefined}
            />
            <Stat
              label="Safe Mode"
              value={safeModeRuns}
              hint="最近分析中触发次数"
              tone={safeModeRuns > 0 ? 'warning' : undefined}
            />
          </div>

          {activeRun ? (
            <Card className="mt-4 border-indigo-200 bg-indigo-50/40">
              <div className="flex flex-wrap items-center justify-between gap-4 px-5 py-4">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 text-sm font-semibold text-indigo-900">
                    <Spinner className="text-indigo-500" />
                    正在分析 {projectNames.get(activeRun.projectId) ?? ''}
                  </div>
                  <div className="mt-1 text-xs text-indigo-700/70">
                    {activeRun.phase ?? '排队中'} · {activeRun.base} → {activeRun.head}
                  </div>
                </div>
                <div className="w-48">
                  <Progress value={activeRun.progress} />
                  <div className="mt-1 text-right font-mono text-[10px] text-indigo-600">{activeRun.progress}%</div>
                </div>
                <Link to={`/analyses/${activeRun.id}`}>
                  <Button variant="secondary" size="sm">
                    查看进度 <ArrowRight className="h-3.5 w-3.5" />
                  </Button>
                </Link>
              </div>
            </Card>
          ) : null}

          {latestSucceeded ? (
            <Card className="mt-4 overflow-hidden">
              <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-100 bg-gradient-to-r from-slate-50 to-white px-5 py-4">
                <div className="min-w-0">
                  <div className="flex items-center gap-2 text-xs text-slate-500">
                    <GitCompareArrows className="h-3.5 w-3.5" />
                    最近一次完成
                    <span className="text-slate-300">·</span>
                    {latestProject ?? latestSucceeded.projectId}
                  </div>
                  <div className="mt-1 flex items-center gap-2">
                    <RiskBadge level={latestSucceeded.riskLevel ?? 'LOW'} score={latestSucceeded.riskScore} />
                    <span className="font-mono text-[11px] text-slate-500">
                      {latestSucceeded.base} → {latestSucceeded.head}
                    </span>
                    {latestSucceeded.summary?.safeMode ? (
                      <span className="inline-flex items-center gap-1 rounded-md bg-rose-50 px-2 py-0.5 text-[11px] font-medium text-rose-700 ring-1 ring-inset ring-rose-200">
                        <ShieldAlert className="h-3.5 w-3.5" />
                        Safe Mode
                      </span>
                    ) : null}
                  </div>
                </div>
                <Link to={`/analyses/${latestSucceeded.id}`}>
                  <Button variant="primary" size="sm">
                    打开报告 <ArrowRight className="h-3.5 w-3.5" />
                  </Button>
                </Link>
              </div>
              <div className="grid grid-cols-2 divide-x divide-slate-100 md:grid-cols-4">
                {[
                  ['变更符号', latestSucceeded.summary?.changedSymbols ?? 0],
                  ['受影响方法', latestSucceeded.summary?.impactedMethods ?? 0],
                  ['受影响 API', latestSucceeded.summary?.impactedApis ?? 0],
                  ['风险分', latestSucceeded.riskScore ?? 0],
                ].map(([label, value]) => (
                  <div key={String(label)} className="px-5 py-3">
                    <div className="text-[11px] text-slate-500">{label}</div>
                    <div className="mt-0.5 text-lg font-semibold text-slate-900">{String(value)}</div>
                  </div>
                ))}
              </div>
            </Card>
          ) : null}

          <div className="mt-4 grid gap-4 xl:grid-cols-5">
            <Card className="xl:col-span-3">
              <CardHeader
                title="最近分析"
                description="点击进入报告；分析中的任务每秒刷新状态。"
                action={
                  <Link to="/history" className="text-xs font-medium text-indigo-600 hover:text-indigo-700">
                    全部历史
                  </Link>
                }
              />
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-50/80 text-[11px] text-slate-500">
                    <tr>
                      <th className="px-5 py-2.5 font-medium">项目</th>
                      <th className="px-3 py-2.5 font-medium">状态</th>
                      <th className="px-3 py-2.5 font-medium">风险</th>
                      <th className="px-3 py-2.5 font-medium">受影响 API</th>
                      <th className="px-5 py-2.5 text-right font-medium">时间</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {(analyses.data ?? []).map((run) => (
                      <tr key={run.id} className="transition hover:bg-slate-50/60">
                        <td className="px-5 py-3">
                          <Link to={`/analyses/${run.id}`} className="font-medium text-slate-800 hover:text-indigo-700">
                            {projectNames.get(run.projectId) ?? run.projectId.slice(0, 8)}
                          </Link>
                          <div className="mt-0.5 font-mono text-[10px] text-slate-400">
                            {run.base} → {run.head}
                          </div>
                        </td>
                        <td className="px-3 py-3">
                          <StatusBadge status={run.status} />
                        </td>
                        <td className="px-3 py-3">
                          {run.riskLevel ? (
                            <RiskBadge level={run.riskLevel} score={run.riskScore} />
                          ) : (
                            <span className="text-slate-400">—</span>
                          )}
                        </td>
                        <td className="px-3 py-3 font-mono text-slate-600">
                          {run.summary?.impactedApis ?? '—'}
                        </td>
                        <td className="px-5 py-3 text-right text-slate-500">{fmtTime(run.startedAt)}</td>
                      </tr>
                    ))}
                    {(analyses.data ?? []).length === 0 ? (
                      <tr>
                        <td colSpan={5} className="px-5 py-8 text-center text-slate-400">
                          暂无分析记录
                        </td>
                      </tr>
                    ) : null}
                  </tbody>
                </table>
              </div>
            </Card>

            <Card className="xl:col-span-2">
              <CardHeader
                title="最近报告中的高风险 API"
                description={latestSucceeded ? `报告 ${fmtTime(latestSucceeded.startedAt)}` : '尚无已完成报告'}
                action={<Sparkles className="h-4 w-4 text-slate-300" />}
              />
              <div className="divide-y divide-slate-100">
                {(highRisk.data?.items ?? []).map((item) => (
                  <Link
                    key={item.id}
                    to={`/analyses/${latestSucceeded?.id}/apis/${item.id}`}
                    className="flex items-center justify-between gap-3 px-5 py-3 transition hover:bg-slate-50/60"
                  >
                    <div className="flex min-w-0 items-center gap-2">
                      <VerBadge verb={item.verb} />
                      <span className="truncate font-mono text-xs text-slate-700">{item.path}</span>
                    </div>
                    <span className="shrink-0 text-[11px] font-semibold text-rose-600">{item.score}</span>
                  </Link>
                ))}
                {(highRisk.data?.items ?? []).length === 0 ? (
                  <div className="px-5 py-8 text-center text-xs text-slate-400">
                    {latestSucceeded ? '最近报告没有高风险 API' : '完成一次分析后显示'}
                  </div>
                ) : null}
              </div>
            </Card>
          </div>
        </>
      ) : null}
    </div>
  )
}
