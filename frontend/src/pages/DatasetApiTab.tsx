import type { Dataset } from "../types/dataset"
import CopyButton from "../components/CopyButton"

interface DatasetOverviewTabProps {
  API_URL: string | null
  dataset: Dataset
}

function DatasetApiTab({ API_URL, dataset }: DatasetOverviewTabProps) {
  const endpointPath = `/datasets/${dataset.id}/query`
  const endpointUrl = `${API_URL}${endpointPath}`

  const comparisonColumns = dataset.schema.columns.filter((column) =>
    column.operators.some((operator) =>
      [">", ">=", "<", "<="].includes(operator),
    ),
  )

  const containsColumns = dataset.schema.columns.filter((column) =>
    column.operators.includes("CONTAINS"),
  )

  return (
    <div className="detail-tab-content">
      <section className="detail-card">
        <p className="eyebrow">GET</p>
        <h2>API Endpoint</h2>
        <p className="section-description">
          Query this dataset with filters, sorting, and pagination.
        </p>

        <div className="endpoint-block">
          <code>{endpointUrl}</code>
          <CopyButton text={endpointUrl} />
        </div>
      </section>

      <section className="detail-card">
        <h2>Query Parameters</h2>
        <p className="section-description">
          All filters are combined with AND. Parameter names use the dataset
          column names shown below.
        </p>

        <div className="api-parameter-list">
          <ApiParameter
            name="column=value or column_eq=value"
            description="Return rows where a column exactly matches a value."
            supportedTypes="STRING, INTEGER, LONG, DOUBLE, BOOLEAN, DATE, DATETIME"
            example={`?${dataset.schema.columns[0]?.name ?? "column"}=value`}
          />

          <ApiParameter
            name="column_ne=value"
            description="Return rows where a column does not match a value."
            supportedTypes="STRING, INTEGER, LONG, DOUBLE, BOOLEAN, DATE, DATETIME"
            example={`?${dataset.schema.columns[0]?.name ?? "column"}_ne=value`}
          />

          {comparisonColumns.length > 0 && (
            <ApiParameter
              name="column_gt=value, column_gte=value, column_lt=value, column_lte=value"
              description="Compare a column against a value."
              supportedTypes="INTEGER, LONG, DOUBLE, DATE, DATETIME"
              example={`?${comparisonColumns[0].name}_gt=value`}
            />
          )}

          {containsColumns.length > 0 && (
            <ApiParameter
              name="column_contains=value"
              description="Find string values containing text."
              supportedTypes="STRING"
              example={`?${containsColumns[0].name}_contains=value`}
            />
          )}

          <ApiParameter
            name="sort=column,asc|desc"
            description="Sort by one column. Ascending is used when the direction is omitted."
            supportedTypes="STRING, INTEGER, LONG, DOUBLE, BOOLEAN, DATE, DATETIME"
            example={`?sort=${dataset.schema.columns[0]?.name ?? "column"},desc`}
          />

          <ApiParameter
            name="limit"
            description="Maximum number of rows to return. Defaults to 100 when omitted or invalid."
            example="?limit=50"
          />

          <ApiParameter
            name="offset"
            description="Number of rows to skip before returning results."
            example="?offset=50"
          />
        </div>
      </section>
    </div>
  )
}

function ApiParameter({
  name,
  description,
  example,
  supportedTypes,
}: {
  name: string
  description: string
  example?: string
  supportedTypes?: string
}) {
  return (
    <div className="api-parameter">
      <code>{name}</code>
      <p>{description}</p>

      {supportedTypes && (
        <p>Supported types: {supportedTypes}</p>
      )}

      {example && (
        <span className="parameter-example">
          Example: <code>{example}</code>
        </span>
      )}
    </div>
  )
}

export default DatasetApiTab