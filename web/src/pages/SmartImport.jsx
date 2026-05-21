import { useState, useRef, useCallback } from 'react'
import client from '../api/client'
import {
  Upload, FileText, CheckCircle, AlertCircle, X, Loader2,
  Brain, ShieldCheck, Route, Play, FileType, Search,
  CreditCard, Wallet, FileSpreadsheet, Briefcase, Building2,
  ChevronRight, Lock, Eye, EyeOff, ArrowLeft, RefreshCw
} from 'lucide-react'
import { toast } from 'sonner'
import { Button, Card, Badge, PageHeader } from '../components/ui'

const NODE_ICONS = {
  pdfTextExtract: FileType,
  classify: Brain,
  extract: FileSpreadsheet,
  entityResolve: Search,
  route: Route,
  execute: Play,
}

const NODE_LABELS = {
  pdfTextExtract: 'Extracting text from document...',
  classify: 'Classifying document type...',
  extract: 'Extracting structured data...',
  entityResolve: 'Matching entities in your portfolio...',
  route: 'Determining actions to take...',
  execute: 'Executing actions...',
}

const DOCUMENT_ICONS = {
  CREDIT_CARD_BILL: CreditCard,
  SALARY_SLIP: Wallet,
  BANK_STATEMENT: Building2,
  CAS: Briefcase,
  FORM_16: FileText,
  UNKNOWN: FileText,
}

function NodeStep({ name, active, completed, error }) {
  const Icon = NODE_ICONS[name] || Loader2
  return (
    <div className={`flex items-center gap-3 px-4 py-2.5 rounded-lg transition-all ${
      active ? 'bg-blue-500/10 border border-blue-500/20' :
      completed ? 'bg-green-500/10 border border-green-500/20' :
      error ? 'bg-red-500/10 border border-red-500/20' :
      'bg-[var(--bg-card)]/50 border border-[var(--border)] opacity-50'
    }`}>
      <Icon className={`w-4 h-4 ${
        active ? 'text-blue-400 animate-pulse' :
        completed ? 'text-green-400' :
        error ? 'text-red-400' : 'text-[var(--text-muted)]'
      }`} />
      <span className={`text-sm ${
        active ? 'text-blue-400 font-medium' :
        completed ? 'text-green-400' :
        error ? 'text-red-400' : 'text-[var(--text-muted)]'
      }`}>
        {NODE_LABELS[name] || name}
      </span>
      {completed && <CheckCircle className="w-3.5 h-3.5 text-green-400 ml-auto" />}
      {error && <AlertCircle className="w-3.5 h-3.5 text-red-400 ml-auto" />}
      {active && <Loader2 className="w-3.5 h-3.5 text-blue-400 animate-spin ml-auto" />}
    </div>
  )
}

