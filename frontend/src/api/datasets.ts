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
  previewId: string,
  typeOverrides: Record<string, string> = {}
): Promise<Dataset> {
  const response = await fetch(`${API_URL}/datasets/from-preview`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name, previewId, typeOverrides }),
  })

  if (!response.ok) {
    const message = await response.text().catch(() => null)
    throw new Error(message || "Failed to create dataset")
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

export async function deleteDataset(id: string): Promise<void> {
  const response = await fetch(`${API_URL}/datasets/${id}`, {
    method: "DELETE",
  })

  if (!response.ok) {
    throw new Error("Failed to delete dataset")
  }
}