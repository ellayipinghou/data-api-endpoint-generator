import { useMemo } from "react"
import type { DataRow, Dataset } from "../types/dataset"
import DatasetTable from "../components/DatasetTable"

interface DatasetOverviewTabProps {
  dataset: Dataset
  previewRows: DataRow[]
  previewError: string | null
}

function DatasetOverviewTab({
  dataset,
  previewRows,
  previewError,
}: DatasetOverviewTabProps) {
  const createdAt = useMemo(
    () =>
      new Intl.DateTimeFormat("en-US", {
        dateStyle: "medium",
      }).format(new Date(dataset.createdAt)),
    [dataset.createdAt],
  )

  return (
    <div className="detail-tab-content">
      <section className="detail-card">
        <h2>Overview</h2>

        <dl className="dataset-metadata">
          <div>
            <dt>Dataset ID</dt>
            <dd className="monospace-text">{dataset.id}</dd>
          </div>

          <div>
            <dt>Rows</dt>
            <dd>{new Intl.NumberFormat("en-US").format(dataset.rowCount)}</dd>
          </div>

          <div>
            <dt>Columns</dt>
            <dd>{dataset.schema.columns.length}</dd>
          </div>

          <div>
            <dt>Created</dt>
            <dd>{createdAt}</dd>
          </div>
        </dl>
      </section>

      <section className="detail-card">
        <div className="section-heading">
          <div>
            <h2>Data Preview</h2>
            <p>First 10 rows</p>
          </div>
        </div>

        {previewError ? (
          <p className="inline-error">{previewError}</p>
        ) : (
          <DatasetTable
            columns={dataset.schema.columns}
            rows={previewRows}
          />
        )}
      </section>

      <section className="detail-card">
        <h2>Schema</h2>

        <div className="schema-list">
          {dataset.schema.columns.map((column) => (
            <div className="schema-list-row" key={column.name}>
              <span>{column.name}</span>
              <code>{column.type}</code>
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}

export default DatasetOverviewTab