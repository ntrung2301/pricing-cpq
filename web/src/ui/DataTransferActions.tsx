import { DownloadOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons'
import { useRef, useState } from 'react'
import { Button } from './Button'

interface DataTransferActionsProps {
  filename: string
  data: readonly unknown[]
  accept?: string
  onImport?: (file: File) => Promise<string>
  importDisabledReason?: string
  onReload?: () => void
}

export function DataTransferActions({
  filename, data, accept = '.json,application/json', onImport, importDisabledReason, onReload,
}: DataTransferActionsProps) {
  const input = useRef<HTMLInputElement>(null)
  const [importing, setImporting] = useState(false)
  const [feedback, setFeedback] = useState<{ message: string; error?: boolean }>()

  function exportData() {
    const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }))
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    link.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  }

  async function importData(file: File) {
    if (!onImport) return
    setFeedback(undefined)
    setImporting(true)
    try {
      setFeedback({ message: await onImport(file) })
    } catch (error) {
      setFeedback({ message: error instanceof Error ? error.message : 'Import failed.', error: true })
    } finally {
      setImporting(false)
    }
  }

  return (
    <>
      <Button icon={<DownloadOutlined aria-hidden="true" />} disabled={data.length === 0} onClick={exportData}>
        Export
      </Button>
      <span title={importDisabledReason}>
        <Button
          icon={<UploadOutlined aria-hidden="true" />}
          disabled={!onImport}
          loading={importing}
          onClick={() => input.current?.click()}
        >
          Import
        </Button>
      </span>
      {onReload && (
        <Button icon={<ReloadOutlined aria-hidden="true" />} disabled={importing} onClick={() => {
          setFeedback(undefined)
          onReload()
        }}>
          Reload
        </Button>
      )}
      {onImport && (
        <input
          ref={input}
          type="file"
          aria-label="Import file"
          accept={accept}
          hidden
          onChange={(event) => {
            const file = event.currentTarget.files?.[0]
            event.currentTarget.value = ''
            if (file) void importData(file)
          }}
        />
      )}
      {feedback && (
        <span className={feedback.error ? 'ui-field-error' : undefined} role={feedback.error ? 'alert' : 'status'}>
          {feedback.message}
        </span>
      )}
    </>
  )
}
