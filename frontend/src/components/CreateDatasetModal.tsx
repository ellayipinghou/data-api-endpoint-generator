// interface Props {
//   onClose: () => void
// }

// function CreateDatasetModal({ onClose }: Props) {
//   return (
//     <div className="modal-backdrop">
//       <div className="modal-panel">
//         <div className="modal-header">
//           <h2>Create Dataset</h2>
//           <button className="close-button" onClick={onClose} aria-label="Close">
//             ×
//           </button>
//         </div>

//         <div className="modal-body">
//           <p>This is the CSV upload modal (Phase 2 will wire the upload/preview API).</p>

//           <div style={{ marginTop: 18 }}>
//             <label className="primary-button" style={{ cursor: "pointer" }}>
//               Choose File
//               <input type="file" accept=".csv" style={{ display: "none" }} />
//             </label>
//           </div>
//         </div>

//         <div className="modal-footer">
//           <button onClick={onClose} className="secondary-button">
//             Cancel
//           </button>
//           <button className="primary-button" disabled>
//             Continue
//           </button>
//         </div>
//       </div>
//     </div>
//   )
// }

// export default CreateDatasetModal


import { useState } from "react"
import { previewDataset, createDatasetFromPreview } from "../api/datasets"
import type { DatasetPreviewResponse } from "../types/dataset"

interface Props {
  onClose: () => void
  onCreated?: () => void
}

type Step = "select" | "previewing" | "preview" | "naming" | "creating"

function CreateDatasetModal({ onClose, onCreated }: Props) {
  const [step, setStep] = useState<Step>("select")
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<DatasetPreviewResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [datasetName, setDatasetName] = useState("")

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
      setStep("preview")
    } catch {
      setError("Failed to preview that file. Check that it's a valid CSV and try again.")
      setStep("select")
    }
  }

  function handleChooseDifferentFile() {
    setFile(null)
    setPreview(null)
    setError(null)
    setStep("select")
  }

  async function handleCreate() {
    if (!preview || !datasetName.trim()) return

    setStep("creating")
    setError(null)

    try {
      await createDatasetFromPreview(datasetName.trim(), preview.previewId)
      onCreated?.()
      onClose()
    } catch {
      setError("Failed to create the dataset. Please try again.")
      setStep("naming")
    }
  }

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
                    This file can't be submitted until every issue below is resolved.
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
                    You can still create this dataset, but you may want to double check these first.
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
                {preview.schema.columns.map((col) => (
                  <div className="schema-preview-row" key={col.name}>
                    <span>{col.name}</span>
                    <span>{col.type}</span>
                  </div>
                ))}
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