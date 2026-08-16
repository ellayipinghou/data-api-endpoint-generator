import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react"

interface Toast {
  id: number
  message: string
}

interface ToastContextValue {
  showError: (message: string) => void
}

const ToastContext = createContext<ToastContextValue | null>(null)

const AUTO_DISMISS_MS = 6000

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<Toast | null>(null)
  const timerRef = useRef<number | null>(null)

  const dismiss = useCallback(() => {
    if (timerRef.current) window.clearTimeout(timerRef.current)
    setToast(null)
  }, [])

  const showError = useCallback((message: string) => {
    if (timerRef.current) window.clearTimeout(timerRef.current)
    setToast({ id: Date.now(), message })
    timerRef.current = window.setTimeout(() => setToast(null), AUTO_DISMISS_MS)
  }, [])

  return (
    <ToastContext.Provider value={{ showError }}>
      {toast && (
        <div className="toast-banner" role="alert">
          <span>{toast.message}</span>
          <button className="toast-dismiss" onClick={dismiss} aria-label="Dismiss">×</button>
        </div>
      )}
      {children}
    </ToastContext.Provider>
  )
}

export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) {
    throw new Error("useToast must be used within a ToastProvider")
  }
  return ctx
}