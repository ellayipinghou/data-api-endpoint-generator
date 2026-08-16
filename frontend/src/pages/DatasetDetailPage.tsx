import { useEffect, useMemo, useState } from "react"
import { Link, useParams } from "react-router-dom"
import { API_URL, getDataset, getDatasetPreview, queryDataset } from "../api/datasets"
import type { DataColumn, DataRow, Dataset } from "../types/dataset"

type DetailTab = "overview" | "api" | "query"
type FilterOperator = "eq" | "ne" | "gt" | "gte" | "lt" | "lte" | "contains"

interface QueryFilter {
  id: number
  column: string
  operator: FilterOperator
  value: string
}

const operatorConfig: Record<string, { value: FilterOperator; label: string }> = {
  "=": { value: "eq", label: "equals" },
  "!=": { value: "ne", label: "does not equal" },
  ">": { value: "gt", label: "greater than" },
  ">=": { value: "gte", label: "greater than or equal" },
  "<": { value: "lt", label: "less than" },
  "<=": { value: "lte", label: "less than or equal" },
  CONTAINS: { value: "contains", label: "contains" },
}

function DatasetDetailPage() {
  const { id } = useParams()
  const datasetId = id ?? ""
  const [dataset, setDataset] = useState<Dataset | null>(null)
  const [previewRows, setPreviewRows] = useState<DataRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<DetailTab>("overview")

  useEffect(() => {
    getDataset(datasetId)
      .then(async (loadedDataset) => {
        const rows = await getDatasetPreview(datasetId).catch(() => [] as DataRow[])

        setDataset(loadedDataset)
        setPreviewRows(rows)
        setError(null)
      })
      .catch((requestError: Error) => setError(requestError.message))
      .finally(() => setLoading(false))
  }, [datasetId])

  if (loading) {
    return <div className="empty-panel"><p>Loading dataset...</p></div>
  }

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
        <Link to="/datasets" className="secondary-button">Back to datasets</Link>
      </div>

      <div className="detail-tabs" role="tablist" aria-label="Dataset detail sections">
        <TabButton active={activeTab === "overview"} onClick={() => setActiveTab("overview")}>Overview</TabButton>
        <TabButton active={activeTab === "api"} onClick={() => setActiveTab("api")}>API</TabButton>
        <TabButton active={activeTab === "query"} onClick={() => setActiveTab("query")}>Query</TabButton>
      </div>

      {activeTab === "overview" ? (
        <OverviewTab dataset={dataset} previewRows={previewRows} />
      ) : activeTab === "query" ? (
        <QueryTab dataset={dataset} />
      ) : (
        <ApiTab dataset={dataset} />
      )}
    </div>
  )
}

function TabButton({ active, children, onClick }: { active: boolean; children: string; onClick: () => void }) {
  return <button className={`detail-tab ${active ? "active" : ""}`} role="tab" aria-selected={active} onClick={onClick}>{children}</button>
}

function OverviewTab({ dataset, previewRows }: { dataset: Dataset; previewRows: DataRow[] }) {
  const createdAt = useMemo(() => new Intl.DateTimeFormat("en-US", { dateStyle: "medium" }).format(new Date(dataset.createdAt)), [dataset.createdAt])

  return (
    <div className="detail-tab-content">
      <section className="detail-card">
        <h2>Overview</h2>
        <dl className="dataset-metadata">
          <div><dt>Dataset ID</dt><dd className="monospace-text">{dataset.id}</dd></div>
          <div><dt>Rows</dt><dd>{new Intl.NumberFormat("en-US").format(dataset.rowCount)}</dd></div>
          <div><dt>Columns</dt><dd>{dataset.schema.columns.length}</dd></div>
          <div><dt>Created</dt><dd>{createdAt}</dd></div>
        </dl>
      </section>

      <section className="detail-card">
        <div className="section-heading"><div><h2>Data Preview</h2><p>First 10 rows</p></div></div>
        <DatasetTable columns={dataset.schema.columns} rows={previewRows} />
      </section>

      <section className="detail-card">
        <h2>Schema</h2>
        <div className="schema-list">
          {dataset.schema.columns.map((column) => <div className="schema-list-row" key={column.name}><span>{column.name}</span><code>{column.type}</code></div>)}
        </div>
      </section>
    </div>
  )
}

