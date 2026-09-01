// provide shared state and controls for opening and closing the create dataset modal
// allow any component to open the create dataset modal without managing its state directly

import { createContext, useContext, useState } from "react"
import type { ReactNode } from "react"
import CreateDatasetModal from "../components/CreateDatasetModal"

// define the functions and state exposed by the modal context
type CreateModalContextType = {
  open: (onCreated?: () => void) => void // callback to run after successful dataset creation
  close: () => void
  isOpen: boolean
}

const CreateModalContext = createContext<CreateModalContextType | null>(null)

export function CreateModalProvider({ children }: { children: ReactNode }) {
  const [isOpen, setIsOpen] = useState(false)
  const [onCreated, setOnCreated] = useState<(() => void) | undefined>(undefined)

  // open the modal and store an optional callback for successful creation
  const open = (callback?: () => void) => {
    // wrap the callback so React does not treat it as a state updater
    setOnCreated(() => callback)
    setIsOpen(true)
  }

  // close the modal and clear the callback
  const close = () => {
    setIsOpen(false)
    setOnCreated(undefined)
  }

  return (
    <CreateModalContext.Provider value={{ open, close, isOpen }}>
      {children}
      {isOpen && <CreateDatasetModal onClose={close} onCreated={onCreated} />}
    </CreateModalContext.Provider>
  )
}

// provide access to the create modal from child components
export function useCreateModal() {
  const ctx = useContext(CreateModalContext)

  // ensure the hook is only used inside the provider
  if (!ctx) throw new Error("useCreateModal must be used within CreateModalProvider")

  return ctx
}

export default CreateModalContext
