import { useEffect, useMemo, useState } from 'react'
import type { ChangeEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ChevronRight,
  FolderGit2,
  FolderOpen,
  GitBranch,
  Pencil,
  Plus,
  Search,
  Trash2,
} from 'lucide-react'
import { PageHeader } from '../components/PageHeader'
import {
  Button,
  Card,
  Drawer,
  EmptyState,
  Field,
  InlineError,
  Input,
  Spinner,
} from '../components/ui'
import { api } from '../lib/api'
import type { Project } from '../lib/types'

interface FormState {
  name: string
  repoPath: string
  apiPrefix: string
  defaultBase: string
  includePackages: string
  excludePackages: string
  sourceRoots: string
}

const emptyForm: FormState = {
  name: '',
  repoPath: '',
  apiPrefix: '',
  defaultBase: 'HEAD~1',
  includePackages: '',
  excludePackages: '',
  sourceRoots: '',
}

function toForm(project: Project): FormState {
  return {
    name: project.name,
    repoPath: project.repoPath,
    apiPrefix: project.apiPrefix,
    defaultBase: project.defaultBase ?? '',
    includePackages: project.includePackages.join(', '),
    excludePackages: project.excludePackages.join(', '),
    sourceRoots: project.sourceRoots.join(', '),
  }
}