function DatasetTable({ columns, rows }: { columns: DataColumn[]; rows: DataRow[] }) {
  if (rows.length === 0) return <p className="muted-text">No preview rows are available for this dataset.</p>

  return (
    <div className="table-scroll"><table className="dataset-table"><thead><tr><th scope="col">#</th>{columns.map((column) => <th scope="col" key={column.name}>{column.name}</th>)}</tr></thead><tbody>{rows.map((row, rowIndex) => <tr key={rowIndex}><td>{rowIndex + 1}</td>{columns.map((column) => <td key={column.name}>{formatCell(row.values[column.name])}</td>)}</tr>)}</tbody></table></div>
  )
}

function formatCell(value: unknown) {
  if (value === null || value === undefined) return "—"
  if (typeof value === "object") return JSON.stringify(value)
  return String(value)
}

function QueryTab({ dataset }: { dataset: Dataset }) {
  const firstColumn = dataset.schema.columns[0]?.name ?? ""
  const [filters, setFilters] = useState<QueryFilter[]>([])
  const [sortColumn, setSortColumn] = useState("")
  const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc")
  const [limit, setLimit] = useState("50")
  const [offset, setOffset] = useState("0")
  const [results, setResults] = useState<DataRow[] | null>(null)
  const [running, setRunning] = useState(false)
  const [queryError, setQueryError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

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
      setQueryError(requestError instanceof Error ? requestError.message : "Failed to run query")
    } finally {
      setRunning(false)
    }
  }

  async function copyUrl() {
    try {
      await navigator.clipboard.writeText(generatedUrl)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1800)
    } catch {
      setCopied(false)
    }
  }

  return (
    <div className="detail-tab-content">
      <section className="detail-card">
        <h2>Query Dataset</h2>
        <p className="section-description">Build a GET request using the fields in this dataset.</p>

        <div className="query-section">
          <div className="section-heading"><div><h3>Filters</h3><p>Add filters to narrow returned rows.</p></div></div>
          {filters.length === 0 ? <p className="muted-text">No filters applied.</p> : <div className="filter-list">{filters.map((filter) => <FilterRow key={filter.id} filter={filter} columns={dataset.schema.columns} onChange={updateFilter} onRemove={() => setFilters((current) => current.filter((item) => item.id !== filter.id))} />)}</div>}
          <button className="secondary-button add-filter-button" onClick={addFilter}>+ Add Filter</button>
        </div>

        <div className="query-controls">
          <label><span>Sort column</span><select value={sortColumn} onChange={(event) => setSortColumn(event.target.value)}><option value="">No sort</option>{dataset.schema.columns.map((column) => <option key={column.name} value={column.name}>{column.name}</option>)}</select></label>
          <label><span>Direction</span><select value={sortDirection} onChange={(event) => setSortDirection(event.target.value as "asc" | "desc")} disabled={!sortColumn}><option value="asc">Ascending</option><option value="desc">Descending</option></select></label>
          <label><span>Limit</span><input type="number" min="1" value={limit} onChange={(event) => setLimit(event.target.value)} /></label>
          <label><span>Offset</span><input type="number" min="0" value={offset} onChange={(event) => setOffset(event.target.value)} /></label>
        </div>
      </section>

      <section className="detail-card">
        <p className="eyebrow">GET</p>
        <h2>Generated Request</h2>
        <div className="endpoint-block"><code>{generatedUrl}</code><button className="secondary-button copy-button" onClick={copyUrl}>{copied ? "Copied" : "Copy URL"}</button></div>
        {hasIncompleteFilter && <p className="inline-error">Enter a value for every filter before running the query.</p>}
        <div className="query-action"><button className="primary-button" onClick={runQuery} disabled={running || hasIncompleteFilter}>{running ? "Running query..." : "Run Query"}</button></div>
      </section>

      {(results || queryError) && <section className="detail-card">
        <div className="section-heading"><div><h2>Query Results</h2>{results && <p>Showing {results.length} {results.length === 1 ? "row" : "rows"}</p>}</div></div>
        {queryError ? <p className="inline-error">{queryError}</p> : results?.length ? <DatasetTable columns={dataset.schema.columns} rows={results} /> : <p className="muted-text">No rows matched this query.</p>}
      </section>}
    </div>
  )
}

