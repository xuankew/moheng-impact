import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Activity,
  Cpu,
  Database,
  FolderOpen,
  HardDrive,
  Info,
  Lock,
  RefreshCw,
  Timer,
} from 'lucide-react'
import { PageHeader } from '../components/PageHeader'
import { Button, Card, CardHeader, InlineError, Spinner } from '../components/ui'
import { api } from '../lib/api'
import { cn } from '../lib/cn'

function Dot({ ok }: { ok: boolean }) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-md px-2 py-0.5 text-[11px] font-medium ring-1 ring-inset',
        ok ? 'bg-emerald-50 text-emerald-700 ring-emerald-200' : 'bg-rose-50 text-rose-700 ring-rose-200',
      )}
    >
      <span className={cn('h-1.5 w-1.5 rounded-full', ok ? 'bg-emerald-500' : 'bg-rose-500')} />
      {ok ? '正常' : '不可用'}
    </span>
  )
}

const configRows: [string, string][] = [
  ['MOHENG_WORKSPACE_ROOT', '目录浏览与相对路径的起点（绝对路径不受限）'],
  ['MOHENG_DATA_DIR', 'SQLite 与临时文件目录'],
  ['MOHENG_WORKER_JAR', 'worker jar 路径（默认自动定位）'],
  ['MOHENG_WORKER_HEAP', 'worker 最大堆，默认 1g'],
  ['MOHENG_ANALYSIS_TIMEOUT_SECONDS', '单次分析超时，默认 600 秒'],
  ['MOHENG_PORT / MOHENG_BIND_ADDRESS', '服务端口与监听地址，默认 8848 / 127.0.0.1'],
]

