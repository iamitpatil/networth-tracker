-- AI Chat sessions for conversation persistence
CREATE TABLE ai_chat_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    mode VARCHAR(20) NOT NULL DEFAULT 'advice',
    title VARCHAR(255),
    messages JSONB NOT NULL DEFAULT '[]',
    tool_call_traces JSONB DEFAULT '[]',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Pending actions for Human-in-the-Loop approval
CREATE TABLE ai_pending_actions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    session_id UUID REFERENCES ai_chat_sessions(id),
    tool_name VARCHAR(100) NOT NULL,
    arguments JSONB NOT NULL,
    summary VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    result JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP
);

CREATE INDEX idx_ai_sessions_user ON ai_chat_sessions(user_id, updated_at DESC);
CREATE INDEX idx_ai_pending_user_status ON ai_pending_actions(user_id, status);
