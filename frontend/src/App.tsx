import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom"
import AppShell from "./components/AppShell"
import DatasetDetailPage from "./pages/DatasetDetailPage"
import HomePage from "./pages/HomePage"
import { CreateModalProvider } from "./context/CreateModalContext"
import { ToastProvider } from "./context/ToastContext"

function App() {
  return (
    <ToastProvider>
      <CreateModalProvider>
        <BrowserRouter>
          <AppShell>
            <Routes>
              <Route path="/" element={<Navigate to="/datasets" replace />} />
              <Route path="/datasets" element={<HomePage />} />
              <Route path="/datasets/:id" element={<DatasetDetailPage />} />
            </Routes>
          </AppShell>
        </BrowserRouter>
      </CreateModalProvider>
    </ToastProvider>
  )
}

export default App