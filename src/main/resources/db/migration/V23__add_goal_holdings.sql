CREATE TABLE goal_holdings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id UUID NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
    holding_id UUID NOT NULL REFERENCES holdings(id) ON DELETE CASCADE,
    allocation_pct DECIMAL(5, 2) NOT NULL DEFAULT 100.00,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_goal_holding UNIQUE(goal_id, holding_id)
);

CREATE INDEX idx_goal_holdings_goal ON goal_holdings(goal_id);
CREATE INDEX idx_goal_holdings_holding ON goal_holdings(holding_id);
