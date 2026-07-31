import { useEffect, useState } from 'react'

export function useAdminToast(durationMs = 2000) {
  const [toast, setToast] = useState<string | null>(null)

  useEffect(() => {
    if (!toast) return
    const timer = window.setTimeout(() => setToast(null), durationMs)
    return () => window.clearTimeout(timer)
  }, [toast, durationMs])

  return {
    toast,
    showToast: setToast,
    toastNode: toast ? (
      <div className="adminToast" role="status" aria-live="polite">
        {toast}
      </div>
    ) : null,
  }
}
