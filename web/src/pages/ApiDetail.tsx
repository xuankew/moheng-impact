import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  AlertTriangle,
  ArrowRight,
  ChevronRight,
  FileCode2,
  GitCompareArrows,
  Info,
  Network,
  ShieldAlert,
} from 'lucide-react'
import { PageHeader } from '../components/PageHeader'
import { SourceDrawer, type SourceTarget } from '../components/SourceDrawer'
import {
  Button,
  Card,
  CardHeader,
  InlineError,
  RiskBadge,
  Spinner,
  Stat,
  VerBadge,
} from '../components/ui'
import { api } from '../lib/api'
import { cn } from '../lib/cn'
import { pct, shortOwner } from '../lib/format'
import type { GraphEdge, GraphNode, RiskLevel } from '../lib/types'

function keyLabel(key: string): string {
  if (!key) return '—'
  if (key.startsWith('METHOD:')) {
    const rest = key.slice('METHOD:'.length)
    const hash = rest.indexOf('#')
    if (hash < 0) return rest
    return `${shortOwner(rest.slice(0, hash))}#${rest.slice(hash + 1)}`
  }
  if (key.startsWith('CLASS:')) return shortOwner(key.slice('CLASS:'.length))
  if (key.startsWith('FIELD:')) {
    const rest = key.slice('FIELD:'.length)
    const hash = rest.indexOf('#')
    if (hash < 0) return rest
    return `${shortOwner(rest.slice(0, hash))}#${rest.slice(hash + 1)}`
  }
  if (key.startsWith('API:')) return key.slice('API:'.length)
  return key
}

