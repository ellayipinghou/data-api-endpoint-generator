import { useEffect, useState, useCallback } from "react"
import { useCreateModal } from "../context/CreateModalContext"
import { getDatasets } from "../api/datasets"
import DatasetList from "../components/DatasetList"
import type { Dataset } from "../types/dataset"

function HomePage() {
  const [datasets, setDatasets] = useState<Dataset[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const { open } = useCreateModal()

  const fetchDatasets = useCallback(() => {
    setLoading(true)
    setError(null)
    getDatasets()
      .then((data) => {
        setDatasets(data)
      })
      .catch(() => {
        setError("Failed to load datasets")
      })
      .finally(() => {
        setLoading(false)
      })
  }, [])

  useEffect(() => {
    fetchDatasets()
  }, [fetchDatasets])

  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <p className="eyebrow">Datasets</p>
          <h1>Your datasets</h1>
          <p className="subtitle">Manage datasets and explore their APIs.</p>
        </div>

        <button className="primary-button" onClick={() => open(fetchDatasets)}>
          + New Dataset
        </button>
      </div>

      {loading ? (
        <div className="empty-panel">
          <p>Loading datasets...</p>
        </div>
      ) : error ? (
        <div className="empty-panel error-box">
          <p>{error}</p>
        </div>
      ) : datasets.length === 0 ? (
        <div className="empty-panel">
          <h2>No datasets yet</h2>
          <p>Upload a CSV to create your first DataServ dataset.</p>

          <div className="cta-row">
            <button className="primary-button" onClick={() => open(fetchDatasets)}>
              + Create Dataset
            </button>
          </div>
        </div>
      ) : (
        <DatasetList datasets={datasets} />
      )}
    </div>
  )
}

export default HomePage