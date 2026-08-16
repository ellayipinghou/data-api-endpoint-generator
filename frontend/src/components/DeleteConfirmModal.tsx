interface Props {
  datasetName: string
  deleting: boolean
  error: string | null
  onCancel: () => void
  onConfirm: () => void
}

function DeleteConfirmModal({ datasetName, deleting, error, onCancel, onConfirm }: Props) {
  return (
    <div className="modal-backdrop">
      <div className="modal-panel">
        <div className="modal-header">
          <h2>Delete dataset</h2>
          <button className="close-button" onClick={onCancel} aria-label="Close">×</button>
        </div>

        <div className="modal-body">
          {error && <p style={{ color: "#b91c1c", marginBottom: 14 }}>{error}</p>}
          <p>
            Are you sure you want to delete <strong>{datasetName}</strong>? This will permanently remove
            the dataset and its data. This action cannot be undone.
          </p>
        </div>

        <div className="modal-footer">
          <button className="secondary-button" onClick={onCancel} disabled={deleting}>Cancel</button>
          <button className="primary-button" onClick={onConfirm} disabled={deleting}>
            {deleting ? "Deleting…" : "Delete dataset"}
          </button>
        </div>
      </div>
    </div>
  )
}

export default DeleteConfirmModal