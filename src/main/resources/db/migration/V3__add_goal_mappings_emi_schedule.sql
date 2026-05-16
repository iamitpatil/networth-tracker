CREATE TABLE goal_mappings (
    goal_id UUID NOT NULL,
    holding_id UUID NOT NULL,
    allocation_percentage DECIMAL(5, 2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (goal_id, holding_id),
    CONSTRAINT fk_goal_mappings_goal FOREIGN KEY (goal_id) REFERENCES goals(id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_mappings_holding FOREIGN KEY (holding_id) REFERENCES holdings(id) ON DELETE CASCADE
);

CREATE TABLE emi_schedule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    liability_id UUID NOT NULL,
    emi_number INT NOT NULL,
    due_date DATE NOT NULL,
    principal_component DECIMAL(18, 2),
    interest_component DECIMAL(18, 2),
    total_emi DECIMAL(18, 2),
    is_paid BOOLEAN DEFAULT FALSE,
    paid_date DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT fk_emi_schedule_liability FOREIGN KEY (liability_id) REFERENCES liabilities(id) ON DELETE CASCADE
);

CREATE INDEX idx_goal_mappings_goal ON goal_mappings(goal_id);
CREATE INDEX idx_goal_mappings_holding ON goal_mappings(holding_id);
CREATE INDEX idx_emi_schedule_liability ON emi_schedule(liability_id, due_date);
CREATE INDEX idx_emi_schedule_due ON emi_schedule(due_date, is_paid);
