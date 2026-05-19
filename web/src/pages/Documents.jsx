import { useState, useEffect, useRef } from 'react'
import { FileText, Plus, Trash2, Download, Upload, X, Loader2, FolderOpen, Building2 } from 'lucide-react'
import client from '../api/client'

const CATEGORIES = ['INVOICE', 'ID_PROOF', 'STATEMENT', 'REPORT', 'OTHER']

const CATEGORY_COLORS = {
  INVOICE: 'text-orange-400 bg-orange-500/20',
  ID_PROOF: 'text-purple-400 bg-purple-500/20',
  STATEMENT: 'text-blue-400 bg-blue-500/20',
  REPORT: 'text-green-400 bg-green-500/20',
  OTHER: 'text-slate-400 bg-slate-500/20',
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatDate(dateStr) {
  return new Date(dateStr).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

export default function Documents() {
  const [documents, setDocuments] = useState([])
  const [groupedDocs, setGroupedDocs] = useState({})
  const [dematAccounts, setDematAccounts] = useState([])
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const [showUpload, setShowUpload] = useState(false)
  const [viewMode, setViewMode] = useState('grouped') // 'grouped' or 'flat'
  const [category, setCategory] = useState('OTHER')
  const [description, setDescription] = useState('')
  const [dematAccountId, setDematAccountId] = useState('')
  const [dragOver, setDragOver] = useState(false)
  const [selectedFile, setSelectedFile] = useState(null)
  const fileInputRef = useRef(null)

  const loadDocuments = () => {
    Promise.all([
      client.get('/documents'),
      client.get('/documents/grouped'),
      client.get('/demat-accounts'),
    ]).then(([docRes, groupRes, dematRes]) => {
      setDocuments(docRes.data || [])
      setGroupedDocs(groupRes.data || {})
      setDematAccounts(dematRes.data || [])
    }).catch(console.error).finally(() => setLoading(false))
  }

  useEffect(() => {
    loadDocuments()
  }, [])

  const dematMap = {}
  dematAccounts.forEach(d => { dematMap[d.id] = d })

  async function handleUpload(e) {
    e.preventDefault()
    if (!selectedFile) return
    setUploading(true)
    try {
      const formData = new FormData()
      formData.append('file', selectedFile)
      if (category) formData.append('category', category)
      if (description) formData.append('description', description)
      if (dematAccountId) formData.append('dematAccountId', dematAccountId)

      const { data } = await client.post('/documents/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      setDocuments([data, ...documents])
      setShowUpload(false)
      setSelectedFile(null)
      setCategory('OTHER')
      setDescription('')
      setDematAccountId('')
    } catch (err) {
      console.error('Upload failed', err)
    } finally {
      setUploading(false)
    }
  }

  async function handleDelete(id) {
    if (!confirm('Delete this document?')) return
    try {
      await client.delete(`/documents/${id}`)
      setDocuments(documents.filter(d => d.id !== id))
    } catch (err) {
      console.error('Delete failed', err)
    }
  }

  async function handleView(doc) {
    try {
      const { data } = await client.get(`/documents/${doc.id}/view`, {
        responseType: 'blob',
      })
      const blob = new Blob([data], { type: doc.contentType || 'application/octet-stream' })
      const url = window.URL.createObjectURL(blob)
      window.open(url, '_blank')
      setTimeout(() => window.URL.revokeObjectURL(url), 60000)
    } catch (err) {
      console.error('View failed', err)
    }
  }

  async function handleDownload(doc) {
    try {
      const { data, headers } = await client.get(`/documents/${doc.id}/download`, {
        responseType: 'blob',
      })
      const url = window.URL.createObjectURL(new Blob([data]))
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', doc.originalFilename)
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(url)
    } catch (err) {
      console.error('Download failed', err)
    }
  }

  function handleDrop(e) {
    e.preventDefault()
    setDragOver(false)
    const file = e.dataTransfer.files[0]
    if (file) setSelectedFile(file)
  }

  if (loading) return <div className="flex justify-center py-20 text-slate-400">Loading...</div>

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Documents</h1>
          <p className="text-slate-400 text-sm mt-1">Store invoices, ID proofs, statements and more</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex bg-slate-700 rounded-lg p-1">
            <button
              onClick={() => setViewMode('grouped')}
              className={`px-3 py-1 rounded text-sm transition ${viewMode === 'grouped' ? 'bg-blue-500 text-white' : 'text-slate-400 hover:text-white'}`}
            >
              Grouped
            </button>
            <button
              onClick={() => setViewMode('flat')}
              className={`px-3 py-1 rounded text-sm transition ${viewMode === 'flat' ? 'bg-blue-500 text-white' : 'text-slate-400 hover:text-white'}`}
            >
              All
            </button>
          </div>
          <button
            onClick={() => setShowUpload(!showUpload)}
            className={`px-4 py-2 rounded-lg flex items-center gap-2 transition ${showUpload ? 'bg-slate-600 hover:bg-slate-500' : 'bg-blue-500 hover:bg-blue-600'}`}
          >
            <Upload className="w-4 h-4" /> {showUpload ? 'Cancel' : 'Upload'}
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-slate-800 rounded-xl p-5 border border-slate-700">
          <p className="text-slate-400 text-sm">Total Documents</p>
          <p className="text-2xl font-bold mt-1">{documents.length}</p>
        </div>
        <div className="bg-slate-800 rounded-xl p-5 border border-slate-700">
          <p className="text-slate-400 text-sm">Total Size</p>
          <p className="text-2xl font-bold mt-1">{formatSize(documents.reduce((s, d) => s + (d.fileSize || 0), 0))}</p>
        </div>
        <div className="bg-slate-800 rounded-xl p-5 border border-slate-700">
          <p className="text-slate-400 text-sm">Categories</p>
          <p className="text-2xl font-bold mt-1">{new Set(documents.map(d => d.category)).size}</p>
        </div>
      </div>

      {showUpload && (
        <div className="bg-slate-800 rounded-xl p-6 border border-slate-700">
          <h3 className="text-lg font-semibold mb-4">Upload Document</h3>
          <form onSubmit={handleUpload} className="space-y-4">
            <div
              onDragOver={(e) => { e.preventDefault(); setDragOver(true) }}
              onDragLeave={() => setDragOver(false)}
              onDrop={handleDrop}
              onClick={() => fileInputRef.current?.click()}
              className={`border-2 border-dashed rounded-xl p-10 text-center cursor-pointer transition ${
                dragOver ? 'border-blue-400 bg-blue-500/10' : 'border-slate-600 hover:border-slate-500'
              } ${selectedFile ? 'bg-slate-700/50' : ''}`}
            >
              {selectedFile ? (
                <div>
                  <FileText className="w-10 h-10 text-blue-400 mx-auto mb-2" />
                  <p className="font-medium">{selectedFile.name}</p>
                  <p className="text-sm text-slate-400 mt-1">{formatSize(selectedFile.size)}</p>
                  <button
                    type="button"
                    onClick={(e) => { e.stopPropagation(); setSelectedFile(null) }}
                    className="mt-2 text-sm text-red-400 hover:text-red-300"
                  >
                    Remove
                  </button>
                </div>
              ) : (
                <div>
                  <Upload className="w-10 h-10 text-slate-500 mx-auto mb-2" />
                  <p className="text-slate-400">Drop a file here or click to browse</p>
                  <p className="text-sm text-slate-500 mt-1">Max 20MB</p>
                </div>
              )}
              <input
                ref={fileInputRef}
                type="file"
                onChange={(e) => setSelectedFile(e.target.files[0])}
                className="hidden"
              />
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div>
                <label className="block text-sm text-slate-400 mb-1">Category</label>
                <select value={category} onChange={(e) => setCategory(e.target.value)} className="w-full bg-slate-700 border border-slate-600 rounded-lg px-3 py-2.5">
                  {CATEGORIES.map((c) => (
                    <option key={c} value={c}>{c.replace('_', ' ')}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm text-slate-400 mb-1">Demat Account <span className="text-slate-500">(optional)</span></label>
                <select value={dematAccountId} onChange={(e) => setDematAccountId(e.target.value)} className="w-full bg-slate-700 border border-slate-600 rounded-lg px-3 py-2.5">
                  <option value="">None (general document)</option>
                  {dematAccounts.map((d) => (
                    <option key={d.id} value={d.id}>{d.brokerName}{d.accountNumber ? ` (${d.accountNumber})` : ''}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm text-slate-400 mb-1">Description <span className="text-slate-500">(optional)</span></label>
                <input type="text" value={description} onChange={(e) => setDescription(e.target.value)} className="w-full bg-slate-700 border border-slate-600 rounded-lg px-3 py-2.5" placeholder="e.g., Gold invoice 2025" />
              </div>
            </div>

            <div className="flex justify-end">
              <button
                type="submit"
                disabled={uploading || !selectedFile}
                className={`px-6 py-2 rounded-lg font-medium flex items-center gap-2 transition ${
                  uploading || !selectedFile ? 'bg-slate-600 cursor-not-allowed opacity-50' : 'bg-blue-500 hover:bg-blue-600'
                }`}
              >
                {uploading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Upload className="w-4 h-4" />}
                {uploading ? 'Uploading...' : 'Upload'}
              </button>
            </div>
          </form>
        </div>
      )}

      {documents.length === 0 ? (
        <div className="bg-slate-800 rounded-xl p-12 border border-slate-700 text-center">
          <FolderOpen className="w-12 h-12 text-slate-600 mx-auto mb-4" />
          <p className="text-slate-400 mb-2">No documents uploaded yet</p>
          <p className="text-slate-500 text-sm">Upload invoices, ID proofs, or account statements here</p>
        </div>
      ) : viewMode === 'grouped' ? (
        <div className="space-y-4">
          {Object.entries(groupedDocs).map(([groupName, docs]) => (
            <div key={groupName} className="bg-slate-800 rounded-xl border border-slate-700 overflow-hidden">
              <div className="px-4 py-3 bg-slate-700/30 border-b border-slate-700 flex items-center justify-between">
                <h3 className="font-semibold text-sm">{groupName}</h3>
                <span className="text-xs text-slate-400">{docs.length} {docs.length === 1 ? 'document' : 'documents'}</span>
              </div>
              <div className="divide-y divide-slate-700">
                {docs.map((doc) => (
                  <div key={doc.id} className="flex items-center justify-between px-4 py-3 hover:bg-slate-700/30">
                    <div className="flex items-center gap-3 min-w-0 flex-1">
                      <FileText className="w-5 h-5 text-slate-500 flex-shrink-0" />
                      <div className="min-w-0">
                        <div className="font-medium text-sm truncate">{doc.originalFilename}</div>
                        <div className="flex items-center gap-2 mt-0.5">
                          <span className={`text-xs px-1.5 py-0.5 rounded ${CATEGORY_COLORS[doc.category] || CATEGORY_COLORS['OTHER']}`}>
                            {doc.category.replace('_', ' ')}
                          </span>
                          <span className="text-xs text-slate-500">{formatSize(doc.fileSize)}</span>
                          <span className="text-xs text-slate-500">•</span>
                          <span className="text-xs text-slate-500">{formatDate(doc.createdAt)}</span>
                        </div>
                      </div>
                    </div>
                    <div className="flex gap-2 ml-3">
                      <button onClick={() => handleView(doc)} className="text-slate-500 hover:text-green-400 transition" title="View">
                        <FileText className="w-4 h-4" />
                      </button>
                      <button onClick={() => handleDownload(doc)} className="text-slate-500 hover:text-blue-400 transition" title="Download">
                        <Download className="w-4 h-4" />
                      </button>
                      <button onClick={() => handleDelete(doc.id)} className="text-slate-500 hover:text-red-400 transition" title="Delete">
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="bg-slate-800 rounded-xl border border-slate-700 overflow-hidden">
          <table className="w-full">
            <thead className="bg-slate-700/50 text-left">
              <tr>
                <th className="px-4 py-3 text-sm font-medium text-slate-400">File</th>
                <th className="px-4 py-3 text-sm font-medium text-slate-400">Category</th>
                <th className="px-4 py-3 text-sm font-medium text-slate-400">Account</th>
                <th className="px-4 py-3 text-sm font-medium text-slate-400">Size</th>
                <th className="px-4 py-3 text-sm font-medium text-slate-400">Uploaded</th>
                <th className="px-4 py-3 text-sm font-medium text-slate-400"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-700">
              {documents.map((doc) => (
                <tr key={doc.id} className="hover:bg-slate-700/30">
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-3">
                      <FileText className="w-5 h-5 text-slate-500 flex-shrink-0" />
                      <div className="min-w-0">
                        <div className="font-medium text-sm truncate max-w-[250px]">{doc.originalFilename}</div>
                        {doc.description && <div className="text-xs text-slate-500 truncate max-w-[250px]">{doc.description}</div>}
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    <span className={`text-xs px-2 py-1 rounded ${CATEGORY_COLORS[doc.category] || CATEGORY_COLORS['OTHER']}`}>
                      {doc.category.replace('_', ' ')}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    {doc.dematAccountId && dematMap[doc.dematAccountId] ? (
                      <div className="flex items-center gap-1 text-xs text-slate-400">
                        <Building2 className="w-3 h-3" />
                        {dematMap[doc.dematAccountId].brokerName}
                      </div>
                    ) : (
                      <span className="text-xs text-slate-600">—</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-sm text-slate-400">{formatSize(doc.fileSize)}</td>
                  <td className="px-4 py-3 text-sm text-slate-400">{formatDate(doc.createdAt)}</td>
                  <td className="px-4 py-3">
                    <div className="flex gap-2">
                      <button onClick={() => handleView(doc)} className="text-slate-500 hover:text-green-400 transition" title="View">
                        <FileText className="w-4 h-4" />
                      </button>
                      <button onClick={() => handleDownload(doc)} className="text-slate-500 hover:text-blue-400 transition" title="Download">
                        <Download className="w-4 h-4" />
                      </button>
                      <button onClick={() => handleDelete(doc.id)} className="text-slate-500 hover:text-red-400 transition" title="Delete">
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
