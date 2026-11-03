export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type RunStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'

export interface Project {
  id: string
  name: string
  repoPath: string
  apiPrefix: string
  includePackages: string[]
  excludePackages: string[]
  sourceRoots: string[]
  defaultBase: string | null
  createdAt: string
  updatedAt: string
}

export interface Summary {
  changedFiles: number
  changedJavaFiles: number
  changedSymbols: number
  impactedMethods: number
  impactedApis: number
  riskLevel: RiskLevel
  riskScore: number
  safeMode: boolean
}

export interface RunStatusInfo {
  id: string
  projectId: string
  base: string
  head: string
  headMode: string
  status: RunStatus
  phase: string | null
  progress: number
  riskLevel: RiskLevel | null
  riskScore: number | null
  safeMode: boolean
  summary: Summary | null
  startedAt: string
  finishedAt: string | null
  error?: { code: string; message: string }
}

export interface FileChange {
  path: string
  oldPath: string | null
  status: 'ADDED' | 'MODIFIED' | 'DELETED' | 'RENAMED'
  ranges: { start: number; end: number }[]
  binary: boolean
  javaFile: boolean
}

export interface SymbolChange {
  key: { kind: 'CLASS' | 'METHOD' | 'FIELD'; owner: string; name: string; descriptor: string }
  changeType: string
  file: string
  line: number
  previousFile: string | null
  publicDto: boolean
  controllerHandler: boolean
  detail: string
}

export interface ApiInfo {
  id: string
  key: string
  verb: string
  path: string
  controller: string
  handlerKey: string
  handlerOwner: string
  handlerName: string
  file: string
  line: number
  classPaths: string[]
  methodPaths: string[]
  conditions: Record<string, string[]>
  annotationResolution: string
  inherited: boolean
}

export interface RiskReason {
  code: string
  weight: number
  message: string
  evidence: Record<string, unknown>
}

export interface Evidence {
  source: string
  file: string
  line: number
  receiverType: string
  annotationResolution: string
  message: string
}

export interface GraphEdge {
  from: string
  to: string
  type: string
  confidence: number
  detail: string
  evidence: Evidence
}

export interface ImpactPath {
  changedKey: string
  apiId: string
  depth: number
  confidence: number
  edges: GraphEdge[]
}

export interface ApiImpact {
  api: ApiInfo
  level: RiskLevel
  score: number
  confidence: number
  depth: number
  reasons: RiskReason[]
  paths: ImpactPath[]
}

export interface SafeMode {
  enabled: boolean
  triggers: RiskReason[]
  recommendation: string
}

export interface RiskReport {
  level: RiskLevel
  score: number
  reasons: RiskReason[]
  safeMode: SafeMode
}

export interface GraphNode {
  key: string
  kind: 'FILE' | 'CLASS' | 'METHOD' | 'FIELD' | 'API'
  name: string
  file: string
  line: number
  changed: boolean
  meta: Record<string, unknown>
}

export interface ImpactGraph {
  nodes: Record<string, GraphNode>
  edges: GraphEdge[]
}

export interface ImpactedMethod {
  key: string
  name: string
  file: string
  line: number
  depth: number
  confidence: number
  impactedApis: string[]
}

export interface ContractChange {
  type: 'ADDED' | 'REMOVED' | 'CHANGED'
  apiKey: string
  before: ApiInfo | null
  after: ApiInfo | null
  detail: string
}

export interface AnalysisResult {
  schemaVersion: string
  analysis: {
    repo: string
    base: string
    head: string
    headMode: string
    startedAt: string
    finishedAt: string
  }
  summary: Summary
  changes: FileChange[]
  changedSymbols: SymbolChange[]
  apiContractChanges: ContractChange[]
  apis: ApiInfo[]
  apiImpacts: ApiImpact[]
  impactedMethods: ImpactedMethod[]
  graph: ImpactGraph
  risk: RiskReport
  warnings: { code: string; message: string; details: Record<string, unknown> }[]
  stats: Record<string, unknown>
}

export interface ApiImpactItem {
  id: string
  key: string
  verb: string
  path: string
  controller: string
  handler: string
  level: RiskLevel
  score: number
  confidence: number
  depth: number
  reasons: RiskReason[]
}

export interface ApiPage {
  items: ApiImpactItem[]
  page: number
  pageSize: number
  total: number
}

export interface WorkspaceDirs {
  root: string
  path: string
  parent: string | null
  directories: { name: string; path: string; git: boolean }[]
}

export interface Health {
  status: string
  version: string
  schemaVersion: string
  database: boolean
  worker: boolean
  workspaceRoot: string
  dataDir: string
  workerHeap: string
  analysisTimeoutSeconds: number
}

export interface GraphView {
  nodes: GraphNode[]
  edges: GraphEdge[]
  truncated: boolean
  maxDepth: number
}
