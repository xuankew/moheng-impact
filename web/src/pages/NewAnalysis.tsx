import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { ArrowRight, ChevronDown, GitCompareArrows, Play, Settings2, Sparkles } from 'lucide-react'
import { PageHeader } from '../components/PageHeader'
import { Button, Card, CardHeader, Field, InlineError, Input, Spinner } from '../components/ui'
import { api } from '../lib/api'
import { cn } from '../lib/cn'
import { shortPath } from '../lib/format'

type Preset = 'worktree' | 'last' | 'five'

const presets: { key: Preset; label: string; description: string; base: string; head: string; mode: string }[] = [
  {
    key: 'worktree',
    label: '工作区未提交',
    description: 'HEAD → WORKTREE，适合提交前自检',
    base: 'HEAD',
    head: 'WORKTREE',
    mode: 'AUTO',
  },
  {
    key: 'last',
    label: '最近一次提交',
    description: 'HEAD~1 → HEAD',
    base: 'HEAD~1',
    head: 'HEAD',
    mode: 'REV_COMMIT',
  },
  {
    key: 'five',
    label: '最近五次提交',
    description: 'HEAD~5 → HEAD',
    base: 'HEAD~5',
    head: 'HEAD',
    mode: 'REV_COMMIT',
  },
]

function splitList(value: string): string[] {
  return value
    .split(/[,，\n]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

export function NewAnalysis() {
  const { projectId = '' } = useParams()
  const navigate = useNavigate()
  const project = useQuery({ queryKey: ['project', projectId], queryFn: () => api.project(projectId) })
  const [preset, setPreset] = useState<Preset>('worktree')
  const [base, setBase] = useState('HEAD')
  const [head, setHead] = useState('WORKTREE')
  const [headMode, setHeadMode] = useState('AUTO')
  const [advanced, setAdvanced] = useState(false)
  const [maxDepth, setMaxDepth] = useState('5')
  const [apiPrefix, setApiPrefix] = useState('')
  const [include, setInclude] = useState('')
  const [exclude, setExclude] = useState('')
  const [sourceRoots, setSourceRoots] = useState('')
  const [includeTests, setIncludeTests] = useState(false)
  const [highConfidenceOnly, setHighConfidenceOnly] = useState(false)

  const applyPreset = (key: Preset) => {
    const selected = presets.find((item) => item.key === key) ?? presets[0]
    setPreset(key)
    setBase(selected.base)
    setHead(selected.head)
    setHeadMode(selected.mode)
  }

  const create = useMutation({
    mutationFn: () =>
      api.createAnalysis(projectId, {
        base: base.trim(),
        head: head.trim(),
        headMode,
        maxDepth: Number(maxDepth) || 5,
        apiPrefix: apiPrefix.trim() || project.data?.apiPrefix || '',
        includePackages: splitList(include).length ? splitList(include) : project.data?.includePackages,
        excludePackages: splitList(exclude).length ? splitList(exclude) : project.data?.excludePackages,
        sourceRoots: splitList(sourceRoots).length ? splitList(sourceRoots) : project.data?.sourceRoots,
        includeTests,
        highConfidenceOnly,
      }),
    onSuccess: (run) => navigate(`/analyses/${run.id}`),
  })

  return (
    <div>
      <PageHeader
        back={{ to: '/projects', label: '返回项目' }}
        title={project.data ? `分析 · ${project.data.name}` : '新建分析'}
        description={
          project.data ? (
            <span className="font-mono text-[11px] text-slate-400">{project.data.repoPath}</span>
          ) : (
            '选择分析范围后开始；分析在独立 worker 进程中运行。'
          )
        }
      />

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="space-y-4 lg:col-span-2">
          <Card>
            <CardHeader title="分析范围" description="base 为对比起点，head 为终点。工作区模式会读取未提交文件。" />
            <div className="p-5">
              <div className="grid gap-2 sm:grid-cols-3">
                {presets.map((item) => (
                  <button
                    key={item.key}
                    onClick={() => applyPreset(item.key)}
                    className={cn(
                      'rounded-lg border px-3 py-3 text-left transition',
                      preset === item.key
                        ? 'border-indigo-300 bg-indigo-50/60 ring-1 ring-indigo-200'
                        : 'border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50/60',
                    )}
                  >
                    <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-800">
                      <GitCompareArrows className="h-3.5 w-3.5 text-slate-400" />
                      {item.label}
                    </div>
                    <div className="mt-1 text-[11px] leading-4 text-slate-500">{item.description}</div>
                  </button>
                ))}
              </div>

              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                <Field label="base" hint="分支、Tag 或提交 SHA；支持 HEAD~N。">
                  <Input
                    value={base}
                    onChange={(event) => {
                      setBase(event.target.value)
                      setPreset('worktree')
                    }}
                    className="font-mono text-xs"
                  />
                </Field>
                <Field label="head" hint="WORKTREE 表示读取当前工作区。">
                  <Input
                    value={head}
                    onChange={(event) => {
                      setHead(event.target.value)
                      setPreset('worktree')
                    }}
                    className="font-mono text-xs"
                  />
                </Field>
              </div>

              <Field
                label="head 模式"
                hint="AUTO：head 为 WORKTREE 时读工作区，否则读提交对象。"
                className="mt-3 max-w-xs"
              >
                <select
                  value={headMode}
                  onChange={(event) => setHeadMode(event.target.value)}
                  className="h-9 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-900 shadow-sm outline-none focus:border-indigo-400 focus:ring-2 focus:ring-indigo-100"
                >
                  <option value="AUTO">AUTO</option>
                  <option value="REV_COMMIT">REV_COMMIT</option>
                  <option value="WORKTREE">WORKTREE</option>
                </select>
              </Field>
            </div>
          </Card>

          <Card>
            <button
              className="flex w-full items-center justify-between px-5 py-4 text-left"
              onClick={() => setAdvanced((value) => !value)}
            >
              <div className="flex items-center gap-2 text-sm font-semibold text-slate-800">
                <Settings2 className="h-4 w-4 text-slate-400" />
                高级选项
              </div>
              <ChevronDown className={cn('h-4 w-4 text-slate-400 transition', advanced && 'rotate-180')} />
            </button>
            {advanced ? (
              <div className="grid gap-3 border-t border-slate-100 px-5 py-4 sm:grid-cols-2">
                <Field label="最大影响深度" hint="1–10，默认 5。">
                  <Input
                    type="number"
                    min={1}
                    max={10}
                    value={maxDepth}
                    onChange={(event) => setMaxDepth(event.target.value)}
                  />
                </Field>
                <Field label="API 前缀" hint="留空时使用项目配置。">
                  <Input value={apiPrefix} onChange={(event) => setApiPrefix(event.target.value)} placeholder="/admin" className="font-mono text-xs" />
                </Field>
                <Field label="包含包">
                  <Input value={include} onChange={(event) => setInclude(event.target.value)} placeholder="com.example.order" className="font-mono text-xs" />
                </Field>
                <Field label="排除包">
                  <Input value={exclude} onChange={(event) => setExclude(event.target.value)} placeholder="com.example.legacy" className="font-mono text-xs" />
                </Field>
                <Field label="源码根" className="sm:col-span-2">
                  <Input value={sourceRoots} onChange={(event) => setSourceRoots(event.target.value)} placeholder="src/main/java" className="font-mono text-xs" />
                </Field>
                <label className="flex items-center gap-2 text-xs text-slate-600">
                  <input
                    type="checkbox"
                    checked={includeTests}
                    onChange={(event) => setIncludeTests(event.target.checked)}
                    className="h-4 w-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
                  />
                  包含测试源码
                </label>
                <label className="flex items-center gap-2 text-xs text-slate-600">
                  <input
                    type="checkbox"
                    checked={highConfidenceOnly}
                    onChange={(event) => setHighConfidenceOnly(event.target.checked)}
                    className="h-4 w-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
                  />
                  仅高置信度路径（≥ 0.8）
                </label>
              </div>
            ) : null}
          </Card>
        </div>

        <div className="space-y-4">
          <Card>
            <CardHeader title="即将分析" action={<Sparkles className="h-4 w-4 text-slate-300" />} />
            <div className="space-y-3 px-5 py-4 text-xs">
              <div>
                <div className="text-[11px] text-slate-500">项目</div>
                <div className="mt-0.5 font-medium text-slate-800">{project.data?.name ?? '—'}</div>
              </div>
              <div>
                <div className="text-[11px] text-slate-500">范围</div>
                <div className="mt-0.5 font-mono text-slate-700">
                  {base || '—'} → {head || '—'}
                </div>
              </div>
              <div>
                <div className="text-[11px] text-slate-500">仓库</div>
                <div className="mt-0.5 break-all font-mono text-[11px] text-slate-500">
                  {project.data ? shortPath(project.data.repoPath, 80) : '—'}
                </div>
              </div>
              <div className="rounded-lg bg-slate-50 px-3 py-2 text-[11px] leading-5 text-slate-500">
                分析只读取 Git 对象与源码文件；worker 使用独立 JVM，默认堆 1g。
              </div>
            </div>
            <div className="border-t border-slate-100 px-5 py-4">
              {create.error ? (
                <div className="mb-3">
                  <InlineError error={create.error} />
                </div>
              ) : null}
              <Button
                variant="primary"
                className="w-full"
                onClick={() => create.mutate()}
                disabled={create.isPending || !base.trim() || !head.trim()}
              >
                {create.isPending ? <Spinner className="text-white" /> : <Play className="h-4 w-4" />}
                开始分析
              </Button>
              <Link to="/projects" className="mt-3 block text-center text-[11px] text-slate-400 hover:text-slate-600">
                返回项目列表
              </Link>
            </div>
          </Card>

          {project.error ? <InlineError error={project.error} /> : null}
        </div>
      </div>
    </div>
  )
}
