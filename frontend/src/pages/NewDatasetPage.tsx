import { Link } from "react-router-dom"

function NewDatasetPage() {
  return (
    <div className="page-shell">
      <div className="page-header">
        <div>
          <p className="eyebrow">Create Dataset</p>
          <h1>Upload your CSV</h1>
        </div>
      </div>

      <div className="empty-panel">
        <p>
          This is the upload step for Phase 2 of the frontend MVP.
        </p>
        <p className="muted-text">The preview flow is coming next.</p>

        <div className="cta-row">
          <Link to="/datasets" className="secondary-button">
            Back to datasets
          </Link>
        </div>
      </div>
    </div>
  )
}

export default NewDatasetPage


// import { useState } from "react"
// import { useNavigate, Link } from "react-router-dom"
// import { previewDataset, createDatasetFromPreview } from "../api/datasets"
// import type { DatasetPreviewResponse } from "../types/dataset"

// type Step = "select" | "previewing" | "preview" | "creating"

// function NewDatasetPage() {
//   const navigate = useNavigate()

//   const [step, setStep] = useState<Step>("select")
//   const [file, setFile] = useState<File | null>(null)
//   const [preview, setPreview] = useState<DatasetPreviewResponse | null>(null)
//   const [error, setError] = useState<string | null>(null)

//   const [showNameModal, setShowNameModal] = useState(false)
//   const [datasetName, setDatasetName] = useState("")

//   function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
//     const selected = e.target.files?.[0] ?? null
//     setFile(selected)
//     setError(null)
//   }

//   async function handlePreview() {
//     if (!file) return

//     setStep("previewing")
//     setError(null)

//     try {
//       const result = await previewDataset(file)
//       setPreview(result)
//       setStep("preview")
//     } catch (err) {
//       setError(err instanceof Error ? err.message : "Something went wrong previewing that file.")
//       setStep("select")
//     }
//   }

//   function handleStartOver() {
//     setFile(null)
//     setPreview(null)
//     setError(null)
//     setStep("select")
//   }

//   async function handleCreate() {
//     if (!preview || !datasetName.trim()) return

//     setStep("creating")
//     setError(null)

//     try {
//       const dataset = await createDatasetFromPreview(datasetName.trim(), preview.previewId)
//       navigate(`/datasets/${dataset.id}`)
//     } catch (err) {
//       setError(err instanceof Error ? err.message : "Something went wrong creating the dataset.")
//       setStep("preview")
//       setShowNameModal(false)
//     }
//   }

//   const blockingIssues = preview?.issues.filter((i) => i.isBlocking) ?? []
//   const warningIssues = preview?.issues.filter((i) => !i.isBlocking) ?? []

//   return (
//     <div className="page-shell">
//       <div className="page-header">
//         <div>
//           <p className="eyebrow">Create Dataset</p>
//           <h1>Upload your CSV</h1>
//           <p className="subtitle">
//             Upload a CSV to preview its columns and sample data before creating a dataset.
//           </p>
//         </div>
//       </div>

//       {error && (
//         <div className="empty-panel error-box" style={{ marginBottom: 20 }}>
//           <h3>Something went wrong</h3>
//           <p className="muted-text">{error}</p>
//         </div>
//       )}

//       {step !== "preview" && step !== "creating" && (
//         <div className="empty-panel">
//           <h2>Choose a CSV file</h2>
//           <p className="muted-text">
//             We'll scan the first rows to detect columns and flag any issues before you commit.
//           </p>

//           <input
//             type="file"
//             accept=".csv,text/csv"
//             onChange={handleFileChange}
//             disabled={step === "previewing"}
//           />

//           <div className="cta-row" style={{ display: "flex", gap: 12 }}>
//             <button
//               className="primary-button"
//               onClick={handlePreview}
//               disabled={!file || step === "previewing"}
//             >
//               {step === "previewing" ? "Previewing…" : "Preview CSV"}
//             </button>
//             <Link to="/datasets" className="secondary-button">
//               Back to datasets
//             </Link>
//           </div>
//         </div>
//       )}

