import { useState, useRef } from 'react'
import client from '../api/client'
import { Upload, FileText, CheckCircle, AlertCircle, X, FileSpreadsheet, FileImage, Building2, Landmark, Loader2 } from 'lucide-react'

const sources = [
  { id: 'zerodha', label: 'Zerodha', desc: 'Equity transactions from console exports', icon: FileSpreadsheet, format: 'CSV' },
  { id: 'groww', label: 'Groww', desc: 'Mutual fund + equity from app exports', icon: FileSpreadsheet, format: 'CSV' },
  { id: 'cas', label: 'CAS', desc: 'Consolidated Account Statement from NSDL/CDSL', icon: FileImage, format: 'PDF' },
  { id: 'bank', label: 'Bank Statement', desc: 'Bank statement PDF parsing', icon: Landmark, format: 'PDF' },
]

export default function Import() {
  const [selectedSource, setSelectedSource] = useState(null)
  const [file, setFile] = useState(null)
  const [dragging, setDragging] = useState(false)
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const inputRef = useRef(null)

  const handleFile = (f) => {
    if (!f) return
    const source = sources.find(s => s.id === selectedSource)
    const validTypes = source?.format === 'PDF'
      ? ['application/pdf']
      : ['text/csv', 'application/vnd.ms-excel', 'text/plain']
    if (!validTypes.includes(f.type) && !(f.name.endsWith('.csv'))) {
      setError(`Please upload a ${source?.format} file`)
      return
    }
    setFile(f)
    setError(null)
    setResult(null)
  }

  const handleDrop = (e) => {
    e.preventDefault()
    setDragging(false)
    const f = e.dataTransfer.files[0]
    handleFile(f)
  }

  const handleUpload = async () => {
    if (!file || !selectedSource) return
    setLoading(true)
    setError(null)
    setResult(null)
    try {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('source', selectedSource)
      const { data } = await client.post('/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
      setResult({ count: data.imported || 0, holdings: data.holdings || [] })
    } catch (e) {
      setError(e.message || 'Failed to import file')
    } finally {
      setLoading(false)
    }
  }

  const reset = () => {
    setFile(null)
    setResult(null)
    setError(null)
    setSelectedSource(null)
    if (inputRef.current) inputRef.current.value = ''
  }

  return (
    <div className="space-y-6">
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-2">Import Portfolio</h1>
        <p className="text-[var(--text-muted)]">Upload your portfolio data from supported brokers and statements</p>
      </div>

      {!result && (
        <>
          <div className="mb-8">
            <h2 className="text-lg font-semibold mb-4 text-[var(--text)]">Select Source</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {sources.map(s => {
                const Icon = s.icon
                const isSelected = selectedSource === s.id
                return (
                  <button
                    key={s.id}
                    onClick={() => { setSelectedSource(s.id); setFile(null); setResult(null); setError(null) }}
                    className={`flex flex-col items-center gap-3 p-5 rounded-lg border-2 transition-all text-left
                      ${isSelected
                        ? 'border-[#3b82f6] bg-[var(--bg-card)] shadow-[0_0_0_1px_#3b82f6]'
                        : 'border-[var(--border)] bg-[var(--bg-card)] hover:border-[#475569]'
                      }`}
                  >
                    <Icon className={`w-8 h-8 ${isSelected ? 'text-[#3b82f6]' : 'text-[var(--text-muted)]'}`} />
                    <div>
                      <div className="font-semibold text-sm">{s.label}</div>
                      <div className="text-xs text-[var(--text-muted)] mt-1">{s.format}</div>
                    </div>
                  </button>
                )
              })}
            </div>
          </div>

          {selectedSource && (
            <div className="mb-8">
              <div
                onDragOver={e => { e.preventDefault(); setDragging(true) }}
                onDragLeave={() => setDragging(false)}
                onDrop={handleDrop}
                className={`border-2 border-dashed rounded-lg p-10 text-center transition-colors
                  ${dragging ? 'border-[#3b82f6] bg-[var(--bg-card)]/80' : 'border-[var(--border)] bg-[var(--bg-card)]'}
                  ${file ? 'border-[#22c55e]' : ''}`}
              >
                {file ? (
                  <div className="flex flex-col items-center gap-3">
                    <FileText className="w-10 h-10 text-[#22c55e]" />
                    <span className="text-sm font-medium">{file.name}</span>
                    <span className="text-xs text-[var(--text-muted)]">{(file.size / 1024).toFixed(1)} KB</span>
                    <button onClick={() => { setFile(null); if (inputRef.current) inputRef.current.value = '' }} className="text-xs text-[var(--text-muted)] hover:text-[var(--text)] flex items-center gap-1">
                      <X className="w-3 h-3" /> Remove
                    </button>
                  </div>
                ) : (
                  <div className="flex flex-col items-center gap-3">
                    <Upload className={`w-10 h-10 ${dragging ? 'text-[#3b82f6]' : 'text-[var(--text-muted)]'}`} />
                    <p className="text-sm text-[var(--text-muted)]">Drag & drop your file here, or{' '}
                      <label className="text-[#3b82f6] cursor-pointer hover:underline">
                        browse
                        <input ref={inputRef} type="file" accept={sources.find(s => s.id === selectedSource)?.format === 'PDF' ? '.pdf' : '.csv'} onChange={e => handleFile(e.target.files[0])} className="hidden" />
                      </label>
                    </p>
                    <p className="text-xs text-[var(--text-secondary)]">
                      {sources.find(s => s.id === selectedSource)?.format} files only
                    </p>
                  </div>
                )}
              </div>

              {error && (
                <div className="mt-4 flex items-center gap-2 text-red-400 text-sm bg-red-500/10 p-3 rounded-lg border border-red-500/20">
                  <AlertCircle className="w-4 h-4" /> {error}
                </div>
              )}

              {file && (
                <button
                  onClick={handleUpload}
                  disabled={loading}
                  className="mt-4 w-full py-3 bg-[#3b82f6] hover:bg-[#2563eb] disabled:opacity-50 rounded-lg font-medium flex items-center justify-center gap-2 transition-colors"
                >
                  {loading ? <><Loader2 className="w-4 h-4 animate-spin" /> Importing...</> : 'Import File'}
                </button>
              )}
            </div>
          )}
        </>
      )}

      {result && (
        <div className="mb-8 bg-[var(--bg-card)] border border-[#22c55e] rounded-lg p-6 text-center">
          <CheckCircle className="w-12 h-12 text-[#22c55e] mx-auto mb-4" />
          <h3 className="text-xl font-semibold mb-2">Import Successful!</h3>
          <p className="text-[var(--text-muted)] mb-1">{result.message || `Successfully imported ${result.count} transactions`}</p>
          <p className="text-3xl font-bold text-[#22c55e]">{result.count} records</p>
          <button onClick={reset} className="mt-6 px-6 py-2 bg-[#334155] hover:bg-[#475569] rounded-lg text-sm transition-colors">
            Import Another
          </button>
        </div>
      )}

      <div className="bg-[var(--bg-card)] border border-[var(--border)] rounded-lg p-6">
        <h3 className="text-lg font-semibold mb-4">Format Reference</h3>
        <div className="space-y-4">
          {sources.map(s => {
            const Icon = s.icon
            return (
              <div key={s.id} className="flex gap-3">
                <Icon className="w-5 h-5 text-[var(--text-muted)] mt-0.5 shrink-0" />
                <div>
                  <div className="font-medium text-sm">{s.label} <span className="text-[var(--text-secondary)]">({s.format})</span></div>
                  <div className="text-xs text-[var(--text-muted)] mt-0.5">{s.desc}</div>
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
