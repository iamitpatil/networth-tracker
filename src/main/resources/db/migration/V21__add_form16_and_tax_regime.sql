-- V21: Tax regime support, Form 16, and ITR filing storage

-- Add tax regime preference to users
ALTER TABLE users ADD COLUMN IF NOT EXISTS tax_regime VARCHAR(10) DEFAULT 'NEW';

-- Form 16 data storage
CREATE TABLE IF NOT EXISTS form16_data (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    financial_year VARCHAR(9) NOT NULL,
    assessment_year VARCHAR(9),

    -- Part A (TDS Summary from Employer)
    employer_name VARCHAR(255),
    employer_pan VARCHAR(20),
    employer_tan VARCHAR(20),
    employee_pan VARCHAR(20),

    -- Salary breakup
    gross_salary DECIMAL(18, 2) DEFAULT 0,
    exempt_allowances DECIMAL(18, 2) DEFAULT 0,
    professional_tax DECIMAL(18, 2) DEFAULT 0,
    standard_deduction DECIMAL(18, 2) DEFAULT 0,
    taxable_salary DECIMAL(18, 2) DEFAULT 0,

    -- TDS info (Part A)
    tds_total DECIMAL(18, 2) DEFAULT 0,
    quarterly_tds JSONB,  -- {q1: amount, q2: amount, q3: amount, q4: amount}

    -- Deductions (Part B)
    section_80c DECIMAL(18, 2) DEFAULT 0,
    section_80ccd_1b DECIMAL(18, 2) DEFAULT 0,
    section_80d DECIMAL(18, 2) DEFAULT 0,
    section_80g DECIMAL(18, 2) DEFAULT 0,
    section_80tta DECIMAL(18, 2) DEFAULT 0,
    other_deductions JSONB,

    -- Computed totals
    total_taxable_income DECIMAL(18, 2),
    total_tax_liability DECIMAL(18, 2),
    tax_regime VARCHAR(10) DEFAULT 'OLD',

    -- Source tracking
    document_id UUID,  -- link to documents table if uploaded
    source VARCHAR(50) DEFAULT 'MANUAL', -- MANUAL, FORM_16_PDF
    parse_confidence INT, -- 0-100 confidence score from auto-parsing
    raw_data JSONB,  -- raw parsed fields for debugging

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT fk_form16_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_form16_user_fy UNIQUE (user_id, financial_year)
);

CREATE INDEX IF NOT EXISTS idx_form16_user_fy ON form16_data(user_id, financial_year);

-- ITR Filing storage - records of filed tax returns
CREATE TABLE IF NOT EXISTS itr_filings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    financial_year VARCHAR(9) NOT NULL,
    assessment_year VARCHAR(9),

    -- ITR Form details
    itr_form_type VARCHAR(20),  -- ITR-1, ITR-2, ITR-3, ITR-4, etc.
    acknowledgement_number VARCHAR(50),  -- 15-digit ITR-V ack number
    filing_date DATE,
    filing_type VARCHAR(20),  -- ORIGINAL, REVISED, BELATED
    ewaiver_date DATE,
    e_verified BOOLEAN DEFAULT FALSE,
    e_verification_mode VARCHAR(50),  -- AADHAAR_OTP, NET_BANKING, EVC, etc.

    -- Income summary
    gross_total_income DECIMAL(18, 2) DEFAULT 0,
    total_deductions DECIMAL(18, 2) DEFAULT 0,
    total_taxable_income DECIMAL(18, 2) DEFAULT 0,

    -- Tax details
    total_tax_payable DECIMAL(18, 2) DEFAULT 0,
    tds_total DECIMAL(18, 2) DEFAULT 0,
    advance_tax_paid DECIMAL(18, 2) DEFAULT 0,
    self_assessment_tax DECIMAL(18, 2) DEFAULT 0,
    tax_refund DECIMAL(18, 2) DEFAULT 0,
    refund_status VARCHAR(50),  -- PENDING, ISSUED, FAILED, ADJUSTED

    -- Regime used
    tax_regime VARCHAR(10),

    -- Source
    document_id UUID,
    source VARCHAR(50) DEFAULT 'MANUAL',  -- MANUAL, ITR_PDF, ITR_JSON
    parse_confidence INT,
    raw_data JSONB,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT fk_itr_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_itr_user_fy_type UNIQUE (user_id, financial_year, filing_type)
);

CREATE INDEX IF NOT EXISTS idx_itr_user_fy ON itr_filings(user_id, financial_year);

-- Add new document linkages for tax documents and bank statements
ALTER TABLE documents ADD COLUMN IF NOT EXISTS form16_id UUID;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS itr_filing_id UUID;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS bank_account_id UUID;

CREATE INDEX IF NOT EXISTS idx_documents_form16 ON documents(form16_id);
CREATE INDEX IF NOT EXISTS idx_documents_itr ON documents(itr_filing_id);
CREATE INDEX IF NOT EXISTS idx_documents_bank ON documents(bank_account_id);

-- Add foreign key constraints (with ON DELETE SET NULL to preserve documents if parent deleted)
ALTER TABLE documents
    DROP CONSTRAINT IF EXISTS fk_documents_form16,
    ADD CONSTRAINT fk_documents_form16 FOREIGN KEY (form16_id) REFERENCES form16_data(id) ON DELETE SET NULL;

ALTER TABLE documents
    DROP CONSTRAINT IF EXISTS fk_documents_itr,
    ADD CONSTRAINT fk_documents_itr FOREIGN KEY (itr_filing_id) REFERENCES itr_filings(id) ON DELETE SET NULL;
