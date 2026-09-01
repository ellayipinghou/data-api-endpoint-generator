// manage the multi-step flow for uploading, previewing, and creating a dataset

import { useState } from "react"
import { previewDataset, createDatasetFromPreview } from "../api/datasets"
import type { DataType, DatasetPreviewResponse } from "../types/dataset"
import { useToast } from "../context/ToastContext"

interface Props {
  onClose: () => void
  onCreated?: () => void
}

type Step = "select" | "previewing" | "preview" | "naming" | "creating"

// TODO?: make submit button disabled when failed to create until something changes (a new file upload, etc)

function CreateDatasetModal({ onClose, onCreated }: Props) {
  const { showError } = useToast()

  // track which stage of the dataset creation flow the user is on
  const [step, setStep] = useState<Step>("select")

  // store the selected file and its preview data
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<DatasetPreviewResponse | null>(null)

  // store errors shown within the modal
  const [error, setError] = useState<string | null>(null)

  // store the dataset name and any user-selected type overrides
  const [datasetName, setDatasetName] = useState("")
  const [typeOverrides, setTypeOverrides] = useState<Record<string, DataType>>({})

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const selected = e.target.files?.[0] ?? null
    setFile(selected)
    setError(null)
  }

  async function handlePreview() {
    if (!file) return

    setStep("previewing")
    setError(null)

    try {
      const result = await previewDataset(file)
      setPreview(result)
      setTypeOverrides({})
      setStep("preview")
    } catch {
      setError("Failed to preview that file. Check that it's a valid CSV and try again.")
      setStep("select")
    }
  }

  function handleChooseDifferentFile() {
    setFile(null)
    setPreview(null)
    setTypeOverrides({})
    setError(null)
    setStep("select")
  }

  function handleTypeChange(columnName: string, newType: DataType) {
    setTypeOverrides((prev) => {
      // remove the override when the user changes the type back to the inferred type
      const inferred = preview?.schema.columns.find((c) => c.name === columnName)?.type
      if (newType === inferred) {
        const { [columnName]: _removed, ...rest } = prev
        return rest
      }

      // otherwise add or update the override for this column
      return { ...prev, [columnName]: newType }
    })
  }

  async function handleCreate() {
    if (!preview || !datasetName.trim()) return

    setStep("creating")
    setError(null)

    try {
      await createDatasetFromPreview(datasetName.trim(), preview.previewId, typeOverrides)
      onCreated?.()
      onClose()
    } catch (e: any) {
      // extract a usable error message from different possible error formats
      let rawMessage = "Please try again."
      
      if (e instanceof Error) {
        rawMessage = e.message
      } else if (typeof e === "string") {
        rawMessage = e
      } else if (e?.message) {
        rawMessage = String(e.message)
      }

      const formattedError = `Failed to create the dataset: ${rawMessage}`

      setError(formattedError)
      showError(formattedError)
      setStep("naming")
    }
  }

  // separate issues into those that block creation and those that are only warnings
  const blockingIssues = preview?.issues.filter((i) => i.isBlocking) ?? []
  const warningIssues = preview?.issues.filter((i) => !i.isBlocking) ?? []

  return (
    <div className="modal-backdrop">
      <div className="modal-panel">
        <div className="modal-header">
          <h2>Create Dataset</h2>
          <button className="close-button" onClick={onClose} aria-label="Close">
            ×
          </button>
        </div>

        <div className="modal-body">
          {error && (
            <p style={{ color: "#b91c1c", marginBottom: 14 }}>{error}</p>
          )}

          {(step === "select" || step === "previewing") && (
            <>
              <p>Upload a CSV to preview its columns and sample data.</p>

              <div style={{ marginTop: 18 }}>
                <label className="primary-button" style={{ cursor: "pointer" }}>
                  {file ? file.name : "Choose File"}
                  <input
                    type="file"
                    accept=".csv,text/csv"
                    style={{ display: "none" }}
                    onChange={handleFileChange}
                    disabled={step === "previewing"}
                  />
                </label>
              </div>
            </>
          )}

          {preview && (step === "preview" || step === "naming" || step === "creating") && (
            <>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <p className="regular-text">{file?.name}</p>
                <button className="secondary-button" onClick={handleChooseDifferentFile}>
                  Choose a different file
                </button>
              </div>

              {blockingIssues.length > 0 && (
                <div
                  role="alert"
                  style={{
                    marginTop: 16,
                    padding: "12px 14px",
                    borderRadius: 8,
                    background: "#fef9c3",
                    border: "1px solid #eab308",
                    color: "#713f12",
                  }}
                >
                  <p style={{ fontWeight: 600, marginBottom: 6 }}>
                    Fix your CSV and reupload before continuing
                  </p>
                  <p style={{ marginBottom: 8 }}>
                    This file can't be submitted until every issue below is resolved:
                  </p>
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {blockingIssues.map((issue, i) => (
                      <li key={`b-${i}`}>
                        {issue.column ? <strong>{issue.column}: </strong> : null}
                        {issue.message}
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              {warningIssues.length > 0 && (
                <div
                  style={{
                    marginTop: blockingIssues.length > 0 ? 10 : 16,
                    padding: "12px 14px",
                    borderRadius: 8,
                    background: "#fefce8",
                    border: "1px solid #fde68a",
                    color: "#713f12",
                  }}
                >
                  <p style={{ fontWeight: 600, marginBottom: 6 }}>
                    Non-blocking warnings
                  </p>
                  <p style={{ marginBottom: 8 }}>
                    You can still create this dataset, but you may want to double check these first:
                  </p>
                  <ul style={{ margin: 0, paddingLeft: 18 }}>
                    {warningIssues.map((issue, i) => (
                      <li key={`w-${i}`}>
                        {issue.column ? <strong>{issue.column}: </strong> : null}
                        {issue.message}
                      </li>
                    ))}
                  </ul>
                </div>
              )}

              <div className="schema-preview" style={{ marginTop: 16 }}>
                <h3>Detected columns</h3>
                <p style={{ marginBottom: 8, color: "#5b6472", fontSize: "0.85rem" }}>
                  Change a column's type if it was detected incorrectly.
                </p>
                {preview.schema.columns.map((col) => {
                  const options = preview.columnTypeOptions[col.name] ?? [col.type]
                  const currentType = typeOverrides[col.name] ?? col.type

                  return (
                    <div className="schema-preview-row" key={col.name}>
                      <span>{col.name}</span>
                      <select
                        value={currentType}
                        onChange={(e) => handleTypeChange(col.name, e.target.value as DataType)}
                        disabled={step === "creating"}
                        style={{ padding: "4px 8px", borderRadius: 6, border: "1px solid #d7dee8" }}
                      >
                        {options.map((option) => (
                          <option key={option} value={option}>
                            {option}
                          </option>
                        ))}
                      </select>
                    </div>
                  )
                })}
              </div>

              <div className="schema-preview" style={{ maxHeight: 220, overflow: "auto" }}>
                <h3>Sample rows</h3>
                <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.85rem" }}>
                  <thead>
                    <tr>
                      {preview.schema.columns.map((col) => (
                        <th key={col.name} style={{ textAlign: "left", padding: "6px 8px", borderBottom: "1px solid #eef2f7" }}>
                          {col.name}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {preview.sampleRows.map((row, rowIdx) => (
                      <tr key={rowIdx}>
                        {preview.schema.columns.map((col) => (
                          <td key={col.name} style={{ padding: "6px 8px", borderBottom: "1px solid #eef2f7" }}>
                            {String(row.values[col.name] ?? "")}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {(step === "naming" || step === "creating") && (
                <div style={{ marginTop: 16 }}>
                  <label htmlFor="dataset-name" style={{ display: "block", marginBottom: 6, fontWeight: 600 }}>
                    Dataset name
                  </label>
                  <input
                    id="dataset-name"
                    type="text"
                    value={datasetName}
                    onChange={(e) => setDatasetName(e.target.value)}
                    placeholder="e.g. Q3 sales export"
                    autoFocus
                    disabled={step === "creating"}
                    style={{ width: "100%", padding: 10, borderRadius: 8, border: "1px solid #d7dee8" }}
                  />
                </div>
              )}
            </>
          )}
        </div>

        <div className="modal-footer">
          <button onClick={onClose} className="secondary-button">
            Cancel
          </button>

          {(step === "select" || step === "previewing") && (
            <button
              className="primary-button"
              onClick={handlePreview}
              disabled={!file || step === "previewing"}
            >
              {step === "previewing" ? "Previewing…" : "Continue"}
            </button>
          )}

          {step === "preview" && (
            <button
              className="primary-button"
              onClick={() => setStep("naming")}
              disabled={!preview?.canSubmit}
              title={preview?.canSubmit ? undefined : "Resolve blocking issues before creating this dataset"}
            >
              Continue
            </button>
          )}

          {(step === "naming" || step === "creating") && (
            <button
              className="primary-button"
              onClick={handleCreate}
              disabled={!datasetName.trim() || step === "creating"}
            >
              {step === "creating" ? "Creating…" : "Create dataset"}
            </button>
          )}
        </div>
      </div>
    </div>
  )
}

export default CreateDatasetModal