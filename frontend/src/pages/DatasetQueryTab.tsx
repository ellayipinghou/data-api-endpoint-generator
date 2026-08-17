
import type { Dataset, DataRow, DataColumn } from "../types/dataset"
import { useState } from 'react';
import { useToast } from "../context/ToastContext"
import { useMemo } from "react"
import { queryDataset } from "../api/datasets"
import DatasetTable from "../components/DatasetTable"
import CopyButton from "../components/CopyButton"

type FilterOperator = "eq" | "ne" | "gt" | "gte" | "lt" | "lte" | "contains"

const operatorConfig: Record<string, { value: FilterOperator; label: string }> = {
  "=": { value: "eq", label: "equals" },
  "!=": { value: "ne", label: "does not equal" },
  ">": { value: "gt", label: "greater than" },
  ">=": { value: "gte", label: "greater than or equal" },
  "<": { value: "lt", label: "less than" },
  "<=": { value: "lte", label: "less than or equal" },
  CONTAINS: { value: "contains", label: "contains" },
}

interface QueryFilter {
  id: number
  column: string
  operator: FilterOperator
  value: string
}

interface DatasetOverviewTabProps {
  API_URL: string | null
  dataset: Dataset
}

function DatasetQueryTab({API_URL, dataset}: DatasetOverviewTabProps) {
  const { showError } = useToast()
  const firstColumn = dataset.schema.columns[0]?.name ?? ""
  const [filters, setFilters] = useState<QueryFilter[]>([])
  const [sortColumn, setSortColumn] = useState("")
  const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc")
  const [limit, setLimit] = useState("50")
  const [offset, setOffset] = useState("0")
  const [results, setResults] = useState<DataRow[] | null>(null)
  const [running, setRunning] = useState(false)
  const [queryError, setQueryError] = useState<string | null>(null)

  const hasIncompleteFilter = filters.some((filter) => !filter.column || !filter.value.trim())
  const params = useMemo(() => buildQueryParams(filters, sortColumn, sortDirection, limit, offset), [filters, sortColumn, sortDirection, limit, offset])
  const generatedUrl = `${API_URL}/datasets/${dataset.id}/query?${params.toString()}`

  function addFilter() {
    setFilters((current) => [...current, { id: Date.now(), column: firstColumn, operator: "eq", value: "" }])
  }

  function updateFilter(id: number, update: Partial<QueryFilter>) {
    setFilters((current) => current.map((filter) => filter.id === id ? { ...filter, ...update } : filter))
  }

  async function runQuery() {
    if (hasIncompleteFilter) return

    setRunning(true)
    setQueryError(null)

    try {
      setResults(await queryDataset(dataset.id, params))
    } catch (requestError) {
      const message = requestError instanceof Error ? requestError.message : "Failed to run query"
      setQueryError("Failed to run the query: " + message)
      showError("Failed to run the query: " + message)
    } finally {
      setRunning(false)
    }
  }


  return (
    <div className="detail-tab-content">
      <section className="detail-card">
        <h2>Query Dataset</h2>
        <p className="section-description">Build a GET request using the fields in this dataset.</p>

        <div className="query-section">
          <div className="section-heading">
            <div>
              <h3>Filters</h3>
              <p>Add filters to narrow returned rows.</p>
            </div>
          </div>

          {filters.length === 0
            ? <p className="muted-text">No filters applied.</p>
            : (
              <div className="filter-list">
                {filters.map((filter) => (
                  <FilterRow
                    key={filter.id}
                    filter={filter}
                    columns={dataset.schema.columns}
                    onChange={updateFilter}
                    onRemove={() => setFilters((current) => current.filter((item) => item.id !== filter.id))}
                  />
                ))}
              </div>
            )}

          <button className="secondary-button add-filter-button" onClick={addFilter}>+ Add Filter</button>
        </div>

        <div className="query-controls">
          <label>
            <span>Sort column</span>
            <select value={sortColumn} onChange={(event) => setSortColumn(event.target.value)}>
              <option value="">No sort</option>
              {dataset.schema.columns.map((column) => <option key={column.name} value={column.name}>{column.name}</option>)}
            </select>
          </label>

          <label>
            <span>Direction</span>
            <select value={sortDirection} onChange={(event) => setSortDirection(event.target.value as "asc" | "desc")} disabled={!sortColumn}>
              <option value="asc">Ascending</option>
              <option value="desc">Descending</option>
            </select>
          </label>

          <label>
            <span>Limit</span>
            <input type="number" min="1" value={limit} onChange={(event) => setLimit(event.target.value)} />
          </label>

          <label>
            <span>Offset</span>
            <input type="number" min="0" value={offset} onChange={(event) => setOffset(event.target.value)} />
          </label>
        </div>
      </section>

      <section className="detail-card">
        <p className="eyebrow">GET</p>
        <h2>Generated Request</h2>

        <div className="endpoint-block">
            <code>{generatedUrl}</code>
            <CopyButton text={generatedUrl} label="Copy URL" />
        </div>

        {hasIncompleteFilter && <p className="inline-error">Enter a value for every filter before running the query.</p>}

        <div className="query-action">
          <button className="primary-button" onClick={runQuery} disabled={running || hasIncompleteFilter}>
            {running ? "Running query..." : "Run Query"}
          </button>
        </div>
      </section>

      {(results || queryError) && (
        <section className="detail-card">
          <div className="section-heading">
            <div>
              <h2>Query Results</h2>
              {results && <p>Showing {results.length} {results.length === 1 ? "row" : "rows"}</p>}
            </div>
          </div>

          {queryError
            ? <p className="inline-error">{queryError}</p>
            : results?.length
              ? <DatasetTable columns={dataset.schema.columns} rows={results} />
              : <p className="muted-text">No rows matched this query.</p>}
        </section>
      )}
    </div>
  )
}

