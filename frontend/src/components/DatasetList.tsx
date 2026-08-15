import DatasetCard from "./DatasetCard"
import type { Dataset } from "../types/dataset"

interface DatasetListProps {
  datasets: Dataset[]
}

function DatasetList({ datasets }: DatasetListProps) {
  return (
    <div className="dataset-grid">
      {datasets.map((dataset) => (
        <DatasetCard key={dataset.id} dataset={dataset} />
      ))}
    </div>
  )
}

export default DatasetList