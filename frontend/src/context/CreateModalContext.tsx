// import { createContext, useContext, useState } from "react"
// import type { ReactNode } from "react"
// import CreateDatasetModal from "../components/CreateDatasetModal"

// type CreateModalContextType = {
//   open: () => void
//   close: () => void
//   isOpen: boolean
// }

// const CreateModalContext = createContext<CreateModalContextType | null>(null)

// export function CreateModalProvider({ children }: { children: ReactNode }) {
//   const [isOpen, setIsOpen] = useState(false)

//   const open = () => setIsOpen(true)
//   const close = () => setIsOpen(false)

//   return (
//     <CreateModalContext.Provider value={{ open, close, isOpen }}>
//       {children}
//       {isOpen && <CreateDatasetModal onClose={close} />}
//     </CreateModalContext.Provider>
//   )
// }

// export function useCreateModal() {
//   const ctx = useContext(CreateModalContext)
//   if (!ctx) throw new Error("useCreateModal must be used within CreateModalProvider")
//   return ctx
// }

// export default CreateModalContext

import { createContext, useContext, useState } from "react"
import type { ReactNode } from "react"
import CreateDatasetModal from "../components/CreateDatasetModal"

type CreateModalContextType = {
  open: (onCreated?: () => void) => void
  close: () => void
  isOpen: boolean
}

const CreateModalContext = createContext<CreateModalContextType | null>(null)

export function CreateModalProvider({ children }: { children: ReactNode }) {
  const [isOpen, setIsOpen] = useState(false)
  const [onCreated, setOnCreated] = useState<(() => void) | undefined>(undefined)

  const open = (callback?: () => void) => {
    // wrap in a function so React's setState doesn't treat it as a state updater
    setOnCreated(() => callback)
    setIsOpen(true)
  }

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

export function useCreateModal() {
  const ctx = useContext(CreateModalContext)
  if (!ctx) throw new Error("useCreateModal must be used within CreateModalProvider")
  return ctx
}

export default CreateModalContext
