import type { DataRow, Dataset, DatasetPreviewResponse } from "../types/dataset"

export const API_URL = "http://localhost:8080"

/**
 * Shared helper to extract detailed backend error messages or fallback gracefully.
 */
async function handleResponse<T>(response: Response, fallbackMessage: string): Promise<T> {
  if (response.status === 404) {
    throw new Error("Dataset not found")
  }

  if (!response.ok) {
    const errorData = await response.json().catch(() => null)
    const backendMessage = errorData?.message || errorData?.error
    throw new Error(backendMessage || fallbackMessage)
  }

  // Handle 204 No Content (e.g., DELETE requests)
  if (response.status === 204) {
    return undefined as unknown as T
  }

  return response.json()
}

export async function getDatasets(): Promise<Dataset[]> {
  const response = await fetch(`${API_URL}/datasets`)
  return handleResponse<Dataset[]>(response, "Failed to fetch datasets")
}

export async function previewDataset(file: File): Promise<DatasetPreviewResponse> {
  const formData = new FormData()
  formData.append("file", file)

  const response = await fetch(`${API_URL}/datasets/preview`, {
    method: "POST",
    body: formData,
  })

  return handleResponse<DatasetPreviewResponse>(response, "Failed to preview dataset")
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

  return handleResponse<Dataset>(response, "Failed to create dataset")
}

export async function getDataset(id: string): Promise<Dataset> {
  const response = await fetch(`${API_URL}/datasets/${id}`)
  return handleResponse<Dataset>(response, "Failed to fetch dataset")
}

export async function getDatasetPreview(id: string): Promise<DataRow[]> {
  const response = await fetch(`${API_URL}/datasets/${id}/query?limit=10`)
  return handleResponse<DataRow[]>(response, "Failed to fetch dataset preview")
}

export async function queryDataset(id: string, params: URLSearchParams): Promise<DataRow[]> {
  const response = await fetch(`${API_URL}/datasets/${id}/query?${params.toString()}`)
  return handleResponse<DataRow[]>(response, "Failed to run query")
}

export async function deleteDataset(id: string): Promise<void> {
  const response = await fetch(`${API_URL}/datasets/${id}`, {
    method: "DELETE",
  })

  return handleResponse<void>(response, "Failed to delete dataset")
}