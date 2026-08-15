import type { DataRow, Dataset, DatasetPreviewResponse } from "../types/dataset"

export const API_URL = "http://localhost:8080"

export async function getDatasets(): Promise<Dataset[]> {
  const response = await fetch(`${API_URL}/datasets`)

  if (!response.ok) {
    throw new Error("Failed to fetch datasets")
  }

  return response.json()
}

export async function previewDataset(file: File): Promise<DatasetPreviewResponse> {
  const formData = new FormData()
  formData.append("file", file)

  const response = await fetch(`${API_URL}/datasets/preview`, {
    method: "POST",
    body: formData,
  })

  if (!response.ok) {
    throw new Error("Failed to preview dataset")
  }

  return response.json()
}

export async function createDatasetFromPreview(
  name: string,
  previewId: string
): Promise<Dataset> {
  const params = new URLSearchParams({ name, previewId })

  const response = await fetch(`${API_URL}/datasets/from-preview?${params.toString()}`, {
    method: "POST",
  })

  if (!response.ok) {
    throw new Error("Failed to create dataset")
  }

  return response.json()
}

export async function getDataset(id: string): Promise<Dataset> {
  const response = await fetch(`${API_URL}/datasets/${id}`)

  if (response.status === 404) {
    throw new Error("Dataset not found")
  }

  if (!response.ok) {
    throw new Error("Failed to fetch dataset")
  }

  return response.json()
}

export async function getDatasetPreview(id: string): Promise<DataRow[]> {
  const response = await fetch(`${API_URL}/datasets/${id}/query?limit=10`)

  if (!response.ok) {
    throw new Error("Failed to fetch dataset preview")
  }

  return response.json()
}

export async function queryDataset(id: string, params: URLSearchParams): Promise<DataRow[]> {
  const response = await fetch(`${API_URL}/datasets/${id}/query?${params.toString()}`)

  if (!response.ok) {
    throw new Error("Failed to run query")
  }

  return response.json()
}
