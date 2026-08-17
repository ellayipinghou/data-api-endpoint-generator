import type { DataColumn, DataRow } from "../types/dataset"

function DatasetTable({
  columns,
  rows,
}: {
  columns: DataColumn[]
  rows: DataRow[]
}) {
  if (rows.length === 0) {
    return (
      <p className="muted-text">
        No preview rows are available for this dataset.
      </p>
    )
  }

  return (
    <div className="table-scroll">
      <table className="dataset-table">
        <thead>
          <tr>
            <th scope="col">#</th>

            {columns.map((column) => (
              <th scope="col" key={column.name}>
                {column.name}
              </th>
            ))}
          </tr>
        </thead>

        <tbody>
          {rows.map((row, rowIndex) => (
            <tr key={rowIndex}>
              <td>{rowIndex + 1}</td>

              {columns.map((column) => (
                <td key={column.name}>
                  {formatCell(row.values[column.name])}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function formatCell(value: unknown) {
  if (value === null || value === undefined) {
    return "—"
  }

  if (typeof value === "object") {
    return JSON.stringify(value)
  }

  return String(value)
}

export default DatasetTable