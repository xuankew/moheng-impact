import { useQuery } from '@tanstack/react-query'
import { FileCode2 } from 'lucide-react'
import { api } from '../lib/api'
import { cn } from '../lib/cn'
import { Drawer, InlineError, Spinner } from './ui'

export interface SourceTarget {
  file: string
  line: number
  context?: number
  label?: string
}

export function SourceDrawer({
  analysisId,
  target,
  onClose,
}: {
  analysisId: string
  target: SourceTarget | null
  onClose: () => void
}) {
  const context = target?.context ?? 14
  const start = target ? Math.max(1, target.line - context) : 1
  const end = target ? target.line + context : 200

  const source = useQuery({
    queryKey: ['source', analysisId, target?.file, start, end],
    queryFn: () => api.source(analysisId, target!.file, start, end),
    enabled: Boolean(target?.file),
    staleTime: 60_000,
    retry: false,
  })

  const lines = (source.data ?? '').replace(/\n$/, '').split('\n')

  return (
    <Drawer
      open={Boolean(target)}
      title={
        <span className="flex items-center gap-1.5">
          <FileCode2 className="h-4 w-4 text-slate-400" />
          {target?.label ?? '源码片段'}
        </span>
      }
      description={
        target ? (
          <span className="font-mono text-[11px]">
            {target.file}:{target.line}
          </span>
        ) : null
      }
      onClose={onClose}
      width="max-w-3xl"
    >
      {source.isLoading ? (
        <div className="flex items-center gap-2 py-6 text-xs text-slate-500">
          <Spinner /> 读取源码…
        </div>
      ) : null}
      {source.error ? <InlineError error={source.error} /> : null}
      {source.data ? (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-slate-950">
          <table className="w-full border-collapse font-mono text-[11px] leading-5">
            <tbody>
              {lines.map((text, index) => {
                const number = start + index
                const active = number === target?.line
                return (
                  <tr key={number} className={active ? 'bg-indigo-500/20' : undefined}>
                    <td
                      className={cn(
                        'w-12 select-none border-r border-slate-800 px-2 text-right align-top',
                        active ? 'text-indigo-300' : 'text-slate-600',
                      )}
                    >
                      {number}
                    </td>
                    <td className={cn('whitespace-pre px-3 align-top', active ? 'text-indigo-100' : 'text-slate-300')}>
                      {text || ' '}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      ) : null}
      <div className="mt-3 text-[11px] leading-5 text-slate-400">
        源码在本地读取，仅返回目标行附近的片段（最多 200 行）。
      </div>
    </Drawer>
  )
}
