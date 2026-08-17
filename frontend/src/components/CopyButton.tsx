import { useState } from "react"
import { useToast } from "../context/ToastContext"

interface CopyButtonProps {
  text: string
  label?: string
}

function CopyButton({ text, label = "Copy" }: CopyButtonProps) {
  const { showError } = useToast()
  const [copied, setCopied] = useState(false)

  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(text)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1800)
    } catch (requestError) {
      const message = requestError instanceof Error
        ? requestError.message
        : "Failed to copy."

      setCopied(false)
      showError("Failed to copy: " + message)
    }
  }

  return (
    <button
      className="secondary-button copy-button"
      onClick={handleCopy}
    >
      {copied ? "Copied" : label}
    </button>
  )
}

export default CopyButton