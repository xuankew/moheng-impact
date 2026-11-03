import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  Background,
  Controls,
  Handle,
  MiniMap,
  Position,
  ReactFlow,
  type Edge as FlowEdge,
  type Node as FlowNode,
  type NodeProps,
} from '@xyflow/react'
import dagre from '@dagrejs/dagre'
import { ArrowRight, FileCode2, Network, SlidersHorizontal } from 'lucide-react'
import { PageHeader } from '../components/PageHeader'
import { SourceDrawer, type SourceTarget } from '../components/SourceDrawer'
import { Button, Card, CardHeader, Drawer, InlineError, Spinner, VerBadge } from '../components/ui'
import { api } from '../lib/api'
import { cn } from '../lib/cn'
import { pct, shortOwner } from '../lib/format'
import type { GraphEdge, GraphNode, GraphView } from '../lib/types'

const NODE_WIDTH = 250
const NODE_HEIGHT = 64

const kindTone: Record<GraphNode['kind'], string> = {
  API: 'bg-indigo-500',
  METHOD: 'bg-sky-500',
  CLASS: 'bg-violet-500',
  FIELD: 'bg-amber-500',
  FILE: 'bg-slate-400',
}

const edgeTone: Record<string, string> = {
  CALLS: '#64748b',
  REFERENCES: '#94a3b8',
  USES_TYPE: '#0891b2',
  IMPLEMENTS: '#7c3aed',
  EXTENDS: '#7c3aed',
  OVERRIDES: '#a855f7',
  EXPOSES_API: '#4f46e5',
  DEFINES: '#cbd5e1',
}

type ImpactNodeData = { node: GraphNode } & Record<string, unknown>
type ImpactFlowNode = FlowNode<ImpactNodeData, 'impact'>

function ImpactNodeView({ data, selected }: NodeProps<ImpactFlowNode>) {
  const node = data.node
  const isApi = node.kind === 'API'
  return (
    <div
      className={cn(
        'w-[250px] rounded-lg border bg-white px-3 py-2 text-left shadow-sm transition',
        selected ? 'border-indigo-400 ring-2 ring-indigo-100' : 'border-slate-200',
        node.changed && !selected ? 'border-rose-300' : '',
      )}
      title={`${node.name}\n${node.file}:${node.line}`}
    >
      <Handle type="target" position={Position.Left} className="!h-2 !w-2 !border-2 !border-white !bg-slate-400" />
      <Handle type="source" position={Position.Right} className="!h-2 !w-2 !border-2 !border-white !bg-slate-400" />
      <div className="flex items-center gap-1.5">
        <span className={cn('h-2 w-2 shrink-0 rounded-full', kindTone[node.kind])} />
        <span className="text-[9px] font-semibold tracking-wide text-slate-400">{node.kind}</span>
        {node.changed ? (
          <span className="rounded bg-rose-50 px-1 py-0.5 text-[9px] font-semibold text-rose-600">变更</span>
        ) : null}
      </div>
      <div className={cn('mt-1 truncate font-medium', isApi ? 'font-mono text-[11px] text-indigo-800' : 'text-xs text-slate-800')}>
        {node.name}
      </div>
      <div className="mt-0.5 truncate font-mono text-[9px] text-slate-400">
        {node.file.split('/').slice(-2).join('/')}
        {node.line ? `:${node.line}` : ''}
      </div>
    </div>
  )
}

const nodeTypes = { impact: ImpactNodeView }

function toFlow(view: GraphView | undefined): { nodes: ImpactFlowNode[]; edges: FlowEdge[] } {
  if (!view || view.nodes.length === 0) return { nodes: [], edges: [] }
  const layout = new dagre.graphlib.Graph()
  layout.setGraph({ rankdir: 'LR', nodesep: 26, ranksep: 96, marginx: 24, marginy: 24 })
  layout.setDefaultEdgeLabel(() => ({}))
  for (const node of view.nodes) {
    layout.setNode(node.key, { width: NODE_WIDTH, height: NODE_HEIGHT })
  }
  const known = new Set(view.nodes.map((node) => node.key))
  const edges = view.edges.filter((edge) => known.has(edge.from) && known.has(edge.to))
  for (const edge of edges) {
    layout.setEdge(edge.from, edge.to)
  }
  dagre.layout(layout)

  const nodes: ImpactFlowNode[] = view.nodes.map((node) => {
    const point = layout.node(node.key) as { x: number; y: number } | undefined
    return {
      id: node.key,
      type: 'impact',
      position: {
        x: (point?.x ?? 0) - NODE_WIDTH / 2,
        y: (point?.y ?? 0) - NODE_HEIGHT / 2,
      },
      data: { node },
    }
  })

  const flowEdges: FlowEdge[] = edges.map((edge: GraphEdge) => ({
    id: `${edge.from}|${edge.type}|${edge.to}|${edge.detail}`,
    source: edge.from,
    target: edge.to,
    type: 'smoothstep',
    label: `${edge.type === 'EXPOSES_API' ? 'EXPOSES' : edge.type} ${pct(edge.confidence)}`,
    labelStyle: { fontSize: 9, fill: '#64748b' },
    labelBgStyle: { fill: '#ffffff', fillOpacity: 0.85 },
    labelBgPadding: [4, 2] as [number, number],
    style: { stroke: edgeTone[edge.type] ?? '#94a3b8', strokeWidth: edge.type === 'CALLS' ? 1.4 : 1.1 },
    animated: edge.type === 'EXPOSES_API',
  }))
  return { nodes, edges: flowEdges }
}