function splitList(value: string): string[] {
  return value
    .split(/[,，\n]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

export function Projects() {
  const client = useQueryClient()
  const projects = useQuery({ queryKey: ['projects'], queryFn: api.projects })
  const [editing, setEditing] = useState<Project | 'new' | null>(null)
  const [form, setForm] = useState<FormState>(emptyForm)
  const [browsePath, setBrowsePath] = useState('')
  const [showBrowser, setShowBrowser] = useState(false)

  const dirs = useQuery({
    queryKey: ['workspace', browsePath],
    queryFn: () => api.workspaceDirs(browsePath),
    enabled: Boolean(editing),
  })

  useEffect(() => {
    if (editing === 'new') {
      setForm(emptyForm)
      setBrowsePath('')
      setShowBrowser(false)
    } else if (editing) {
      setForm(toForm(editing))
      setShowBrowser(false)
    }
  }, [editing])

  const save = useMutation({
    mutationFn: async () => {
      if (!form.repoPath.trim()) throw new Error('请填写仓库路径')
      const body = {
        name: form.name.trim(),
        repoPath: form.repoPath.trim(),
        apiPrefix: form.apiPrefix.trim(),
        defaultBase: form.defaultBase.trim(),
        includePackages: splitList(form.includePackages),
        excludePackages: splitList(form.excludePackages),
        sourceRoots: splitList(form.sourceRoots),
      }
      return editing === 'new' ? api.createProject(body) : api.updateProject(editing!.id, body)
    },
    onSuccess: () => {
      client.invalidateQueries({ queryKey: ['projects'] })
      setEditing(null)
    },
  })

  const remove = useMutation({
    mutationFn: (id: string) => api.deleteProject(id),
    onSuccess: () => client.invalidateQueries({ queryKey: ['projects'] }),
  })

  const breadcrumbs = useMemo(() => {
    const segments = browsePath.split('/').filter(Boolean)
    return segments.map((segment, index) => ({
      label: segment,
      path: segments.slice(0, index + 1).join('/'),
    }))
  }, [browsePath])

  const update = (key: keyof FormState) => (event: ChangeEvent<HTMLInputElement>) =>
    setForm((current) => ({ ...current, [key]: event.target.value }))

  return (
    <div>
      <PageHeader
        title="项目"
        description="登记本地 Git 仓库。墨衡只读分析，不会向仓库写入任何内容。"
        actions={
          <Button variant="primary" onClick={() => setEditing('new')}>
            <Plus className="h-4 w-4" />
            添加项目
          </Button>
        }
      />

      {projects.error ? <InlineError error={projects.error} /> : null}
      {projects.isLoading ? (
        <div className="flex items-center gap-2 py-10 text-sm text-slate-500">
          <Spinner /> 加载中…
        </div>
      ) : null}

      {projects.data && projects.data.length === 0 ? (
        <Card>
          <EmptyState
            icon={<FolderGit2 className="h-6 w-6" />}
            title="还没有项目"
            description="从工作区目录选择，或直接填写任意本地 Git 仓库的绝对路径。"
            action={
              <Button variant="primary" onClick={() => setEditing('new')}>
                <Plus className="h-4 w-4" />
                添加项目
              </Button>
            }
          />
        </Card>
      ) : null}

      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {(projects.data ?? []).map((project) => (
          <Card key={project.id} className="flex flex-col">
            <div className="flex items-start justify-between gap-3 px-5 pt-4">
              <div className="min-w-0">
                <div className="truncate text-sm font-semibold text-slate-900">{project.name}</div>
                <div className="mt-1 flex items-center gap-1 truncate font-mono text-[10px] text-slate-400" title={project.repoPath}>
                  <FolderOpen className="h-3 w-3 shrink-0" />
                  {project.repoPath}
                </div>
              </div>
              <FolderGit2 className="h-4 w-4 shrink-0 text-slate-300" />
            </div>
            <div className="mt-3 flex flex-wrap gap-1.5 px-5">
              <span className="inline-flex items-center gap-1 rounded-md bg-slate-100 px-1.5 py-0.5 text-[10px] text-slate-600">
                <GitBranch className="h-3 w-3" />
                {project.defaultBase || 'HEAD~1'}
              </span>
              {project.apiPrefix ? (
                <span className="rounded-md bg-slate-100 px-1.5 py-0.5 font-mono text-[10px] text-slate-600">
                  {project.apiPrefix}
                </span>
              ) : null}
              {project.includePackages.length ? (
                <span className="rounded-md bg-slate-100 px-1.5 py-0.5 text-[10px] text-slate-600">
                  包含 {project.includePackages.length}
                </span>
              ) : null}
            </div>
            <div className="mt-4 flex items-center justify-between border-t border-slate-100 px-3 py-2">
              <Link to={`/projects/${project.id}/analyses/new`}>
                <Button variant="ghost" size="sm">
                  <Search className="h-3.5 w-3.5" />
                  分析
                </Button>
              </Link>
              <div className="flex items-center gap-1">
                <Button variant="ghost" size="sm" onClick={() => setEditing(project)} aria-label="编辑">
                  <Pencil className="h-3.5 w-3.5" />
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  aria-label="删除"
                  onClick={() => {
                    if (window.confirm(`确认删除项目「${project.name}」？不会删除仓库本身。`)) {
                      remove.mutate(project.id)
                    }
                  }}
                >
                  <Trash2 className="h-3.5 w-3.5 text-rose-500" />
                </Button>
              </div>
            </div>
          </Card>
        ))}
      </div>

      <Drawer
        open={editing !== null}
        title={editing === 'new' ? '添加项目' : '编辑项目'}
        description="填写本地 Git 仓库的绝对路径，目录需包含 .git。墨衡只读分析，不会修改仓库。"
        onClose={() => setEditing(null)}
      >
        <div className="space-y-4">
          {save.error ? <InlineError error={save.error} /> : null}

          <Field label="仓库路径" hint="可手动输入绝对路径，或从工作区目录选择。">
            <div className="flex gap-2">
              <Input
                value={form.repoPath}
                onChange={update('repoPath')}
                placeholder="/Users/you/code/order-service"
                className="font-mono text-xs"
              />
              <Button
                variant="secondary"
                onClick={() => {
                  setShowBrowser((value) => !value)
                  if (!browsePath && dirs.data?.path) setBrowsePath(dirs.data.path === '.' ? '' : dirs.data.path)
                }}
              >
                <FolderOpen className="h-4 w-4" />
                浏览
              </Button>
            </div>
          </Field>

          {showBrowser ? (
            <div className="rounded-lg border border-slate-200 bg-slate-50/50">
              <div className="flex flex-wrap items-center gap-1 border-b border-slate-200 px-3 py-2 text-[11px] text-slate-500">
                <button className="hover:text-indigo-600" onClick={() => setBrowsePath('')}>
                  工作区
                </button>
                {breadcrumbs.map((crumb) => (
                  <span key={crumb.path} className="flex items-center gap-1">
                    <ChevronRight className="h-3 w-3 text-slate-300" />
                    <button className="hover:text-indigo-600" onClick={() => setBrowsePath(crumb.path)}>
                      {crumb.label}
                    </button>
                  </span>
                ))}
              </div>
              <div className="max-h-56 overflow-y-auto p-2">
                {dirs.isLoading ? (
                  <div className="flex items-center gap-2 px-2 py-3 text-xs text-slate-500">
                    <Spinner /> 读取目录…
                  </div>
                ) : null}
                {dirs.error ? <InlineError error={dirs.error} /> : null}
                {dirs.data?.parent !== null && dirs.data?.parent !== undefined ? (
                  <button
                    className="mb-1 flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left text-xs text-slate-500 hover:bg-white"
                    onClick={() => setBrowsePath(dirs.data!.parent ?? '')}
                  >
                    <FolderOpen className="h-3.5 w-3.5" />
                    上一级
                  </button>
                ) : null}
                {(dirs.data?.directories ?? []).map((dir) => (
                  <div
                    key={dir.path}
                    className="group flex items-center justify-between rounded-md px-2 py-1.5 hover:bg-white"
                  >
                    <button
                      className="flex min-w-0 flex-1 items-center gap-2 text-left text-xs text-slate-700"
                      onClick={() => setBrowsePath(dir.path)}
                    >
                      <FolderOpen className="h-3.5 w-3.5 shrink-0 text-slate-400" />
                      <span className="truncate">{dir.name}</span>
                      {dir.git ? (
                        <span className="shrink-0 rounded bg-emerald-50 px-1 py-0.5 text-[9px] font-medium text-emerald-700">
                          Git
                        </span>
                      ) : null}
                    </button>
                    {dir.git ? (
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => {
                          const root = (dirs.data?.root ?? '').replace(/\/+$/, '')
                          setForm((current) => ({ ...current, repoPath: root ? `${root}/${dir.path}` : dir.path }))
                          setShowBrowser(false)
                        }}
                      >
                        选择
                      </Button>
                    ) : null}
                  </div>
                ))}
                {(dirs.data?.directories ?? []).length === 0 && !dirs.isLoading ? (
                  <div className="px-2 py-4 text-center text-xs text-slate-400">此目录下没有子目录</div>
                ) : null}
              </div>
            </div>
          ) : null}

          <div className="grid grid-cols-2 gap-3">
            <Field label="项目名称" hint="留空时使用目录名。">
              <Input value={form.name} onChange={update('name')} placeholder="订单服务" />
            </Field>
            <Field label="默认 base">
              <Input value={form.defaultBase} onChange={update('defaultBase')} placeholder="HEAD~1" className="font-mono text-xs" />
            </Field>
          </div>

          <Field label="API 前缀" hint="可选。用于拼接类与方法路径，例如 /admin。">
            <Input value={form.apiPrefix} onChange={update('apiPrefix')} placeholder="/admin" className="font-mono text-xs" />
          </Field>

          <div className="grid grid-cols-1 gap-3">
            <Field label="包含包" hint="逗号分隔；留空表示全部。">
              <Input value={form.includePackages} onChange={update('includePackages')} placeholder="com.example.order" className="font-mono text-xs" />
            </Field>
            <Field label="排除包" hint="逗号分隔。">
              <Input value={form.excludePackages} onChange={update('excludePackages')} placeholder="com.example.legacy" className="font-mono text-xs" />
            </Field>
            <Field label="源码根" hint="可选；默认自动识别 src/main/java。">
              <Input value={form.sourceRoots} onChange={update('sourceRoots')} placeholder="src/main/java" className="font-mono text-xs" />
            </Field>
          </div>

          <div className="flex justify-end gap-2 border-t border-slate-100 pt-4">
            <Button variant="ghost" onClick={() => setEditing(null)}>
              取消
            </Button>
            <Button variant="primary" onClick={() => save.mutate()} disabled={save.isPending}>
              {save.isPending ? <Spinner className="text-white" /> : null}
              保存
            </Button>
          </div>
        </div>
      </Drawer>
    </div>
  )
}
