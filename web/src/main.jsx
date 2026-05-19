import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Toaster } from 'sonner'
import { AuthProvider, useAuth } from './context/AuthContext'
import { ThemeProvider } from './context/ThemeContext'
import { FamilyViewProvider } from './context/FamilyViewContext'
import { FeatureFlagProvider } from './context/FeatureFlagContext'
import Layout from './components/Layout'
import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/Dashboard'
import NetWorth from './pages/NetWorth'
import Holdings from './pages/Holdings'
import Transactions from './pages/Transactions'
import Analytics from './pages/Analytics'
import Tax from './pages/Tax'
import Goals from './pages/Goals'
import Liabilities from './pages/Liabilities'
import Import from './pages/Import'
import DematAccounts from './pages/DematAccounts'
import Documents from './pages/Documents'
import Profile from './pages/Profile'
import AIChat from './pages/AIChat'
import Family from './pages/Family'
import BankAccounts from './pages/BankAccounts'
import Salaries from './pages/Salaries'
import ErrorBoundary from './components/ErrorBoundary'
import './index.css'

function ProtectedRoute({ children }) {
  const { user, loading } = useAuth()
  if (loading) return <div className="flex justify-center items-center min-h-screen">Loading...</div>
  return user ? children : <Navigate to="/login" />
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <ErrorBoundary>
    <BrowserRouter>
      <AuthProvider>
        <ThemeProvider>
        <FeatureFlagProvider>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/" element={<ProtectedRoute><FamilyViewProvider><Layout /></FamilyViewProvider></ProtectedRoute>}>
            <Route index element={<Navigate to="/dashboard" />} />
            <Route path="dashboard" element={<Dashboard />} />
            <Route path="net-worth" element={<NetWorth />} />
            <Route path="holdings" element={<Holdings />} />
            <Route path="transactions" element={<Transactions />} />
            <Route path="analytics" element={<Analytics />} />
            <Route path="tax" element={<Tax />} />
            <Route path="goals" element={<Goals />} />
            <Route path="liabilities" element={<Liabilities />} />
            <Route path="import" element={<Import />} />
            <Route path="demat-accounts" element={<DematAccounts />} />
            <Route path="documents" element={<Documents />} />
            <Route path="ai-chat" element={<AIChat />} />
            <Route path="profile" element={<Profile />} />
            <Route path="family" element={<Family />} />
            <Route path="bank-accounts" element={<BankAccounts />} />
            <Route path="salary" element={<Salaries />} />
          </Route>
          <Route path="*" element={<Navigate to="/dashboard" />} />
        </Routes>
        <Toaster
          position="top-right"
          theme="dark"
          richColors
          closeButton
          duration={4000}
          toastOptions={{
            style: {
              background: 'var(--bg-card)',
              color: 'var(--text)',
              border: '1px solid var(--border)',
            },
          }}
        />
        </FeatureFlagProvider>
        </ThemeProvider>
      </AuthProvider>
    </BrowserRouter>
    </ErrorBoundary>
  </StrictMode>
)
