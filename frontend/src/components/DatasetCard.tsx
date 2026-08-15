import { Link } from "react-router-dom"
import type { Dataset } from "../types/dataset"

interface DatasetCardProps {
  dataset: Dataset
}

function DatasetCard({ dataset }: DatasetCardProps) {
  const columns = dataset.schema.columns
  const previewColumns = columns.slice(0, 4)
  const remainingColumns = Math.max(columns.length - previewColumns.length, 0)

  return (
    <article className="dataset-card">
      <div className="dataset-card-header">
        <div>
          <h3>{dataset.name}</h3>
          <span className="status-pill">● Ready</span>
        </div>
      </div>

      <div className="dataset-card-metrics">
        <span>{columns.length} columns</span>
      </div>

      <div className="schema-preview">
        {previewColumns.map((column) => (
          <div key={`${dataset.id}-${column.name}`} className="schema-preview-row">
            <span>{column.name}</span>
            <span>{column.type}</span>
          </div>
        ))}

        {remainingColumns > 0 && (
          <div className="schema-preview-row muted">
            <span>+ {remainingColumns} more columns</span>
          </div>
        )}
      </div>

      <Link to={`/datasets/${dataset.id}`} className="dataset-link">
        View Dataset →
      </Link>
    </article>
  )
}

export default DatasetCard