//       {preview && (step === "preview" || step === "creating") && (
//         <div className="dataset-card" style={{ marginTop: 20 }}>
//           <div className="dataset-card-header">
//             <div>
//               <h2>Preview</h2>
//               <p className="muted-text">{file?.name}</p>
//             </div>
//             <button className="secondary-button" onClick={handleStartOver}>
//               Choose a different file
//             </button>
//           </div>

//           <div className="schema-preview">
//             <h3>Detected columns</h3>
//             {preview.schema.columns.map((col) => (
//               <div className="schema-preview-row" key={col.name}>
//                 <span>{col.name}</span>
//                 <span className="muted-text">{col.type}</span>
//               </div>
//             ))}
//           </div>

//           {(blockingIssues.length > 0 || warningIssues.length > 0) && (
//             <div className="schema-preview">
//               <h3>Issues</h3>
//               <div className="issue-list">
//                 {blockingIssues.map((issue, i) => (
//                   <div className="issue-item issue-item--blocking" key={`b-${i}`}>
//                     <strong>{issue.column ?? "File"}:</strong> {issue.message}
//                   </div>
//                 ))}
//                 {warningIssues.map((issue, i) => (
//                   <div className="issue-item" key={`w-${i}`}>
//                     <strong>{issue.column ?? "File"}:</strong> {issue.message}
//                   </div>
//                 ))}
//               </div>
//             </div>
//           )}

//           <div className="schema-preview">
//             <h3>Sample rows</h3>
//             <div style={{ overflowX: "auto" }}>
//               <table className="preview-table">
//                 <thead>
//                   <tr>
//                     {preview.schema.columns.map((col) => (
//                       <th key={col.name}>{col.name}</th>
//                     ))}
//                   </tr>
//                 </thead>
//                 <tbody>
//                   {preview.sampleRows.map((row, rowIdx) => (
//                     <tr key={rowIdx}>
//                       {preview.schema.columns.map((col) => (
//                         <td key={col.name}>{String(row.values[col.name] ?? "")}</td>
//                       ))}
//                     </tr>
//                   ))}
//                 </tbody>
//               </table>
//             </div>
//           </div>

//           <div className="cta-row">
//             <button
//               className="primary-button"
//               disabled={!preview.canSubmit || step === "creating"}
//               onClick={() => setShowNameModal(true)}
//               title={preview.canSubmit ? undefined : "Resolve blocking issues before creating this dataset"}
//             >
//               Create dataset
//             </button>
//           </div>
//         </div>
//       )}

//       {showNameModal && (
//         <div className="modal-backdrop" onClick={() => setShowNameModal(false)}>
//           <div className="modal-panel" onClick={(e) => e.stopPropagation()}>
//             <div className="modal-header">
//               <h3>Name your dataset</h3>
//               <button className="close-button" onClick={() => setShowNameModal(false)}>
//                 ×
//               </button>
//             </div>
//             <div className="modal-body">
//               <input
//                 type="text"
//                 value={datasetName}
//                 onChange={(e) => setDatasetName(e.target.value)}
//                 placeholder="e.g. Q3 sales export"
//                 autoFocus
//                 style={{ width: "100%", padding: 10, borderRadius: 8, border: "1px solid #d7dee8" }}
//               />
//             </div>
//             <div className="modal-footer">
//               <button className="secondary-button" onClick={() => setShowNameModal(false)}>
//                 Cancel
//               </button>
//               <button
//                 className="primary-button"
//                 onClick={handleCreate}
//                 disabled={!datasetName.trim() || step === "creating"}
//               >
//                 {step === "creating" ? "Creating…" : "Create"}
//               </button>
//             </div>
//           </div>
//         </div>
//       )}
//     </div>
//   )
// }

// export default NewDatasetPage