function NodeChip({ node, nodeKey }: { node?: GraphNode; nodeKey: string }) {
  return (
    <span
      className={cn(
        'inline-flex max-w-full items-center gap-1.5 rounded-md border px-2 py-1 font-mono text-[11px]',
        node?.changed ? 'border-rose-200 bg-rose-50 text-rose-700' : 'border-slate-200 bg-slate-50 text-slate-700',
      )}
      title={nodeKey}
    >
      {node?.changed ? <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-rose-500" /> : null}
      <span className="truncate">{node?.name ?? keyLabel(nodeKey)}</span>
    </span>
  )
}

const levelCopy: Record<RiskLevel, string> = {
  CRITICAL: '高影响 API：建议暂缓发布并执行全量回归。',
  HIGH: '高风险 API：建议扩大回归范围并优先验证。',
  MEDIUM: '中等风险 API：建议覆盖核心场景与相关契约。',
  LOW: '低风险 API：按常规回归执行即可。',
}

export function ApiDetailPage() {
  const { analysisId = '', apiId = '' } = useParams()
  const [source, setSource] = useState<SourceTarget | null>(null)

  const status = useQuery({
    queryKey: ['analysis', analysisId, 'status'],
    queryFn: () => api.analysisStatus(analysisId),
  })
  const succeeded = status.data?.status === 'SUCCEEDED'
  const detail = useQuery({
    queryKey: ['analysis', analysisId, 'api', apiId],
    queryFn: () => api.apiDetail(analysisId, apiId),
    enabled: succeeded,
  })
  const result = useQuery({
    queryKey: ['analysis', analysisId, 'result'],
    queryFn: () => api.analysisResult(analysisId),
    enabled: succeeded,
  })

  const nodes = result.data?.graph.nodes ?? {}
  const contractChange = useMemo(
    () => result.data?.apiContractChanges.find((change) => change.apiKey === detail.data?.api.key),
    [result.data, detail.data],
  )
  const changedKeys = useMemo(() => {
    const keys = new Set<string>()
    for (const path of detail.data?.paths ?? []) keys.add(path.changedKey)
    return [...keys]
  }, [detail.data])

  if (status.error) return <InlineError error={status.error} />
  if (!status.isLoading && !succeeded) {
    return (
      <div>
        <PageHeader back={{ to: `/analyses/${analysisId}`, label: '返回报告' }} title="API 详情" />
        <Card>
          <div className="flex items-center gap-2 px-5 py-10 text-sm text-slate-500">
            <Spinner />
            分析尚未完成，完成后即可查看 API 详情。
          </div>
        </Card>
      </div>
    )
  }
  if (detail.isLoading) {
    return (
      <div className="flex items-center gap-2 py-16 text-sm text-slate-500">
        <Spinner /> 加载 API 详情…
      </div>
    )
  }
  if (detail.error) return <InlineError error={detail.error} />
  if (!detail.data) return null

  const data = detail.data
  const apiInfo = data.api

  return (
    <div>
      <PageHeader
        back={{ to: `/analyses/${analysisId}`, label: '返回报告' }}
        title={
          <span className="flex flex-wrap items-center gap-2">
            <VerBadge verb={apiInfo.verb} />
            <span className="font-mono text-lg">{apiInfo.path}</span>
            {apiInfo.inherited ? (
              <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-500">继承</span>
            ) : null}
          </span>
        }
        description={
          <span className="flex flex-wrap items-center gap-2 font-mono text-[11px]">
            <span>{apiInfo.controller}</span>
            <span className="text-slate-300">·</span>
            <span>{apiInfo.handlerName}</span>
          </span>
        }
        actions={
          <div className="flex items-center gap-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setSource({ file: apiInfo.file, line: apiInfo.line, label: `${apiInfo.handlerOwner}#${apiInfo.handlerName}` })}
            >
              <FileCode2 className="h-3.5 w-3.5" />
              源码
            </Button>
            <Link to={`/analyses/${analysisId}/graph?apiKey=${encodeURIComponent(apiInfo.key)}`}>
              <Button variant="primary" size="sm">
                <Network className="h-3.5 w-3.5" />
                影响图
              </Button>
            </Link>
          </div>
        }
      />

      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <Stat label="风险等级" value={<RiskBadge level={data.level} />} />
        <Stat
          label="风险分"
          value={data.score}
          tone={data.score >= 60 ? 'danger' : data.score >= 40 ? 'warning' : undefined}
        />
        <Stat label="路径深度" value={data.depth} hint="从变更符号到 API" />
        <Stat label="置信度" value={pct(data.confidence)} hint="路径上最低边置信度" />
      </div>

      <div className="mt-4 grid gap-4 xl:grid-cols-3">
        <Card className={cn('xl:col-span-2', data.level === 'HIGH' || data.level === 'CRITICAL' ? 'border-orange-200' : '')}>
          <CardHeader
            title="风险说明"
            description={levelCopy[data.level]}
            action={<Info className="h-4 w-4 text-slate-300" />}
          />
          <div className="divide-y divide-slate-100">
            {data.reasons.map((reason, index) => (
              <div key={`${reason.code}-${index}`} className="flex items-start gap-3 px-5 py-3">
                <span
                  className={cn(
                    'mt-0.5 inline-flex shrink-0 items-center rounded px-1.5 py-0.5 font-mono text-[10px] font-semibold',
                    reason.weight >= 15 ? 'bg-rose-50 text-rose-600' : 'bg-amber-50 text-amber-700',
                  )}
                >
                  +{reason.weight}
                </span>
                <div className="min-w-0">
                  <div className="text-xs font-medium text-slate-800">{reason.message}</div>
                  <div className="mt-0.5 font-mono text-[10px] text-slate-400">{reason.code}</div>
                  {Object.keys(reason.evidence ?? {}).length ? (
                    <div className="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-[10px] text-slate-500">
                      {Object.entries(reason.evidence).map(([key, value]) => (
                        <span key={key}>
                          {key}=<span className="font-mono">{typeof value === 'object' ? JSON.stringify(value) : String(value)}</span>
                        </span>
                      ))}
                    </div>
                  ) : null}
                </div>
              </div>
            ))}
            {data.reasons.length === 0 ? (
              <div className="px-5 py-8 text-center text-xs text-slate-400">没有额外风险因子，仅由调用路径推导。</div>
            ) : null}
          </div>
          {data.risk.safeMode.enabled ? (
            <div className="flex items-start gap-2 border-t border-rose-100 bg-rose-50/60 px-5 py-3 text-xs text-rose-800">
              <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0 text-rose-500" />
              <span>
                Safe Mode 已启用：{data.risk.safeMode.recommendation || '建议全量回归。'}
              </span>
            </div>
          ) : null}
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader title="接口定义" />
            <dl className="divide-y divide-slate-100 text-xs">
              <div className="flex gap-3 px-5 py-2.5">
                <dt className="w-16 shrink-0 text-slate-500">Key</dt>
                <dd className="break-all font-mono text-[11px] text-slate-700">{apiInfo.key}</dd>
              </div>
              <div className="flex gap-3 px-5 py-2.5">
                <dt className="w-16 shrink-0 text-slate-500">位置</dt>
                <dd className="break-all font-mono text-[11px] text-slate-700">
                  {apiInfo.file}:{apiInfo.line}
                </dd>
              </div>
              <div className="flex gap-3 px-5 py-2.5">
                <dt className="w-16 shrink-0 text-slate-500">Handler</dt>
                <dd className="break-all font-mono text-[11px] text-slate-700">
                  {shortOwner(apiInfo.handlerOwner)}#{apiInfo.handlerName}
                </dd>
              </div>
              <div className="flex gap-3 px-5 py-2.5">
                <dt className="w-16 shrink-0 text-slate-500">类路径</dt>
                <dd className="break-all font-mono text-[11px] text-slate-700">
                  {apiInfo.classPaths.length ? apiInfo.classPaths.join(', ') : '—'}
                </dd>
              </div>
              <div className="flex gap-3 px-5 py-2.5">
                <dt className="w-16 shrink-0 text-slate-500">方法路径</dt>
                <dd className="break-all font-mono text-[11px] text-slate-700">
                  {apiInfo.methodPaths.length ? apiInfo.methodPaths.join(', ') : '—'}
                </dd>
              </div>
              <div className="flex gap-3 px-5 py-2.5">
                <dt className="w-16 shrink-0 text-slate-500">解析</dt>
                <dd className="font-mono text-[11px] text-slate-700">{apiInfo.annotationResolution}</dd>
              </div>
              {Object.keys(apiInfo.conditions ?? {}).length ? (
                <div className="flex gap-3 px-5 py-2.5">
                  <dt className="w-16 shrink-0 text-slate-500">条件</dt>
                  <dd className="space-y-1 text-[11px] text-slate-700">
                    {Object.entries(apiInfo.conditions).map(([key, values]) => (
                      <div key={key} className="font-mono">
                        {key}: {values.join(', ')}
                      </div>
                    ))}
                  </dd>
                </div>
              ) : null}
            </dl>
          </Card>

          {contractChange ? (
            <Card className="border-amber-200">
              <CardHeader
                title={
                  <span className="flex items-center gap-1.5 text-amber-800">
                    <GitCompareArrows className="h-4 w-4" /> API 契约变更
                  </span>
                }
                description={contractChange.detail || contractChange.type}
              />
              <div className="px-5 py-3 text-xs text-amber-800">
                <span className="rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold">{contractChange.type}</span>
                <div className="mt-2 break-all font-mono text-[11px]">{contractChange.apiKey}</div>
              </div>
            </Card>
          ) : null}

          {changedKeys.length ? (
            <Card>
              <CardHeader title="变更起点" description={`${changedKeys.length} 个符号`} />
              <div className="divide-y divide-slate-100">
                {changedKeys.map((key) => {
                  const node = nodes[key]
                  return (
                    <div key={key} className="px-5 py-2.5">
                      <div className="truncate font-mono text-[11px] text-slate-800">{node?.name ?? keyLabel(key)}</div>
                      <div className="mt-0.5 flex items-center justify-between gap-2">
                        <span className="truncate font-mono text-[10px] text-slate-400">
                          {node?.file ?? key}
                          {node?.line ? `:${node.line}` : ''}
                        </span>
                        {node?.file ? (
                          <button
                            className="shrink-0 text-[10px] font-medium text-indigo-600 hover:text-indigo-700"
                            onClick={() => setSource({ file: node.file, line: node.line, label: node.name })}
                          >
                            源码
                          </button>
                        ) : null}
                      </div>
                    </div>
                  )
                })}
              </div>
            </Card>
          ) : null}
        </div>
      </div>

      <Card className="mt-4">
        <CardHeader
          title="影响路径"
          description={`共 ${data.paths.length} 条路径，按置信度排序；每一行都对应调用图中的真实边。`}
          action={<Network className="h-4 w-4 text-slate-300" />}
        />
        <div className="space-y-3 p-4">
          {data.paths.map((path, pathIndex) => (
            <div key={`${path.changedKey}-${pathIndex}`} className="rounded-lg border border-slate-200">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 bg-slate-50/60 px-4 py-2">
                <div className="flex items-center gap-2 text-[11px] text-slate-500">
                  <span className="font-semibold text-slate-700">路径 {pathIndex + 1}</span>
                  <span className="text-slate-300">·</span>
                  深度 {path.depth}
                  <span className="text-slate-300">·</span>
                  置信度 {pct(path.confidence)}
                </div>
                <span className="truncate font-mono text-[10px] text-slate-400" title={path.changedKey}>
                  {keyLabel(path.changedKey)}
                </span>
              </div>
              <div className="divide-y divide-slate-100">
                {path.edges.map((edge: GraphEdge, edgeIndex) => (
                  <div key={`${edge.type}-${edgeIndex}`} className="px-4 py-2.5">
                    <div className="flex flex-wrap items-center gap-2">
                      <NodeChip node={nodes[edge.from]} nodeKey={edge.from} />
                      <span className="inline-flex items-center gap-1 font-mono text-[10px] text-slate-400">
                        {edge.type}
                        <ChevronRight className="h-3 w-3" />
                        {pct(edge.confidence)}
                      </span>
                      <NodeChip node={nodes[edge.to]} nodeKey={edge.to} />
                    </div>
                    <div className="mt-1.5 flex flex-wrap items-center gap-3 text-[10px] text-slate-400">
                      {edge.detail ? <span className="font-mono">{edge.detail}</span> : null}
                      {edge.evidence?.file ? (
                        <button
                          className="inline-flex items-center gap-1 font-mono text-indigo-600 hover:text-indigo-700"
                          onClick={() =>
                            setSource({
                              file: edge.evidence.file,
                              line: edge.evidence.line,
                              label: `${edge.type} · ${keyLabel(edge.from)} → ${keyLabel(edge.to)}`,
                            })
                          }
                        >
                          <FileCode2 className="h-3 w-3" />
                          {edge.evidence.file}:{edge.evidence.line}
                        </button>
                      ) : null}
                      {edge.evidence?.receiverType ? (
                        <span className="font-mono">receiver {shortOwner(edge.evidence.receiverType)}</span>
                      ) : null}
                      {edge.evidence?.message ? <span>{edge.evidence.message}</span> : null}
                    </div>
                  </div>
                ))}
                {path.edges.length === 0 ? (
                  <div className="px-4 py-3 text-xs text-slate-400">该路径没有可展示的边。</div>
                ) : null}
              </div>
            </div>
          ))}
          {data.paths.length === 0 ? (
            <div className="px-4 py-10 text-center text-xs text-slate-400">没有可用的影响路径。</div>
          ) : null}
        </div>
      </Card>

      {result.data?.warnings.length ? (
        <Card className="mt-4 border-amber-200">
          <CardHeader
            title={
              <span className="flex items-center gap-1.5 text-amber-800">
                <AlertTriangle className="h-4 w-4" /> 分析警告
              </span>
            }
            description="警告可能影响结果的覆盖范围，请结合源码判断。"
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

      <div className="mt-4 flex items-center justify-end">
        <Link to={`/analyses/${analysisId}/changes`} className="inline-flex items-center gap-1 text-xs font-medium text-slate-500 hover:text-slate-800">
          <ArrowRight className="h-3.5 w-3.5" />
          查看全部变更
        </Link>
      </div>

      <SourceDrawer analysisId={analysisId} target={source} onClose={() => setSource(null)} />
    </div>
  )
}
