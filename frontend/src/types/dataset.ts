export type DataType =
    | "STRING"
    | "INTEGER"
    | "LONG"
    | "DOUBLE"
    | "BOOLEAN"
    | "DATE"
    | "DATETIME"

export interface DataColumn {
    name: string
    type: DataType
    operators: string[]
}

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

export interface DataRow {
    values: Record<string, unknown>
}

export type PreviewIssueKind = string

export interface PreviewIssue {
    kind: PreviewIssueKind
    message: string
    column: string | null
    isBlocking: boolean
}

export interface DatasetPreviewResponse {
    previewId: string
    schema: DatasetSchema
    sampleRows: DataRow[]
    issues: PreviewIssue[]
    canSubmit: boolean
}