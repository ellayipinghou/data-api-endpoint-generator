import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from "react"

// shape of an active toast notification
interface Toast {
  id: number
  message: string
}

// interface exposed to consumers via context
interface ToastContextValue {
  showError: (message: string) => void
}

// React Context instance holding the toast controls
const ToastContext = createContext<ToastContextValue | null>(null)

// duration (in ms) before a toast automatically disappears
const AUTO_DISMISS_MS = 6000

export function ToastProvider({ children }: { children: ReactNode }) {
  // currently active toast (null if none visible)
  const [toast, setToast] = useState<Toast | null>(null)
  
  // reference to active auto-dismiss timeout to allow cancellation/resetting
  const timerRef = useRef<number | null>(null)

  // immediately dismisses the active toast and cancels pending timers
  const dismiss = useCallback(() => {
    if (timerRef.current) window.clearTimeout(timerRef.current)
    setToast(null)
  }, [])

  // displays an error toast and schedules auto-dismissal
  const showError = useCallback((message: string) => {
    // clear existing timer if a new toast arrives before previous one expires
    if (timerRef.current) window.clearTimeout(timerRef.current)
    
    setToast({ id: Date.now(), message })
    
    // auto-dismiss toast after designated timeout
    timerRef.current = window.setTimeout(() => setToast(null), AUTO_DISMISS_MS)
  }, [])

  return (
    <ToastContext.Provider value={{ showError }}>
      {/* Toast banner overlay */}
      {toast && (
        <div className="toast-banner" role="alert">
          <span>{toast.message}</span>
          <button className="toast-dismiss" onClick={dismiss} aria-label="Dismiss">×</button>
        </div>
      )}
      {/* Render children underneath context provider */}
      {children}
    </ToastContext.Provider>
  )
}

// custom hook to consume the ToastContext safely
export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) {
    throw new Error("useToast must be used within a ToastProvider")
  }
  return ctx
}