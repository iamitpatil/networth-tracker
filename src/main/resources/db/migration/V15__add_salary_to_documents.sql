ALTER TABLE documents ADD COLUMN salary_id UUID REFERENCES salaries(id) ON DELETE SET NULL;
CREATE INDEX idx_documents_salary ON documents(salary_id);
