import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  AlertTriangle,
  ArrowRight,
  FileCode2,
  FileDiff,
  GitCompareArrows,
  Search,
  ShieldAlert,
} from 'lucide-react'
import { PageHeader } from '../components/PageHeader'
import { SourceDrawer, type SourceTarget } from '../components/SourceDrawer'
import { Card, CardHeader, EmptyState, InlineError, Spinner, Stat } from '../components/ui'
import { api } from '../lib/api'
import { cn } from '../lib/cn'
import { shortPath, symbolLabel } from '../lib/format'
import type { FileChange, SymbolChange } from '../lib/types'

const statusTone: Record<FileChange['status'], string> = {
  ADDED: 'bg-emerald-50 text-emerald-700 ring-emerald-200',
  MODIFIED: 'bg-amber-50 text-amber-700 ring-amber-200',
  DELETED: 'bg-rose-50 text-rose-700 ring-rose-200',
  RENAMED: 'bg-sky-50 text-sky-700 ring-sky-200',
}

const statusLabel: Record<FileChange['status'], string> = {
  ADDED: '新增',
  MODIFIED: '修改',
  DELETED: '删除',
  RENAMED: '重命名',
}

const changeTone: Record<string, string> = {
  ADDED: 'bg-emerald-50 text-emerald-700',
  REMOVED: 'bg-rose-50 text-rose-700',
  STRUCTURE_CHANGED: 'bg-amber-50 text-amber-700',
  CONTRACT_CHANGED: 'bg-orange-50 text-orange-700',
  MODIFIED: 'bg-sky-50 text-sky-700',
}

type Tab = 'files' | 'symbols' | 'contracts'

function kindLabel(kind: SymbolChange['key']['kind']): string {
  return kind === 'CLASS' ? '类' : kind === 'METHOD' ? '方法' : '字段'
}

