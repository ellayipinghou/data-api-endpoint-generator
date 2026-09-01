import { useEffect, useState } from "react"
import { Link, useNavigate, useParams } from "react-router-dom"
import { API_URL, deleteDataset, getDataset, getDatasetPreview } from "../api/datasets"
import type { DataRow, Dataset } from "../types/dataset"
import DeleteConfirmModal from "../components/DeleteConfirmModal"
import { useToast } from "../context/ToastContext"
import DatasetOverviewTab from "./DatasetOverviewTab"
import DatasetApiTab from "./DatasetApiTab"
import DatasetQueryTab from "./DatasetQueryTab"

type DetailTab = "overview" | "api" | "query"

function DatasetDetailPage() {
  // extract dataset id from url params
  const { showError } = useToast()
  const { id } = useParams()
  const navigate = useNavigate()
  const datasetId = id ?? ""
  
  // dataset state including preview and metadata
  const [dataset, setDataset] = useState<Dataset | null>(null)
  const [previewRows, setPreviewRows] = useState<DataRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [previewError, setPreviewError] = useState<string | null>(null)
  
  // ui state for tabs and delete confirmation
  const [activeTab, setActiveTab] = useState<DetailTab>("overview")
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  // load dataset and preview data
  useEffect(() => {
    getDataset(datasetId)
      .then(async (loadedDataset) => {
        try {
          const rows = await getDatasetPreview(datasetId)
          setPreviewRows(rows)
          setPreviewError(null)
        } catch (requestError) {
          // handle preview fetch failure separately from main dataset load
          const message = requestError instanceof Error
            ? requestError.message
            : "Failed to load the dataset preview."

          setPreviewRows([])
          setPreviewError("Failed to load the dataset preview: " + message)
          showError("Failed to load the dataset preview: " + message)
        }

        setDataset(loadedDataset)
        setError(null)
      })
      .catch((requestError: Error) => setError(requestError.message))
      .finally(() => setLoading(false))
  }, [datasetId, showError])

  // delete dataset and navigate back to list
  async function handleConfirmDelete() {
    setDeleting(true)
    setDeleteError(null)

    try {
      await deleteDataset(datasetId)
      navigate("/datasets")
    } catch (requestError) {
      const message = requestError instanceof Error
        ? requestError.message
        : "Failed to delete the dataset."

      setDeleteError("Failed to delete the dataset: " + message)
      showError("Failed to delete the dataset: " + message)
      setDeleting(false)
    }
  }

  // show loading state
  if (loading) {
    return <div className="empty-panel"><p>Loading dataset...</p></div>
  }

  // show error state if load failed or dataset missing
  if (error || !dataset) {
    return (
      <div className="empty-panel error-box">
        <h2>{error === "Dataset not found" ? "Dataset not found" : "Unable to load dataset"}</h2>
        <p>{error ?? "The requested dataset could not be loaded."}</p>
        <div className="cta-row"><Link to="/datasets" className="secondary-button">Back to datasets</Link></div>
      </div>
    )
  }

  return (
    <div className="page-shell">
      <div className="page-header dataset-detail-header">
        <div>
          <p className="eyebrow">Dataset</p>
          <h1>{dataset.name}</h1>
          <p className="subtitle">Explore the dataset and its query API.</p>
        </div>
        <div style={{ display: "flex", gap: 12 }}>
          <button className="secondary-button delete-dataset-button" onClick={() => setShowDeleteConfirm(true)}>Delete dataset</button>
          <Link to="/datasets" className="secondary-button">Back to datasets</Link>
        </div>
      </div>

      {/* tab navigation */}
      <div className="detail-tabs" role="tablist" aria-label="Dataset detail sections">
        <TabButton active={activeTab === "overview"} onClick={() => setActiveTab("overview")}>Overview</TabButton>
        <TabButton active={activeTab === "api"} onClick={() => setActiveTab("api")}>API</TabButton>
        <TabButton active={activeTab === "query"} onClick={() => setActiveTab("query")}>Query</TabButton>
      </div>

      {/* render active tab content */}
      {activeTab === "overview" ? (
        <DatasetOverviewTab
          dataset={dataset}
          previewRows={previewRows}
          previewError={previewError}
        />
      ) : activeTab === "query" ? (
        <DatasetQueryTab API_URL={API_URL} dataset={dataset} />
      ) : (
        <DatasetApiTab API_URL={API_URL} dataset={dataset} />
      )}

      {/* show delete confirmation modal when triggered */}
      {showDeleteConfirm && (
        <DeleteConfirmModal
          datasetName={dataset.name}
          deleting={deleting}
          error={deleteError}
          onCancel={() => {
            setShowDeleteConfirm(false)
            setDeleteError(null)
          }}
          onConfirm={handleConfirmDelete}
        />
      )}
    </div>
  )
}

// reusable tab button with active state styling
function TabButton({ active, children, onClick }: { active: boolean; children: string; onClick: () => void }) {
  return <button className={`detail-tab ${active ? "active" : ""}`} role="tab" aria-selected={active} onClick={onClick}>{children}</button>
}


export default DatasetDetailPage