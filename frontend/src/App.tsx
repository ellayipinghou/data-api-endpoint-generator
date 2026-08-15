import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom"
import AppShell from "./components/AppShell"
import DatasetDetailPage from "./pages/DatasetDetailPage"
import HomePage from "./pages/HomePage"
import NewDatasetPage from "./pages/NewDatasetPage"
import { CreateModalProvider } from "./context/CreateModalContext"

function App() {
  return (
    <CreateModalProvider>
      <BrowserRouter>
        <AppShell>
          <Routes>
            <Route path="/" element={<Navigate to="/datasets" replace />} />
            <Route path="/datasets" element={<HomePage />} />
            <Route path="/datasets/new" element={<NewDatasetPage />} />
            <Route path="/datasets/:id" element={<DatasetDetailPage />} />
          </Routes>
        </AppShell>
      </BrowserRouter>
    </CreateModalProvider>
  )
}

export default App