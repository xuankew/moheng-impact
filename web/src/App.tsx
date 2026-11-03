import { Navigate, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { Dashboard } from './pages/Dashboard'
import { Projects } from './pages/Projects'
import { NewAnalysis } from './pages/NewAnalysis'
import { AnalysisOverview } from './pages/AnalysisOverview'
import { ImpactGraphPage } from './pages/ImpactGraph'
import { ApiDetailPage } from './pages/ApiDetail'
import { ChangesPage } from './pages/Changes'
import { HistoryPage } from './pages/History'
import { SettingsPage } from './pages/Settings'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Dashboard />} />
        <Route path="projects" element={<Projects />} />
        <Route path="projects/:projectId/analyses/new" element={<NewAnalysis />} />
        <Route path="analyses/:analysisId" element={<AnalysisOverview />} />
        <Route path="analyses/:analysisId/graph" element={<ImpactGraphPage />} />
        <Route path="analyses/:analysisId/apis/:apiId" element={<ApiDetailPage />} />
        <Route path="analyses/:analysisId/changes" element={<ChangesPage />} />
        <Route path="history" element={<HistoryPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}