function FilterRow({
  filter,
  columns,
  onChange,
  onRemove,
}: {
  filter: QueryFilter
  columns: DataColumn[]
  onChange: (id: number, update: Partial<QueryFilter>) => void
  onRemove: () => void
}) {
  const column = columns.find((item) => item.name === filter.column)
  const operators = (column?.operators ?? [])
    .map((operator) => operatorConfig[operator])
    .filter((operator): operator is { value: FilterOperator; label: string } => operator !== undefined)

  return (
    <div className="filter-row">
      <label>
        <span>Column</span>
        <select
          value={filter.column}
          onChange={(event) => {
            const newColumn = columns.find((item) => item.name === event.target.value)
            const firstOperator = newColumn?.operators[0]

            if (!firstOperator || !operatorConfig[firstOperator]) return

            onChange(filter.id, {
              column: event.target.value,
              operator: operatorConfig[firstOperator].value,
            })
          }}
        >
          {columns.map((item) => <option key={item.name} value={item.name}>{item.name}</option>)}
        </select>
      </label>

      <label>
        <span>Operator</span>
        <select value={filter.operator} onChange={(event) => onChange(filter.id, { operator: event.target.value as FilterOperator })}>
          {operators.map((operator) => <option key={operator.value} value={operator.value}>{operator.label}</option>)}
        </select>
      </label>

      <label>
        <span>Value</span>
        {column?.type === "BOOLEAN"
          ? (
            <select value={filter.value} onChange={(event) => onChange(filter.id, { value: event.target.value })}>
              <option value="">Select</option>
              <option value="true">true</option>
              <option value="false">false</option>
            </select>
          )
          : (
            <input
              type={inputTypeForColumn(column)}
              value={filter.value}
              onChange={(event) => onChange(filter.id, { value: event.target.value })}
              placeholder="Value"
            />
          )}
      </label>

      <button className="remove-filter-button" onClick={onRemove} aria-label={`Remove ${filter.column} filter`}>
        Remove
      </button>
    </div>
  )
}

function inputTypeForColumn(column: DataColumn | undefined) {
  if (!column) return "text"
  if (["INTEGER", "LONG", "DOUBLE"].includes(column.type)) return "number"
  if (column.type === "DATE") return "date"
  if (column.type === "DATETIME") return "datetime-local"
  return "text"
}

function buildQueryParams(filters: QueryFilter[], sortColumn: string, sortDirection: "asc" | "desc", limit: string, offset: string) {
  const params = new URLSearchParams()

  filters.forEach((filter) => {
    if (filter.column && filter.value.trim()) {
      params.set(
        filter.operator === "eq" ? filter.column : `${filter.column}_${filter.operator}`,
        filter.value,
      )
    }
  })

  if (sortColumn) params.set("sort", `${sortColumn},${sortDirection}`)
  if (limit) params.set("limit", limit)
  if (offset) params.set("offset", offset)

  return params
}

export default DatasetQueryTab;