export function SettingsPage() {
  const client = useQueryClient()
  const health = useQuery({ queryKey: ['health'], queryFn: api.health, refetchInterval: 30_000 })

  return (
    <div>
      <PageHeader
        title="设置"
        description="墨衡的运行信息与本地配置。V0.1 的配置通过环境变量提供，修改后需重启服务。"
        actions={
          <Button variant="secondary" size="sm" onClick={() => {
            client.invalidateQueries({ queryKey: ['health'] })
            health.refetch()
          }}>
            <RefreshCw className={cn('h-3.5 w-3.5', health.isFetching && 'animate-spin')} />
            刷新
          </Button>
        }
      />

      {health.error ? <InlineError error={health.error} /> : null}

      <div className="grid gap-4 xl:grid-cols-2">
        <Card>
          <CardHeader
            title="运行状态"
            description="后端健康检查每 30 秒自动刷新。"
            action={health.isFetching ? <Spinner /> : <Activity className="h-4 w-4 text-slate-300" />}
          />
          {health.data ? (
            <dl className="divide-y divide-slate-100 text-xs">
              <div className="flex items-center justify-between px-5 py-3">
                <dt className="flex items-center gap-2 text-slate-600">
                  <Activity className="h-3.5 w-3.5 text-slate-400" />
                  服务
                </dt>
                <dd>
                  <Dot ok={health.data.status === 'ok'} />
                </dd>
              </div>
              <div className="flex items-center justify-between px-5 py-3">
                <dt className="flex items-center gap-2 text-slate-600">
                  <Database className="h-3.5 w-3.5 text-slate-400" />
                  数据库
                </dt>
                <dd>
                  <Dot ok={health.data.database} />
                </dd>
              </div>
              <div className="flex items-center justify-between px-5 py-3">
                <dt className="flex items-center gap-2 text-slate-600">
                  <Cpu className="h-3.5 w-3.5 text-slate-400" />
                  Worker
                </dt>
                <dd>
                  <Dot ok={health.data.worker} />
                </dd>
              </div>
              <div className="flex items-center justify-between px-5 py-3">
                <dt className="flex items-center gap-2 text-slate-600">
                  <Timer className="h-3.5 w-3.5 text-slate-400" />
                  分析超时 / 堆
                </dt>
                <dd className="font-mono text-slate-700">
                  {health.data.analysisTimeoutSeconds}s · {health.data.workerHeap}
                </dd>
              </div>
              <div className="flex items-center justify-between px-5 py-3">
                <dt className="flex items-center gap-2 text-slate-600">
                  <Info className="h-3.5 w-3.5 text-slate-400" />
                  版本
                </dt>
                <dd className="font-mono text-slate-700">
                  v{health.data.version} · schema {health.data.schemaVersion}
                </dd>
              </div>
            </dl>
          ) : health.isLoading ? (
            <div className="flex items-center gap-2 px-5 py-8 text-xs text-slate-500">
              <Spinner /> 读取运行信息…
            </div>
          ) : null}
        </Card>

        <Card>
          <CardHeader title="存储位置" description="所有数据都落在本机，不上传任何源码或报告。" />
          <div className="space-y-4 px-5 py-4 text-xs">
            <div>
              <div className="flex items-center gap-1.5 text-[11px] text-slate-500">
                <FolderOpen className="h-3.5 w-3.5" /> 工作区根目录
              </div>
              <div className="mt-1 break-all rounded-lg bg-slate-50 px-3 py-2 font-mono text-[11px] text-slate-700">
                {health.data?.workspaceRoot ?? '—'}
              </div>
            </div>
            <div>
              <div className="flex items-center gap-1.5 text-[11px] text-slate-500">
                <HardDrive className="h-3.5 w-3.5" /> 数据目录
              </div>
              <div className="mt-1 break-all rounded-lg bg-slate-50 px-3 py-2 font-mono text-[11px] text-slate-700">
                {health.data?.dataDir ?? '—'}
              </div>
            </div>
            <p className="leading-5 text-slate-500">
              SQLite 使用 WAL 模式，适合本地单实例。分析结果保存在 analysis_run 表，报告中的源码片段按需从 Git 对象或工作区读取。
            </p>
          </div>
        </Card>

        <Card>
          <CardHeader
            title="本地优先与隐私"
            description="墨衡不包含遥测，也不会把分析内容发往外部。"
            action={<Lock className="h-4 w-4 text-slate-300" />}
          />
          <ul className="space-y-2.5 px-5 py-4 text-xs leading-5 text-slate-600">
            <li>分析对象是本地挂载的 Git 仓库，默认只读；服务不会向仓库写入文件，也不会执行提交。</li>
            <li>源码文本仅通过本机 API 返回给浏览器，最多 200 行，不写入数据库。</li>
            <li>日志只记录阶段、计数、路径与错误码，不打印源码片段。</li>
            <li>worker 在独立 JVM 中运行，单个分析超时或内存不足会被终止，不影响服务进程。</li>
          </ul>
        </Card>

        <Card>
          <CardHeader title="配置方式" description="环境变量在服务启动时读取。" />
          <div className="divide-y divide-slate-100 text-xs">
            {configRows.map(([key, description]) => (
              <div key={key} className="flex flex-col gap-0.5 px-5 py-2.5 sm:flex-row sm:items-center sm:justify-between">
                <span className="font-mono text-[11px] text-slate-800">{key}</span>
                <span className="text-[11px] text-slate-500">{description}</span>
              </div>
            ))}
          </div>
          <div className="border-t border-slate-100 px-5 py-3 text-[11px] leading-5 text-slate-400">
            通过 Docker 运行时，使用 <span className="font-mono">docker compose</span> 的 environment 或 .env 文件覆盖默认值。
          </div>
        </Card>
      </div>

      <Card className="mt-4">
        <CardHeader title="关于 墨衡 · 见微" description="V0.1 · 本地优先的变更影响分析" />
        <div className="grid gap-4 px-5 py-4 text-xs leading-5 text-slate-600 sm:grid-cols-3">
          <div>
            <div className="font-semibold text-slate-800">分析链路</div>
            <p className="mt-1">
              Git diff → 符号级变更 → Spoon 静态调用图 → 反向影响遍历 → Spring REST API 映射 → 风险与 Safe Mode。
            </p>
          </div>
          <div>
            <div className="font-semibold text-slate-800">结果形态</div>
            <p className="mt-1">
              同一套核心同时服务 CLI（JSON）与 Web 报告。所有结论都带路径、边类型、置信度与源码证据位置。
            </p>
          </div>
          <div>
            <div className="font-semibold text-slate-800">边界</div>
            <p className="mt-1">
              静态分析不覆盖反射、动态代理、消息队列与跨仓库调用；这些场景会在 warnings 与置信度中体现。
            </p>
          </div>
        </div>
      </Card>
    </div>
  )
}