export function ChangesPage() {
  const { analysisId = '' } = useParams()
  const [tab, setTab] = useState<Tab>('files')
  const [query, setQuery] = useState('')
  const [source, setSource] = useState<SourceTarget | null>(null)

  const status = useQuery({
    queryKey: ['analysis', analysisId, 'status'],
    queryFn: () => api.analysisStatus(analysisId),
  })
  const succeeded = status.data?.status === 'SUCCEEDED'
  const changes = useQuery({
    queryKey: ['analysis', analysisId, 'changes'],
    queryFn: () => api.changes(analysisId),
    enabled: succeeded,
  })

  const needle = query.trim().toLowerCase()
  const files = useMemo(
    () =>
      (changes.data?.files ?? []).filter((file) =>
        needle ? `${file.path} ${file.oldPath ?? ''}`.toLowerCase().includes(needle) : true,
      ),
    [changes.data, needle],
  )
  const symbols = useMemo(
    () =>
      (changes.data?.symbols ?? []).filter((symbol) =>
        needle
          ? `${symbol.file} ${symbol.key.owner} ${symbol.key.name} ${symbol.changeType}`.toLowerCase().includes(needle)
          : true,
      ),
    [changes.data, needle],
  )
  const contracts = useMemo(
    () =>
      (changes.data?.contractChanges ?? []).filter((change) =>
        needle ? `${change.apiKey} ${change.type}`.toLowerCase().includes(needle) : true,
      ),
    [changes.data, needle],
  )

  if (status.error) return <InlineError error={status.error} />
  if (!status.isLoading && !succeeded) {
    return (
      <div>
        <PageHeader back={{ to: `/analyses/${analysisId}`, label: '返回报告' }} title="变更清单" />
        <Card>
          <div className="flex items-center gap-2 px-5 py-10 text-sm text-slate-500">
            <Spinner />
            分析尚未完成，完成后即可查看变更清单。
          </div>
        </Card>
      </div>
    )
  }
  if (changes.isLoading) {
    return (
      <div className="flex items-center gap-2 py-16 text-sm text-slate-500">
        <Spinner /> 加载变更…
      </div>
    )
  }
  if (changes.error) return <InlineError error={changes.error} />
  if (!changes.data) return null

  const javaFiles = changes.data.files.filter((file) => file.javaFile).length

  return (
    <div>
      <PageHeader
        back={{ to: `/analyses/${analysisId}`, label: '返回报告' }}
        title="变更清单"
        description="Git 层面的文件变更、符号级变更与 API 契约变化。点击文件或符号可查看源码。"
        actions={
          <Link
            to={`/analyses/${analysisId}/graph`}
            className="inline-flex items-center gap-1 text-xs font-medium text-indigo-600 hover:text-indigo-700"
          >
            影响图 <ArrowRight className="h-3.5 w-3.5" />
          </Link>
        }
      />

      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <Stat label="变更文件" value={changes.data.files.length} hint={`Java ${javaFiles}`} />
        <Stat label="变更符号" value={changes.data.symbols.length} />
        <Stat label="契约变更" value={changes.data.contractChanges.length} />
        <Stat
          label="公共 DTO 变更"
          value={changes.data.symbols.filter((symbol) => symbol.publicDto).length}
          tone={changes.data.symbols.some((symbol) => symbol.publicDto) ? 'warning' : undefined}
        />
      </div>

      <Card className="mt-4">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-100 px-4 py-3">
          <div className="flex rounded-lg bg-slate-100 p-0.5">
            {(
              [
                ['files', `文件 ${changes.data.files.length}`],
                ['symbols', `符号 ${changes.data.symbols.length}`],
                ['contracts', `契约 ${changes.data.contractChanges.length}`],
              ] as [Tab, string][]
            ).map(([key, label]) => (
              <button
                key={key}
                onClick={() => setTab(key)}
                className={cn(
                  'rounded-md px-3 py-1.5 text-xs font-medium transition',
                  tab === key ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-700',
                )}
              >
                {label}
              </button>
            ))}
          </div>
          <div className="relative">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-slate-400" />
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="过滤路径或符号"
              className="h-8 w-56 rounded-lg border border-slate-200 pl-8 pr-3 text-xs outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
            />
          </div>
        </div>

        {tab === 'files' ? (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50/80 text-[11px] text-slate-500">
                <tr>
                  <th className="px-5 py-2.5 font-medium">文件</th>
                  <th className="px-3 py-2.5 font-medium">状态</th>
                  <th className="px-3 py-2.5 font-medium">类型</th>
                  <th className="px-5 py-2.5 font-medium">变更行</th>
                  <th className="px-5 py-2.5 text-right font-medium">源码</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {files.map((file) => (
                  <tr key={`${file.status}-${file.path}`} className="transition hover:bg-slate-50/60">
                    <td className="px-5 py-3">
                      <div className="font-mono text-slate-800">{file.path}</div>
                      {file.oldPath ? (
                        <div className="mt-0.5 font-mono text-[10px] text-slate-400">来自 {file.oldPath}</div>
                      ) : null}
                    </td>
                    <td className="px-3 py-3">
                      <span
                        className={cn(
                          'inline-flex rounded-md px-1.5 py-0.5 text-[10px] font-semibold ring-1 ring-inset',
                          statusTone[file.status],
                        )}
                      >
                        {statusLabel[file.status]}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-slate-500">
                      {file.javaFile ? 'Java' : file.binary ? '二进制' : '其他'}
                    </td>
                    <td className="px-5 py-3 font-mono text-[11px] text-slate-500">
                      {file.ranges.length
                        ? file.ranges.map((range) => `${range.start}-${range.end}`).join(', ')
                        : '—'}
                    </td>
                    <td className="px-5 py-3 text-right">
                      <button
                        className="inline-flex items-center gap-1 text-[11px] font-medium text-indigo-600 hover:text-indigo-700 disabled:text-slate-300"
                        disabled={file.binary}
                        onClick={() =>
                          setSource({
                            file: file.path,
                            line: file.ranges[0]?.start ?? 1,
                            label: shortPath(file.path, 80),
                          })
                        }
                      >
                        <FileCode2 className="h-3 w-3" />
                        查看
                      </button>
                    </td>
                  </tr>
                ))}
                {files.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-5 py-12 text-center text-slate-400">
                      没有匹配的文件
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        ) : null}

        {tab === 'symbols' ? (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50/80 text-[11px] text-slate-500">
                <tr>
                  <th className="px-5 py-2.5 font-medium">符号</th>
                  <th className="px-3 py-2.5 font-medium">变更</th>
                  <th className="px-3 py-2.5 font-medium">标记</th>
                  <th className="px-3 py-2.5 font-medium">位置</th>
                  <th className="px-5 py-2.5 font-medium">说明</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {symbols.map((symbol, index) => (
                  <tr
                    key={`${symbol.key.kind}-${symbol.file}-${symbol.key.name}-${symbol.line}-${index}`}
                    className="transition hover:bg-slate-50/60"
                  >
                    <td className="px-5 py-3">
                      <div className="flex items-center gap-1.5">
                        <span className="rounded bg-slate-100 px-1 py-0.5 text-[9px] text-slate-500">
                          {kindLabel(symbol.key.kind)}
                        </span>
                        <span className="font-mono text-[11px] text-slate-800">{symbolLabel(symbol.key)}</span>
                      </div>
                    </td>
                    <td className="px-3 py-3">
                      <span
                        className={cn(
                          'rounded px-1.5 py-0.5 text-[10px] font-semibold',
                          changeTone[symbol.changeType] ?? 'bg-slate-100 text-slate-600',
                        )}
                      >
                        {symbol.changeType}
                      </span>
                    </td>
                    <td className="px-3 py-3">
                      <div className="flex flex-wrap gap-1">
                        {symbol.publicDto ? (
                          <span className="rounded bg-indigo-50 px-1 py-0.5 text-[9px] font-medium text-indigo-700">
                            DTO
                          </span>
                        ) : null}
                        {symbol.controllerHandler ? (
                          <span className="rounded bg-sky-50 px-1 py-0.5 text-[9px] font-medium text-sky-700">
                            Handler
                          </span>
                        ) : null}
                        {!symbol.publicDto && !symbol.controllerHandler ? (
                          <span className="text-slate-300">—</span>
                        ) : null}
                      </div>
                    </td>
                    <td className="px-3 py-3">
                      <button
                        className="text-left font-mono text-[10px] text-indigo-600 hover:text-indigo-700"
                        onClick={() =>
                          setSource({
                            file: symbol.file,
                            line: Math.max(1, symbol.line),
                            label: symbolLabel(symbol.key),
                          })
                        }
                      >
                        {shortPath(symbol.file, 44)}:{symbol.line}
                      </button>
                    </td>
                    <td className="px-5 py-3 text-[11px] text-slate-500">{symbol.detail || '—'}</td>
                  </tr>
                ))}
                {symbols.length === 0 ? (
                  <tr>
                    <td colSpan={5} className="px-5 py-12 text-center text-slate-400">
                      没有匹配的符号变更
                    </td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        ) : null}

        {tab === 'contracts' ? (
          <div className="divide-y divide-slate-100">
            {contracts.map((change) => (
              <div key={`${change.type}-${change.apiKey}`} className="flex items-start gap-3 px-5 py-3">
                <span
                  className={cn(
                    'mt-0.5 shrink-0 rounded px-1.5 py-0.5 text-[10px] font-semibold',
                    change.type === 'REMOVED'
                      ? 'bg-rose-50 text-rose-700'
                      : change.type === 'ADDED'
                        ? 'bg-emerald-50 text-emerald-700'
                        : 'bg-amber-50 text-amber-700',
                  )}
                >
                  {change.type}
                </span>
                <div className="min-w-0">
                  <div className="font-mono text-[11px] text-slate-800">{change.apiKey}</div>
                  <div className="mt-0.5 text-[11px] text-slate-500">{change.detail || '契约发生变化'}</div>
                </div>
              </div>
            ))}
            {contracts.length === 0 ? (
              <EmptyState
                icon={<GitCompareArrows className="h-5 w-5" />}
                title="没有 API 契约变更"
                description="契约变更来自 HTTP 映射、路径、条件或公共 DTO 的结构变化。"
              />
            ) : null}
          </div>
        ) : null}
      </Card>

      {changes.data.warnings.length ? (
        <Card className="mt-4 border-amber-200">
          <CardHeader
            title={
              <span className="flex items-center gap-1.5 text-amber-800">
                <AlertTriangle className="h-4 w-4" /> 分析警告
              </span>
            }
            description="NOCLASSPATH 解析下的常见缺失：依赖类、框架注解或动态调用。"
          />
          <div className="divide-y divide-amber-100/70">
            {changes.data.warnings.map((warning, index) => (
              <div key={`${warning.code}-${index}`} className="px-5 py-2.5 text-xs">
                <div className="font-mono text-[10px] text-amber-600">{warning.code}</div>
                <div className="mt-0.5 text-amber-800">{warning.message}</div>
              </div>
            ))}
          </div>
        </Card>
      ) : null}

      {changes.data.symbols.some((symbol) => symbol.publicDto) ? (
        <Card className="mt-4 border-indigo-200 bg-indigo-50/40">
          <div className="flex items-start gap-2 px-5 py-3 text-xs text-indigo-900">
            <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0 text-indigo-500" />
            <span>
              检测到公共 DTO 契约变化。请确认序列化字段、空值与兼容性策略，必要时扩大 API 回归范围。
            </span>
          </div>
        </Card>
      ) : null}

      <div className="mt-4 flex items-center justify-end gap-3 text-xs">
        <span className="inline-flex items-center gap-1 text-slate-400">
          <FileDiff className="h-3.5 w-3.5" />
          {changes.data.files.length} 个文件
        </span>
        <Link
          to={`/analyses/${analysisId}`}
          className="inline-flex items-center gap-1 font-medium text-slate-500 hover:text-slate-800"
        >
          返回报告 <ArrowRight className="h-3.5 w-3.5" />
        </Link>
      </div>

      <SourceDrawer analysisId={analysisId} target={source} onClose={() => setSource(null)} />
    </div>
  )
}
