
import type { Dataset, DataRow, DataColumn } from "../types/dataset"
import { useState } from 'react';
import { useToast } from "../context/ToastContext"
import { useMemo } from "react"
import { queryDataset } from "../api/datasets"
import DatasetTable from "../components/DatasetTable"
import CopyButton from "../components/CopyButton"

type FilterOperator = "eq" | "ne" | "gt" | "gte" | "lt" | "lte" | "contains"

// mapping of symbols to filter operators and display labels
const operatorConfig: Record<string, { value: FilterOperator; label: string }> = {
  "=": { value: "eq", label: "equals" },
  "!=": { value: "ne", label: "does not equal" },
  ">": { value: "gt", label: "greater than" },
  ">=": { value: "gte", label: "greater than or equal" },
  "<": { value: "lt", label: "less than" },
  "<=": { value: "lte", label: "less than or equal" },
  CONTAINS: { value: "contains", label: "contains" },
}

// single filter with column, operator, and value
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
  
  // query builder state
  const [filters, setFilters] = useState<QueryFilter[]>([])
  const [sortColumn, setSortColumn] = useState("")
  const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc")
  const [limit, setLimit] = useState("50")
  const [offset, setOffset] = useState("0")
  
  // query execution and results
  const [results, setResults] = useState<DataRow[] | null>(null)
  const [running, setRunning] = useState(false)
  const [queryError, setQueryError] = useState<string | null>(null)

  // validation and url generation
  const hasIncompleteFilter = filters.some((filter) => !filter.column || !filter.value.trim())
  const params = useMemo(() => buildQueryParams(filters, sortColumn, sortDirection, limit, offset), [filters, sortColumn, sortDirection, limit, offset])
  const generatedUrl = `${API_URL}/datasets/${dataset.id}/query?${params.toString()}`

  // add new empty filter row
  function addFilter() {
    setFilters((current) => [...current, { id: Date.now(), column: firstColumn, operator: "eq", value: "" }])
  }

  // update specific filter by id
  function updateFilter(id: number, update: Partial<QueryFilter>) {
    setFilters((current) => current.map((filter) => filter.id === id ? { ...filter, ...update } : filter))
  }

  // execute query with current filters and options
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

        {/* filter builder section */}
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

        {/* sorting and pagination options */}
        <div className="query-controls">
          <label>
            <span>Sort column</span>
            {/* choose which column to sort by */}
            <select value={sortColumn} onChange={(event) => setSortColumn(event.target.value)}>
              <option value="">No sort</option>
              {dataset.schema.columns.map((column) => <option key={column.name} value={column.name}>{column.name}</option>)}
            </select>
          </label>

          <label>
            <span>Direction</span>
            {/* sort direction only available when sort column selected */}
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

      {/* generated api request */}
      <section className="detail-card">
        <p className="eyebrow">GET</p>
        <h2>Generated Request</h2>

        <div className="endpoint-block">
            <code>{generatedUrl}</code>
            <CopyButton text={generatedUrl} label="Copy URL" />
        </div>

        {hasIncompleteFilter && <p className="inline-error">Enter a value for every filter before running the query.</p>}

        <div className="query-action">
          {/* run button disabled if query incomplete or already running */}
          <button className="primary-button" onClick={runQuery} disabled={running || hasIncompleteFilter}>
            {running ? "Running query..." : "Run Query"}
          </button>
        </div>
      </section>

      {/* show results or error only after query execution */}
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

// individual filter row with column, operator, value selectors
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
  // get operators supported by current column
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

            // reset operator when column changes to first valid operator
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
        {/* show operators available for selected column */}
        <select value={filter.operator} onChange={(event) => onChange(filter.id, { operator: event.target.value as FilterOperator })}>
          {operators.map((operator) => <option key={operator.value} value={operator.value}>{operator.label}</option>)}
        </select>
      </label>

      <label>
        <span>Value</span>
        {/* use dropdown for booleans, otherwise use type-specific input */}
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

// determine html input type based on column data type
function inputTypeForColumn(column: DataColumn | undefined) {
  if (!column) return "text"
  if (["INTEGER", "LONG", "DOUBLE"].includes(column.type)) return "number"
  if (column.type === "DATE") return "date"
  if (column.type === "DATETIME") return "datetime-local"
  return "text"
}

// build url search parameters from query filters and options
function buildQueryParams(filters: QueryFilter[], sortColumn: string, sortDirection: "asc" | "desc", limit: string, offset: string) {
  const params = new URLSearchParams()

  // add filter parameters
  filters.forEach((filter) => {
    if (filter.column && filter.value.trim()) {
      // use plain column name for eq, add operator suffix for others
      params.set(
        filter.operator === "eq" ? filter.column : `${filter.column}_${filter.operator}`,
        filter.value,
      )
    }
  })

  // add sort, limit, and offset
  if (sortColumn) params.set("sort", `${sortColumn},${sortDirection}`)
  if (limit) params.set("limit", limit)
  if (offset) params.set("offset", offset)

  return params
}

export default DatasetQueryTab;