export function ImpactGraphPage() {
  const { analysisId = '' } = useParams()
  const [apiKey, setApiKey] = useState('')
  const [changedOnly, setChangedOnly] = useState(false)
  const [maxDepth, setMaxDepth] = useState('0')
  const [selected, setSelected] = useState<GraphNode | null>(null)
  const [source, setSource] = useState<SourceTarget | null>(null)

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
  const graph = useQuery({
    queryKey: ['analysis', analysisId, 'graph', apiKey, changedOnly, maxDepth],
    queryFn: () =>
      api.graph(analysisId, {
        apiKey: apiKey || undefined,
        changed: changedOnly,
        maxDepth: Number(maxDepth) || undefined,
      }),
    enabled: succeeded,
  })

  const flow = useMemo(() => toFlow(graph.data), [graph.data])
  const apiOptions = result.data?.apiImpacts ?? []

  if (status.error) return <InlineError error={status.error} />
  if (!status.isLoading && !succeeded) {
    return (
      <div>
        <PageHeader back={{ to: `/analyses/${analysisId}`, label: '返回报告' }} title="影响图" />
        <Card>
          <div className="flex items-center gap-2 px-5 py-10 text-sm text-slate-500">
            <Spinner />
            分析尚未完成（{status.data?.status ?? '未知状态'}），完成后即可查看影响图。
          </div>
        </Card>
      </div>
    )
  }

  return (
    <div>
      <PageHeader
        back={{ to: `/analyses/${analysisId}`, label: '返回报告' }}
        title="影响图"
        description="从变更符号出发，沿调用、实现与类型引用关系回溯到对外 API。图中只包含报告中的影响路径。"
        actions={
          <span className="hidden items-center gap-2 text-[11px] text-slate-500 sm:flex">
            <Network className="h-3.5 w-3.5" />
            {graph.data?.nodes.length ?? 0} 节点 · {graph.data?.edges.length ?? 0} 边
          </span>
        }
      />

      <Card>
        <div className="flex flex-wrap items-end gap-3 px-5 py-4">
          <div className="flex items-center gap-2 text-xs font-semibold text-slate-700">
            <SlidersHorizontal className="h-4 w-4 text-slate-400" />
            筛选
          </div>
          <label className="flex min-w-[240px] flex-1 flex-col gap-1 text-[11px] text-slate-500">
            API
            <select
              value={apiKey}
              onChange={(event) => setApiKey(event.target.value)}
              className="h-8 rounded-lg border border-slate-200 bg-white px-2 font-mono text-xs text-slate-800 outline-none focus:border-indigo-400"
            >
              <option value="">全部受影响 API</option>
              {apiOptions.map((impact) => (
                <option key={impact.api.key} value={impact.api.key}>
                  {impact.api.verb} {impact.api.path} · {impact.level}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1 text-[11px] text-slate-500">
            最大深度
            <select
              value={maxDepth}
              onChange={(event) => setMaxDepth(event.target.value)}
              className="h-8 rounded-lg border border-slate-200 bg-white px-2 text-xs text-slate-800 outline-none focus:border-indigo-400"
            >
              <option value="0">全部</option>
              <option value="1">1</option>
              <option value="2">2</option>
              <option value="3">3</option>
              <option value="4">4</option>
              <option value="5">5</option>
            </select>
          </label>
          <label className="flex h-8 items-center gap-2 text-xs text-slate-600">
            <input
              type="checkbox"
              checked={changedOnly}
              onChange={(event) => setChangedOnly(event.target.checked)}
              className="h-4 w-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
            />
            仅已变更符号起点
          </label>
          <div className="ml-auto flex items-center gap-3 text-[10px] text-slate-400">
            {Object.entries(kindTone).map(([kind, tone]) => (
              <span key={kind} className="inline-flex items-center gap-1">
                <span className={cn('h-1.5 w-1.5 rounded-full', tone)} />
                {kind}
              </span>
            ))}
          </div>
        </div>
      </Card>

      {graph.error ? <div className="mt-4"><InlineError error={graph.error} /></div> : null}
      {graph.data?.truncated ? (
        <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-2.5 text-xs text-amber-800">
          影响图超过 200 个节点，已截断展示；请通过 API 或深度筛选查看更小的子图。
        </div>
      ) : null}

      <Card className="mt-4 overflow-hidden">
        <div className="relative h-[620px] bg-slate-50/60">
          {graph.isLoading ? (
            <div className="flex h-full items-center justify-center gap-2 text-sm text-slate-500">
              <Spinner /> 构建影响图…
            </div>
          ) : flow.nodes.length === 0 ? (
            <div className="flex h-full flex-col items-center justify-center gap-2 text-sm text-slate-500">
              <Network className="h-6 w-6 text-slate-300" />
              当前筛选下没有影响路径
            </div>
          ) : (
            <ReactFlow
              nodes={flow.nodes}
              edges={flow.edges}
              nodeTypes={nodeTypes}
              fitView
              minZoom={0.2}
              maxZoom={1.6}
              onNodeClick={(_, node) => setSelected((node.data as ImpactNodeData).node)}
              proOptions={{ hideAttribution: true }}
            >
              <Background gap={18} size={1} color="#e2e8f0" />
              <Controls showInteractive={false} />
              <MiniMap
                pannable
                zoomable
                nodeColor={(node) => {
                  const kind = (node.data as ImpactNodeData).node.kind
                  return (
                    { API: '#6366f1', METHOD: '#0ea5e9', CLASS: '#8b5cf6', FIELD: '#f59e0b', FILE: '#94a3b8' }[
                      kind
                    ] ?? '#94a3b8'
                  )
                }}
                maskColor="rgba(248,250,252,0.75)"
              />
            </ReactFlow>
          )}
        </div>
      </Card>

      <Drawer
        open={Boolean(selected)}
        title={
          selected ? (
            <span className="flex items-center gap-2">
              <span className={cn('h-2 w-2 rounded-full', kindTone[selected.kind])} />
              {selected.name}
            </span>
          ) : null
        }
        description={selected ? `${selected.kind} · ${selected.file}:${selected.line}` : null}
        onClose={() => setSelected(null)}
      >
        {selected ? (
          <div className="space-y-4">
            <Card>
              <CardHeader title="节点信息" />
              <dl className="divide-y divide-slate-100 text-xs">
                <div className="flex gap-3 px-5 py-2.5">
                  <dt className="w-20 shrink-0 text-slate-500">标识</dt>
                  <dd className="break-all font-mono text-[11px] text-slate-700">{selected.key}</dd>
                </div>
                <div className="flex gap-3 px-5 py-2.5">
                  <dt className="w-20 shrink-0 text-slate-500">文件</dt>
                  <dd className="break-all font-mono text-[11px] text-slate-700">{selected.file}</dd>
                </div>
                <div className="flex gap-3 px-5 py-2.5">
                  <dt className="w-20 shrink-0 text-slate-500">变更</dt>
                  <dd className={selected.changed ? 'text-rose-600' : 'text-slate-500'}>
                    {selected.changed ? '本次变更加入的符号' : '本次未直接变更'}
                  </dd>
                </div>
                {Object.entries(selected.meta ?? {}).map(([key, value]) => (
                  <div key={key} className="flex gap-3 px-5 py-2.5">
                    <dt className="w-20 shrink-0 text-slate-500">{key}</dt>
                    <dd className="break-all font-mono text-[11px] text-slate-700">
                      {typeof value === 'object' ? JSON.stringify(value) : String(value)}
                    </dd>
                  </div>
                ))}
              </dl>
            </Card>

            <div className="flex flex-wrap gap-2">
              <Button
                variant="secondary"
                size="sm"
                onClick={() =>
                  setSource({
                    file: selected.file,
                    line: Math.max(1, selected.line),
                    label: selected.name,
                  })
                }
              >
                <FileCode2 className="h-3.5 w-3.5" />
                查看源码
              </Button>
              {selected.kind === 'API' && selected.meta?.apiId ? (
                <Link to={`/analyses/${analysisId}/apis/${String(selected.meta.apiId)}`}>
                  <Button variant="primary" size="sm">
                    打开 API 详情
                    <ArrowRight className="h-3.5 w-3.5" />
                  </Button>
                </Link>
              ) : null}
              {selected.kind === 'API' && selected.meta?.controller ? (
                <span className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-2.5 py-1.5 text-[11px] text-slate-600">
                  <VerBadge verb={String(selected.meta.verb ?? '')} />
                  {shortOwner(String(selected.meta.controller))}
                </span>
              ) : null}
            </div>
          </div>
        ) : null}
      </Drawer>

      <SourceDrawer analysisId={analysisId} target={source} onClose={() => setSource(null)} />
    </div>
  )
}
