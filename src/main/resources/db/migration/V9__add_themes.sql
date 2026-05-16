CREATE TABLE themes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    colors_json TEXT NOT NULL,
    is_system BOOLEAN NOT NULL DEFAULT false,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE users ADD COLUMN active_theme_id UUID REFERENCES themes(id);

INSERT INTO themes (name, colors_json, is_system) VALUES
('Dark', '{"bg":"#0f172a","bg-card":"#1e293b","bg-card-hover":"#1e293b","border":"#334155","text":"#f1f5f9","text-muted":"#94a3b8","text-secondary":"#64748b","primary":"#3b82f6","primary-hover":"#2563eb","green":"#22c55e","red":"#ef4444","amber":"#f59e0b","sidebar-bg":"#1e293b","sidebar-border":"#334155","hover-bg":"rgba(255,255,255,0.05)","input-bg":"#334155","input-border":"#475569"}', true),
('Light', '{"bg":"#f1f5f9","bg-card":"#ffffff","bg-card-hover":"#f8fafc","border":"#e2e8f0","text":"#0f172a","text-muted":"#64748b","text-secondary":"#475569","primary":"#3b82f6","primary-hover":"#2563eb","green":"#16a34a","red":"#dc2626","amber":"#d97706","sidebar-bg":"#ffffff","sidebar-border":"#e2e8f0","hover-bg":"rgba(0,0,0,0.04)","input-bg":"#f1f5f9","input-border":"#cbd5e1"}', true);
