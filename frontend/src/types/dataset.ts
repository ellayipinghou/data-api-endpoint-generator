export interface DataColumn {
  name: string
  type: DataType
  operators: string[]
}

export type DataType =
  | "STRING"
  | "INTEGER"
  | "LONG"
  | "DOUBLE"
  | "BOOLEAN"
  | "DATE"
  | "DATETIME"

export interface DatasetSchema {
  columns: DataColumn[]
}

export interface Dataset {
  id: string
  name: string
  schema: DatasetSchema
  rowCount: number
  createdAt: string
}

// DataRow serializes as { "values": { ...arbitrary columns } } -
// Jackson treats DataRow as a bean with a single "values" property,
// it does NOT flatten the map to the top level.
export interface DataRow {
  values: Record<string, unknown>
}

// Backend sends this as a free-form string (no enum on the Java side),
// so keep it loose rather than unioning specific literals.
export type PreviewIssueKind = string

export interface PreviewIssue {
  kind: PreviewIssueKind
  column: string | null
  message: string
  isBlocking: boolean
}

export interface DatasetPreviewResponse {
  previewId: string
  schema: DatasetSchema
  sampleRows: DataRow[]
  issues: PreviewIssue[]
  canSubmit: boolean
}