function DocumentTypeBadge({ type }) {
  const Icon = DOCUMENT_ICONS[type] || FileText
  const colors = {
    CREDIT_CARD_BILL: 'bg-purple-500/10 text-purple-400 border-purple-500/20',
    SALARY_SLIP: 'bg-green-500/10 text-green-400 border-green-500/20',
    BANK_STATEMENT: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
    CAS: 'bg-amber-500/10 text-amber-400 border-amber-500/20',
    FORM_16: 'bg-cyan-500/10 text-cyan-400 border-cyan-500/20',
    UNKNOWN: 'bg-gray-500/10 text-gray-400 border-gray-500/20',
  }
  return (
    <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium border ${colors[type] || colors.UNKNOWN}`}>
      <Icon className="w-3.5 h-3.5" />
      {type?.replace(/_/g, ' ') || 'Unknown'}
    </span>
  )
}

function ExtractedDataCard({ data, documentType }) {
  if (!data) return null
  const type = documentType || data.type
  return (
    <Card className="space-y-3">
      <h4 className="font-semibold text-sm flex items-center gap-2">
        <FileSpreadsheet className="w-4 h-4 text-blue-400" />
        Extracted Data
      </h4>
      <div className="grid grid-cols-2 md:grid-cols-3 gap-3">
        {type === 'Credit Card Bill' && (
          <>
            {data.issuer && <DataField label="Card Issuer" value={data.issuer} />}
            {data.dueDate && <DataField label="Due Date" value={data.dueDate} />}
            {data.totalAmountDue && <DataField label="Total Due" value={`₹${Number(data.totalAmountDue).toLocaleString()}`} />}
            {data.transactionCount !== undefined && <DataField label="Transactions" value={data.transactionCount} />}
          </>
        )}
        {type === 'Salary Slip' && (
          <>
            {data.employer && <DataField label="Employer" value={data.employer} />}
            {data.grossPay && <DataField label="Gross Pay" value={`₹${Number(data.grossPay).toLocaleString()}`} />}
            {data.netPay && <DataField label="Net Pay" value={`₹${Number(data.netPay).toLocaleString()}`} />}
          </>
        )}
        {type === 'Bank Statement' && (
          <>
            {data.transactionCount !== undefined && <DataField label="Transactions" value={data.transactionCount} />}
          </>
        )}
        {(type === 'Form 16' || data.grossSalary) && (
          <>
            {data.employer && <DataField label="Employer" value={data.employer} />}
            {data.grossSalary && <DataField label="Gross Salary" value={`₹${Number(data.grossSalary).toLocaleString()}`} />}
            {data.confidence !== undefined && <DataField label="Confidence" value={`${data.confidence}%`} />}
          </>
        )}
        {!['Credit Card Bill', 'Salary Slip', 'Bank Statement', 'Form 16'].includes(type) && (
          Object.entries(data).filter(([k]) => !['type'].includes(k)).slice(0, 6).map(([k, v]) => (
            <DataField key={k} label={k.replace(/([A-Z])/g, ' $1').trim()} value={typeof v === 'object' ? JSON.stringify(v).slice(0, 50) : String(v)} />
          ))
        )}
      </div>
    </Card>
  )
}

function DataField({ label, value }) {
  return (
    <div>
      <p className="text-xs text-[var(--text-muted)] mb-0.5">{label}</p>
      <p className="text-sm font-medium">{value}</p>
    </div>
  )
}

const ACTION_LABELS = {
  CREATE_TRANSACTION: 'Create Transaction',
  UPDATE_CC_SPEND: 'Update Credit Card Spend',
  UPDATE_SALARY: 'Record Salary',
  UPDATE_ACCOUNT_BALANCE: 'Update Account Balance',
  UPDATE_USER_TAX_INFO: 'Update Tax Info',
}

const ACTION_ICONS = {
  CREATE_TRANSACTION: Briefcase,
  UPDATE_CC_SPEND: CreditCard,
  UPDATE_SALARY: Wallet,
  UPDATE_ACCOUNT_BALANCE: Building2,
  UPDATE_USER_TAX_INFO: FileText,
}

function ProposedActionRow({ action, index, selected, onToggle }) {
  const Icon = ACTION_ICONS[action.type] || Play
  return (
    <label className={`flex items-start gap-3 p-4 rounded-lg border cursor-pointer transition-all ${
      selected
        ? 'border-blue-500 bg-blue-500/5'
        : 'border-[var(--border)] hover:border-[var(--border)] bg-[var(--bg-card)]'
    }`}>
      <input
        type="checkbox"
        checked={selected}
        onChange={() => onToggle(index)}
        className="mt-0.5 w-4 h-4 accent-blue-500"
      />
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-1">
          <Icon className="w-4 h-4 text-blue-400 shrink-0" />
          <span className="font-medium text-sm">{ACTION_LABELS[action.type] || action.type}</span>
        </div>
        {action.description && (
          <p className="text-xs text-[var(--text-muted)]">{action.description}</p>
        )}
        {action.entityName && (
          <p className="text-xs text-[var(--text-secondary)] mt-1">Entity: {action.entityName}</p>
        )}
      </div>
      {action.confidence && (
        <Badge variant={action.confidence > 0.8 ? 'green' : action.confidence > 0.5 ? 'amber' : 'red'}>
          {Math.round(action.confidence * 100)}%
        </Badge>
      )}
    </label>
  )
}

function ExecutedActionRow({ action }) {
  const Icon = ACTION_ICONS[action.action?.type] || Play
  return (
    <div className={`flex items-start gap-3 p-4 rounded-lg border ${
      action.success
        ? 'border-green-500/20 bg-green-500/5'
        : 'border-red-500/20 bg-red-500/5'
    }`}>
      {action.success
        ? <CheckCircle className="w-5 h-5 text-green-400 mt-0.5 shrink-0" />
        : <AlertCircle className="w-5 h-5 text-red-400 mt-0.5 shrink-0" />
      }
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-1">
          <Icon className="w-4 h-4 text-blue-400 shrink-0" />
          <span className="font-medium text-sm">{ACTION_LABELS[action.action?.type] || action.action?.type}</span>
          <Badge variant={action.success ? 'green' : 'red'}>{action.success ? 'Success' : 'Failed'}</Badge>
        </div>
        {action.result && Object.keys(action.result).length > 0 && (
          <p className="text-xs text-[var(--text-muted)]">{JSON.stringify(action.result).slice(0, 120)}</p>
        )}
        {action.errorMessage && (
          <p className="text-xs text-red-400 mt-1">{action.errorMessage}</p>
        )}
      </div>
    </div>
  )
}

export default function SmartImport() {
  const [phase, setPhase] = useState('idle') // idle | password_required | processing | awaiting_review | completed | error
  const [file, setFile] = useState(null)
  const [dragging, setDragging] = useState(false)
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [correlationId, setCorrelationId] = useState(null)
  const [response, setResponse] = useState(null)
  const [currentNode, setCurrentNode] = useState(null)
  const [completedNodes, setCompletedNodes] = useState(new Set())
  const [selectedActions, setSelectedActions] = useState(new Set())
  const [confirming, setConfirming] = useState(false)
  const inputRef = useRef(null)
  const pollRef = useRef(null)

  const handleFile = useCallback((f) => {
    if (!f) return
    setFile(f)
    setPhase('idle')
    setResponse(null)
    setCorrelationId(null)
    setCurrentNode(null)
    setCompletedNodes(new Set())
    setSelectedActions(new Set())
    setPassword('')
  }, [])

  const handleDrop = useCallback((e) => {
    e.preventDefault()
    setDragging(false)
    handleFile(e.dataTransfer.files[0])
  }, [handleFile])

  const startProcessing = async (pw) => {
    if (!file) return
    setPhase('processing')
    setResponse(null)
    setCurrentNode(null)
    setCompletedNodes(new Set())
    setSelectedActions(new Set())

    const formData = new FormData()
    formData.append('file', file)
    if (pw || password) formData.append('password', pw || password)

    try {
      const { data } = await client.post('/documents/process', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      handleResponse(data)
    } catch (e) {
      if (e.response?.status === 422) {
        setPhase('password_required')
        if (e.response?.data?.correlationId) setCorrelationId(e.response.data.correlationId)
      } else {
        setPhase('error')
        setResponse({ error: e.message || 'Failed to process document' })
      }
    }
  }

  const handleResponse = (data) => {
    setResponse(data)
    setCurrentNode(data.currentNode)
    setCorrelationId(data.correlationId)

    if (data.status === 'COMPLETED') {
      setPhase('completed')
      setCompletedNodes(new Set(['pdfTextExtract', 'classify', 'extract', 'entityResolve', 'route', 'execute']))
      return
    }

    if (data.status === 'AWAITING_REVIEW') {
      setPhase('awaiting_review')
      setCompletedNodes(new Set(['pdfTextExtract', 'classify', 'extract', 'entityResolve', 'route']))
      return
    }

    if (data.status === 'ERROR') {
      setPhase('error')
      return
    }

    // PROCESSING — poll via interval tracking node progression
    const tracked = new Set(completedNodes)
    const nodes = ['pdfTextExtract', 'classify', 'extract', 'entityResolve', 'route', 'execute']
    const idx = nodes.indexOf(data.currentNode)
    for (let i = 0; i < idx; i++) tracked.add(nodes[i])
    if (data.currentNode) tracked.add(data.currentNode)
    setCompletedNodes(tracked)
  }

  const poll = () => {
    if (!correlationId) return
    pollRef.current = setInterval(async () => {
      try {
        const { data } = await client.get(`/documents/process/${correlationId}/status`)
        handleResponse(data)
      } catch {
        clearInterval(pollRef.current)
      }
    }, 1500)
  }

  const confirmActions = async () => {
    if (selectedActions.size === 0) {
      toast.warning('Select at least one action to confirm')
      return
    }
    setConfirming(true)
    try {
      const { data } = await client.post(`/documents/process/${correlationId}/confirm`, {
        actions: Array.from(selectedActions).map(Number).sort(),
      })
      handleResponse(data)
    } catch (e) {
      setPhase('error')
      setResponse({ error: e.message || 'Failed to confirm actions' })
    } finally {
      setConfirming(false)
    }
  }

  const toggleAction = (idx) => {
    setSelectedActions(prev => {
      const next = new Set(prev)
      if (next.has(idx)) next.delete(idx)
      else next.add(idx)
      return next
    })
  }

  const reset = () => {
    if (pollRef.current) clearInterval(pollRef.current)
    setFile(null)
    setPhase('idle')
    setResponse(null)
    setCorrelationId(null)
    setCurrentNode(null)
    setCompletedNodes(new Set())
    setSelectedActions(new Set())
    setPassword('')
    if (inputRef.current) inputRef.current.value = ''
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="Smart Import"
        subtitle="AI-powered document processing — upload any financial document and let AI extract, resolve, and act"
      />

      {/* Upload zone */}
      {phase === 'idle' && (
        <Card>
          <div
            onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
            onDragLeave={() => setDragging(false)}
            onDrop={handleDrop}
            className={`border-2 border-dashed rounded-lg p-12 text-center transition-all ${
              dragging ? 'border-blue-500 bg-blue-500/5' :
              file ? 'border-green-500' : 'border-[var(--border)]'
            }`}
          >
            {file ? (
              <div className="flex flex-col items-center gap-3">
                <FileText className="w-12 h-12 text-green-400" />
                <span className="font-medium">{file.name}</span>
                <span className="text-xs text-[var(--text-muted)]">{(file.size / 1024).toFixed(1)} KB</span>
                <button onClick={() => setFile(null)} className="text-xs text-[var(--text-muted)] hover:text-[var(--text)] flex items-center gap-1">
                  <X className="w-3 h-3" /> Remove
                </button>
                <Button onClick={() => startProcessing()} className="mt-2">
                  <Brain className="w-4 h-4" />
                  Process with AI
                </Button>
              </div>
            ) : (
              <div className="flex flex-col items-center gap-3">
                <Brain className={`w-12 h-12 ${dragging ? 'text-blue-400' : 'text-[var(--text-muted)]'}`} />
                <p className="text-sm text-[var(--text-muted)]">
                  Drag & drop any financial document, or{' '}
                  <label className="text-blue-400 cursor-pointer hover:underline">
                    browse
                    <input
                      ref={inputRef}
                      type="file"
                      accept=".pdf,.csv,.png,.jpg,.jpeg"
                      onChange={(e) => handleFile(e.target.files[0])}
                      className="hidden"
                    />
                  </label>
                </p>
                <p className="text-xs text-[var(--text-secondary)]">
                  Credit card bills, salary slips, bank statements, Form 16, CAS
                </p>
              </div>
            )}
          </div>
        </Card>
      )}

      {/* Password required */}
      {phase === 'password_required' && (
        <Card className="space-y-4">
          <div className="flex items-center gap-3">
            <Lock className="w-6 h-6 text-amber-400" />
            <div>
              <h3 className="font-semibold">Password Required</h3>
              <p className="text-sm text-[var(--text-muted)]">This PDF is password-protected. Enter the password to proceed.</p>
            </div>
          </div>
          <div className="relative">
            <input
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter PDF password"
              className="w-full px-4 py-2.5 pr-10 rounded-lg border border-[var(--border)] bg-[var(--input-bg)] text-sm"
              onKeyDown={(e) => e.key === 'Enter' && startProcessing(password)}
              autoFocus
            />
            <button
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--text-muted)] hover:text-[var(--text)]"
            >
              {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          </div>
          <div className="flex gap-3">
            <Button onClick={() => startProcessing(password)}>
              <Lock className="w-4 h-4" /> Unlock & Process
            </Button>
            <Button variant="secondary" onClick={reset}>
              <X className="w-4 h-4" /> Cancel
            </Button>
          </div>
        </Card>
      )}

      {/* Processing */}
      {phase === 'processing' && (
        <Card className="space-y-3">
          <div className="flex items-center gap-3 mb-2">
            <Loader2 className="w-5 h-5 text-blue-400 animate-spin" />
            <h3 className="font-semibold">Processing Document</h3>
          </div>
          {response?.summary && (
            <p className="text-sm text-[var(--text-muted)] mb-2">{response.summary}</p>
          )}
          <div className="space-y-2">
            {['pdfTextExtract', 'classify', 'extract', 'entityResolve', 'route', 'execute'].map((name) => (
              <NodeStep
                key={name}
                name={name}
                active={currentNode === name}
                completed={completedNodes.has(name)}
                error={response?.error && currentNode === name}
              />
            ))}
          </div>
        </Card>
      )}

      {/* Awaiting review */}
      {phase === 'awaiting_review' && response && (
        <div className="space-y-4">
          <div className="flex items-center gap-3 mb-2">
            <ShieldCheck className="w-5 h-5 text-amber-400" />
            <h3 className="font-semibold">Review Proposed Actions</h3>
          </div>

          <Card className="space-y-3">
            <div className="flex items-center gap-2">
              <DocumentTypeBadge type={response.documentType} />
              <span className="text-xs text-[var(--text-muted)]">
                Confidence: {Math.round((response.classificationConfidence || 0) * 100)}%
              </span>
            </div>
            {response.classificationReasoning && (
              <p className="text-xs text-[var(--text-muted)] italic">{response.classificationReasoning}</p>
            )}
          </Card>

          {response.extractedData && Object.keys(response.extractedData).length > 0 && (
            <ExtractedDataCard data={response.extractedData} documentType={response.documentType} />
          )}

          {response.proposedActions?.length > 0 && (
            <Card className="space-y-3">
              <div className="flex items-center justify-between">
                <h4 className="font-semibold text-sm flex items-center gap-2">
                  <Play className="w-4 h-4 text-blue-400" />
                  Proposed Actions ({response.proposedActions.length})
                </h4>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setSelectedActions(new Set(response.proposedActions.map((_, i) => i)))}
                >
                  Select All
                </Button>
              </div>
              <div className="space-y-2">
                {response.proposedActions.map((action, idx) => (
                  <ProposedActionRow
                    key={idx}
                    action={action}
                    index={idx}
                    selected={selectedActions.has(idx)}
                    onToggle={toggleAction}
                  />
                ))}
              </div>
            </Card>
          )}

          <div className="flex gap-3">
            <Button onClick={confirmActions} loading={confirming} disabled={selectedActions.size === 0}>
              <ShieldCheck className="w-4 h-4" />
              Confirm & Execute ({selectedActions.size})
            </Button>
            <Button variant="secondary" onClick={reset}>
              <X className="w-4 h-4" /> Discard
            </Button>
          </div>
        </div>
      )}

      {/* Completed */}
      {phase === 'completed' && response && (
        <div className="space-y-4">
          <Card className="border-green-500/30">
            <div className="flex flex-col items-center gap-3 py-4">
              <CheckCircle className="w-12 h-12 text-green-400" />
              <h3 className="text-xl font-semibold">Processing Complete</h3>
              <p className="text-sm text-[var(--text-muted)]">{response.summary}</p>
            </div>
          </Card>

          {response.documentType && (
            <Card>
              <div className="flex items-center gap-2">
                <DocumentTypeBadge type={response.documentType} />
                {response.classificationConfidence > 0 && (
                  <span className="text-xs text-[var(--text-muted)]">
                    Confidence: {Math.round(response.classificationConfidence * 100)}%
                  </span>
                )}
              </div>
            </Card>
          )}

          {response.matchedEntities?.length > 0 && (
            <Card className="space-y-2">
              <h4 className="font-semibold text-sm flex items-center gap-2">
                <Search className="w-4 h-4 text-green-400" />
                Matched Entities ({response.matchedEntities.length})
              </h4>
              <div className="space-y-1">
                {response.matchedEntities.map((entity, idx) => (
                  <div key={idx} className="flex items-center gap-2 text-sm text-[var(--text-muted)]">
                    <CheckCircle className="w-3 h-3 text-green-400 shrink-0" />
                    <span>{entity.entityType}: {entity.entityName}</span>
                    {entity.confidence && <Badge variant="green">{Math.round(entity.confidence * 100)}%</Badge>}
                  </div>
                ))}
              </div>
            </Card>
          )}

          {response.executedActions?.length > 0 && (
            <Card className="space-y-3">
              <h4 className="font-semibold text-sm flex items-center gap-2">
                <Play className="w-4 h-4 text-green-400" />
                Executed Actions ({response.executedActions.length})
              </h4>
              <div className="space-y-2">
                {response.executedActions.map((action, idx) => (
                  <ExecutedActionRow key={idx} action={action} />
                ))}
              </div>
            </Card>
          )}

          <Button onClick={reset}>
            <Upload className="w-4 h-4" /> Process Another Document
          </Button>
        </div>
      )}

      {/* Error */}
      {phase === 'error' && response && (
        <Card className="border-red-500/30">
          <div className="flex flex-col items-center gap-3 py-4">
            <AlertCircle className="w-12 h-12 text-red-400" />
            <h3 className="text-xl font-semibold text-red-400">Processing Failed</h3>
            <p className="text-sm text-[var(--text-muted)]">{response.error || 'An unknown error occurred'}</p>
            <div className="flex gap-3 mt-2">
              <Button variant="secondary" onClick={reset}>
                <RefreshCw className="w-4 h-4" /> Try Again
              </Button>
            </div>
          </div>
        </Card>
      )}
    </div>
  )
}
