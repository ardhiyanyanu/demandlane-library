-- Insert initial roles with permissions based on DESIGN.md

-- Admin Role: Full system access
INSERT INTO roles (name, permissions) VALUES
    ('ADMIN', '["ADMIN:CREATE", "ADMIN:READ", "ADMIN:UPDATE", "ADMIN:DELETE", "BOOK:CREATE", "BOOK:READ", "BOOK:UPDATE", "BOOK:DELETE", "BORROW:READ", "BORROW:UPDATE", "BORROW:DELETE", "MEMBER:READ", "MEMBER:UPDATE"]'::jsonb)
ON CONFLICT (name) DO NOTHING;

-- Librarian Role: Book management only
INSERT INTO roles (name, permissions) VALUES
    ('LIBRARIAN', '["BOOK:CREATE", "BOOK:READ", "BOOK:UPDATE", "BOOK:DELETE"]'::jsonb)
ON CONFLICT (name) DO NOTHING;

-- Front Desk Staff Role: Borrow/return and member management
INSERT INTO roles (name, permissions) VALUES
    ('FRONT_DESK_STAFF', '["BORROW:READ", "BORROW:UPDATE", "BORROW:DELETE", "MEMBER:READ", "MEMBER:UPDATE"]'::jsonb)
ON CONFLICT (name) DO NOTHING;

-- Member Role: Read-only member information
INSERT INTO roles (name, permissions) VALUES
    ('MEMBER', '["MEMBER:READ"]'::jsonb)
ON CONFLICT (name) DO NOTHING;

-- Insert default admin user (password: admin123 - bcrypt hashed)
-- Note: This is a development user and should be changed in production
INSERT INTO users (name, email, password, role_id, is_active)
SELECT 'Administrator', 'admin@library.local', '$2a$10$slYQmyNdGzin7olVN3p5Be7DlH.PKZbv5H8KnzzVgXXbVxzy.U5dO', id, true
FROM roles WHERE name = 'ADMIN'
ON CONFLICT (email) DO NOTHING;

-- Insert a sample librarian user (password: librarian123)
INSERT INTO users (name, email, password, role_id, is_active)
SELECT 'Sample Librarian', 'librarian@library.local', '$2a$10$Vr7eZBX8CZRccNk3W1Xs4OfcsxE0dBwCxCNMh6lNDBZfjEfhkdFNi', id, true
FROM roles WHERE name = 'LIBRARIAN'
ON CONFLICT (email) DO NOTHING;

-- Insert a sample front desk staff user (password: frontdesk123)
INSERT INTO users (name, email, password, role_id, is_active)
SELECT 'Sample Front Desk Staff', 'frontdesk@library.local', '$2a$10$dGSJZWf5JdmCvEt8jDkVNebLDNVN0VmPvWtPZBEhqVDZJx8M3RLjm', id, true
FROM roles WHERE name = 'FRONT_DESK_STAFF'
ON CONFLICT (email) DO NOTHING;

