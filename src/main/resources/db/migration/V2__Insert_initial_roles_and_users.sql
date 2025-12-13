-- Insert initial roles with permissions based on DESIGN.md

-- Admin Role: Full system access
INSERT INTO roles (name, permissions) VALUES
    ('ADMIN', '["ADMIN:CREATE","ADMIN:READ","ADMIN:UPDATE","ADMIN:DELETE","BOOK:CREATE","BOOK:READ","BOOK:UPDATE","BOOK:DELETE","BORROW:READ","BORROW:UPDATE","BORROW:DELETE","MEMBER:READ","MEMBER:UPDATE"]'::jsonb)
ON CONFLICT (name) DO NOTHING;

-- Librarian Role: Book management only
INSERT INTO roles (name, permissions) VALUES
    ('LIBRARIAN', '["BOOK:CREATE","BOOK:READ","BOOK:UPDATE","BOOK:DELETE"]'::jsonb)
ON CONFLICT (name) DO NOTHING;

-- Front Desk Staff Role: Borrow/return and member management
INSERT INTO roles (name, permissions) VALUES
    ('FRONT_DESK_STAFF', '["BORROW:CREATE","BORROW:READ","BORROW:UPDATE","BORROW:DELETE","MEMBER:READ","MEMBER:UPDATE","MEMBER:CREATE"]'::jsonb)
ON CONFLICT (name) DO NOTHING;

-- Member Role: Read-only member information
INSERT INTO roles (name, permissions) VALUES
    ('MEMBER', '["MEMBER:READ"]'::jsonb)
ON CONFLICT (name) DO NOTHING;

-- Note: Default users (admin, librarian, frontdesk) are now seeded by Spring Boot's UserSeeder component
-- This ensures passwords are properly encoded using the application's PasswordEncoder