function FilterRow({ filter, columns, onChange, onRemove }: { filter: QueryFilter; columns: DataColumn[]; onChange: (id: number, update: Partial<QueryFilter>) => void; onRemove: () => void }) {
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
            onChange(filter.id, { column: event.target.value, operator: operatorConfig[firstOperator].value })
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
        {column?.type === "BOOLEAN" ? <select value={filter.value} onChange={(event) => onChange(filter.id, { value: event.target.value })}><option value="">Select</option><option value="true">true</option><option value="false">false</option></select> : <input type={inputTypeForColumn(column)} value={filter.value} onChange={(event) => onChange(filter.id, { value: event.target.value })} placeholder="Value" />}
      </label>
      <button className="remove-filter-button" onClick={onRemove} aria-label={`Remove ${filter.column} filter`}>Remove</button>
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
  filters.forEach((filter) => { if (filter.column && filter.value.trim()) params.set(filter.operator === "eq" ? filter.column : `${filter.column}_${filter.operator}`, filter.value) })
  if (sortColumn) params.set("sort", `${sortColumn},${sortDirection}`)
  if (limit) params.set("limit", limit)
  if (offset) params.set("offset", offset)
  return params
}

function ApiTab({ dataset }: { dataset: Dataset }) {
  const endpointPath = `/datasets/${dataset.id}/query`
  const endpointUrl = `${API_URL}${endpointPath}`
  const [copied, setCopied] = useState(false)
  const comparisonColumns = dataset.schema.columns.filter((column) => column.operators.some((operator) => [">", ">=", "<", "<="].includes(operator)))
  const containsColumns = dataset.schema.columns.filter((column) => column.operators.includes("CONTAINS"))

  async function copyEndpoint() {
    try {
      await navigator.clipboard.writeText(endpointUrl)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1800)
    } catch {
      setCopied(false)
    }
  }

  return (
    <div className="detail-tab-content">
      <section className="detail-card">
        <p className="eyebrow">GET</p>
        <h2>API Endpoint</h2>
        <p className="section-description">Query this dataset with filters, sorting, and pagination.</p>
        <div className="endpoint-block"><code>{endpointUrl}</code><button className="secondary-button copy-button" onClick={copyEndpoint}>{copied ? "Copied" : "Copy"}</button></div>
      </section>

      <section className="detail-card">
        <h2>Query Parameters</h2>
        <p className="section-description">All filters are combined with AND. Parameter names use the dataset column names shown below.</p>
        <div className="api-parameter-list">
          <ApiParameter name="column=value or column_eq=value" description="Return rows where a column exactly matches a value." example={`?${dataset.schema.columns[0]?.name ?? "column"}=value`} />
          <ApiParameter name="column_ne=value" description="Return rows where a column does not match a value." />
          {comparisonColumns.length > 0 && <ApiParameter name="column_gt, column_gte, column_lt, column_lte" description="Compare numeric, date, and datetime columns." example={`?${comparisonColumns[0].name}_gt=value`} />}
          {containsColumns.length > 0 && <ApiParameter name="column_contains=value" description="Find string values containing text." example={`?${containsColumns[0].name}_contains=value`} />}
          <ApiParameter name="sort=column,asc|desc" description="Sort by one column. Ascending is used when the direction is omitted." example={`?sort=${dataset.schema.columns[0]?.name ?? "column"},desc`} />
          <ApiParameter name="limit" description="Maximum rows to return. Defaults to 100 when omitted or invalid." example="?limit=50" />
          <ApiParameter name="offset" description="Number of rows to skip before returning results." example="?offset=50" />
        </div>
      </section>
    </div>
  )
}

function ApiParameter({ name, description, example }: { name: string; description: string; example?: string }) {
  return <div className="api-parameter"><code>{name}</code><p>{description}</p>{example && <span className="parameter-example">Example: <code>{example}</code></span>}</div>
}

export default DatasetDetailPage