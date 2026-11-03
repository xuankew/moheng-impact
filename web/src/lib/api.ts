import type {
  AnalysisResult,
  ApiInfo,
  ApiPage,
  GraphView,
  Health,
  ImpactPath,
  Project,
  RiskLevel,
  RiskReason,
  RiskReport,
  RunStatusInfo,
  WorkspaceDirs,
} from './types'

export class ApiError extends Error {
  code: string
  status: number
  details: Record<string, unknown>

  constructor(code: string, message: string, status: number, details: Record<string, unknown> = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.details = details
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...(init?.headers ?? {}),
    },
  })
  const text = await response.text()
  let body: any = null
  if (text) {
    try {
      body = JSON.parse(text)
    } catch {
      body = null
    }
  }
  if (!response.ok) {
    const error = body?.error ?? {}
    throw new ApiError(
      error.code ?? `HTTP_${response.status}`,
      error.message ?? `请求失败（${response.status}）`,
      response.status,
      error.details ?? {},
    )
  }
  return body?.data as T
}

export const api = {
  health: () => request<Health>('/api/health'),

  projects: () => request<Project[]>('/api/projects'),
  project: (id: string) => request<Project>(`/api/projects/${id}`),
  createProject: (body: Partial<Project> & { repoPath: string }) =>
    request<Project>('/api/projects', { method: 'POST', body: JSON.stringify(body) }),
  updateProject: (id: string, body: Partial<Project>) =>
    request<Project>(`/api/projects/${id}`, { method: 'PATCH', body: JSON.stringify(body) }),
  deleteProject: (id: string) =>
    request<{ deleted: string }>(`/api/projects/${id}`, { method: 'DELETE' }),

  workspaceDirs: (path: string) =>
    request<WorkspaceDirs>(`/api/workspace/dirs?path=${encodeURIComponent(path)}`),

  analyses: (projectId?: string, limit = 30) =>
    request<RunStatusInfo[]>(
      `/api/analyses?limit=${limit}${projectId ? `&projectId=${projectId}` : ''}`,
    ),
  analysisStatus: (id: string) => request<RunStatusInfo>(`/api/analyses/${id}/status`),
  analysisResult: (id: string) => request<AnalysisResult>(`/api/analyses/${id}`),
  createAnalysis: (projectId: string, body: Record<string, unknown>) =>
    request<RunStatusInfo>(`/api/projects/${projectId}/analyses`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  cancelAnalysis: (id: string) =>
    request<{ cancelled: boolean; status: RunStatusInfo }>(`/api/analyses/${id}/cancel`, {
      method: 'POST',
    }),
  deleteAnalysis: (id: string) =>
    request<{ deleted: string }>(`/api/analyses/${id}`, { method: 'DELETE' }),

  apiPage: (id: string, params: { risk?: string; q?: string; page?: number; size?: number }) => {
    const search = new URLSearchParams()
    if (params.risk) search.set('risk', params.risk)
    if (params.q) search.set('q', params.q)
    search.set('page', String(params.page ?? 1))
    search.set('size', String(params.size ?? 20))
    return request<ApiPage>(`/api/analyses/${id}/apis?${search.toString()}`)
  },
  apiDetail: (id: string, apiId: string) =>
    request<{
      api: ApiInfo
      level: RiskLevel
      score: number
      confidence: number
      depth: number
      reasons: RiskReason[]
      paths: ImpactPath[]
      risk: RiskReport
    }>(`/api/analyses/${id}/apis/${apiId}`),
  changes: (id: string) =>
    request<{
      files: AnalysisResult['changes']
      symbols: AnalysisResult['changedSymbols']
      contractChanges: AnalysisResult['apiContractChanges']
      warnings: AnalysisResult['warnings']
    }>(`/api/analyses/${id}/changes`),
  graph: (id: string, params: { apiKey?: string; changed?: boolean; maxDepth?: number }) => {
    const search = new URLSearchParams()
    if (params.apiKey) search.set('apiKey', params.apiKey)
    if (params.changed) search.set('changed', 'true')
    if (params.maxDepth) search.set('maxDepth', String(params.maxDepth))
    return request<GraphView>(`/api/analyses/${id}/graph?${search.toString()}`)
  },
  source: async (id: string, file: string, start?: number, end?: number) => {
    const search = new URLSearchParams({ file })
    if (start) search.set('start', String(start))
    if (end) search.set('end', String(end))
    const response = await fetch(`/api/analyses/${id}/source?${search.toString()}`)
    if (!response.ok) {
      const text = await response.text()
      let error: any = null
      try {
        error = JSON.parse(text)?.error
      } catch {
        error = null
      }
      throw new ApiError(
        error?.code ?? 'SOURCE_READ_FAILED',
        error?.message ?? `无法读取源码（${response.status}）`,
        response.status,
      )
    }
    return response.text()
  },
}

export async function fetchSource(
  id: string,
  file: string,
  line: number,
  context = 14,
): Promise<string> {
  const start = Math.max(1, line - context)
  return api.source(id, file, start, line + context)
}
