# Library Management System

## Overview

This document outlines the design and architecture of the Library Management System. The system is built to manage library operations including book inventory, user management, and borrowing/returning of books.

## System Assumptions
- This system is intended for use by library staff and registered members.
- This is offline library management system, so person go to library and library staff process with borrow and return books. member cannot do self borrow or self return to ensure book returned in good condition.
- Library staff have administrative privileges to manage books, members, and users.
- Library staff separated to several roles:
  - Admin: Full access to all system functionalities, can add new library staff.
  - Librarian: Manage book inventory only.
  - Front Desk Staff: Handle member registrations and book checkouts/returns.
- Members can search for books, view their borrowing history, and manage their profiles.
- Members can self-register through the system.

## User Journey

### Admin Registration and Authentication
- Admin login and get JWT token. (login is pre-seeded for development simplicity)
- Admin create new library staff account with specific roles (password is inputted by admin to simplify development).
- Library Staff can login and get JWT token.

### Book Management
- Librarian logs in and get JWT token.
- Librarian adds new books to the inventory with details such as title, author, ISBN, and quantity.
- Librarian updates book information or removes books from the inventory.
- Librarian views the list of all books in the inventory.

### Member Management
- Front Desk Staff logs in and get JWT token.
- Front Desk Staff registers new members with personal details.
- Front Desk Staff updates member information or deactivates memberships.
- Front Desk Staff views the list of all registered members.

### Borrowing Books
- Front Desk Staff searches for a member and system return if this user can borrow.
- Front Desk Staff searches for books in system and system check with book record.
- Front Desk Staff checks out books to the member, updating the inventory and member's borrowing history

### Returning Books
- Front Desk Staff searches for a member and system return if this user can return.
- Front Desk Staff processes the return of books, updating the inventory and member's borrowing history.
- System checks for overdue books and applies any necessary fines to the member's account.

### Member Self-Service
- Members can register themselves through a web interface.
- Members can log in to view their borrowing history and current borrowed books.

## System Architecture
- The system is built using Java with Spring Boot framework.
- RESTful APIs are used for communication between the client and server.
- JWT (JSON Web Tokens) are used for secure authentication and authorization.
- A relational database PostgreSQL is used to store book
- Observability is implemented using Prometheus for metrics collection and Grafana for visualization.
- Swagger is used for API documentation and testing.

## Database Design

Here is table schema for this system:
- Users Table:
  - id (Primary Key) integer auto-increment
  - name 
  - email
  - password (hashed)
  - role (Admin, Librarian, Front Desk Staff, Member)
  - created_at
  - updated_at
  - is_active (boolean)
- Role Table:
  - id (Primary Key) integer auto-increment
  - name (Admin, Librarian, Front Desk Staff, Member)
  - permissions (JSON containing list of permissions)
- Members Table:
  - id (Primary Key) integer auto-increment
  - user_id (Foreign Key to Users Table)
  - created_at
  - updated_at
- Books Table:
  - id (Primary Key) integer auto-increment
  - title
  - author
  - isbn
  - total_copies
  - available_copies
  - created_at
  - updated_at
- Loans Table:
  - id (Primary Key) integer auto-increment
  - member_id (Foreign Key to Members Table)
  - book_id (Foreign Key to Books Table)
  - borrow_date
  - return_date
  - due_date
  - created_at
  - updated_at

## Security Considerations


## API Endpoints

- Authentication:
  - POST /library/auth/login: Authenticate user and return JWT token.
  - POST /library/member/register: Register a new member.
- Library Staff Management:
  - POST /library/admin/staff/create: Create a new library staff account (permission ADMIN:CREATE).
  - GET /library/admin/staff: List all library staff (permission ADMIN:READ).
  - PUT /library/admin/staff/{id}: Update library staff information (permission ADMIN:UPDATE).
  - DELETE /library/admin/staff/{id}: Deactivate library staff account (permission ADMIN:DELETE).
- Book Management:
  - POST /library/admin/books: Add a new book (permission BOOK:CREATE).
  - POST /library/admin/books/csv: Bulk insert and update multiple book (permission BOOK:CREATE).
  - GET /library/admin/books: List all books (permission BOOK:READ).
  - PUT /library/admin/books/{id}: Update book information (permission BOOK:UPDATE).
  - DELETE /library/admin/books/{id}: Remove a book (permission BOOK:DELETE).
- Loan Management:
  - POST /library/admin/loans/borrow: Borrow a book (permission BORROW:CREATE).
  - POST /library/admin/loans/return: Return a book (permission BORROW:UPDATE).
  - GET /library/admin/loans/member/{memberId}: View member's borrowing history (permission BORROW:READ).
  - GET /library/admin/loans/overdue: List all overdue loans (permission BORROW:READ).
  - GET /library/admin/loans/book/{bookId}: View all loans for a specific book (permission BORROW:READ).
- Member Management:
  - GET /library/admin/members: List all members (permission MEMBER:READ).
  - PUT /library/admin/members/{id}: Update member information (permission MEMBER:UPDATE).
  - DELETE /library/members/{id}: Deactivate member account (permission MEMBER:DELETE).
- Member Self-Service:
  - GET /library/member/me/loans: View own borrowing history (permission MEMBER:READ).
  - GET /library/member/me/books: View currently borrowed books (permission MEMBER:READ).
- Public Endpoints:
  - GET /library/public/books: Search and view available books (no authentication required).

## Notable Case Handling

- Prevent double borrowing of the same book. This can heppen if borrowing request is called at same time and implementation have query first and then update data.
To prevent this, use database transaction with "SELECT ... FOR UPDATE" to lock the book record during the borrowing process.
- Handle double sending request from client side when front desk staff click borrow or return button multiple times.
Implement idempotency by generating a unique request ID for each borrow/return operation and storing it

## Observability
- Metric Collection:
  - Prometheus is used to collect metrics such as request counts, response times, error rates, and system resource usage.
  - Use Grafana Cloud for visualization of metrics and creating dashboards.
- Logging:
  - Implement structured logging using a logging framework (e.g., Logback or Log4j).
  - Logs include request details, user actions, errors, and system events.
  - Logs is sent to Grafana Loki for centralized log management and analysis.
- Tracing:
  - Use OpenTelemetry for distributed tracing to monitor request flows and identify performance bottlenecks.
  - Integrate tracing data with Grafana for visualization and analysis.

## Deployment Considerations
- Containerization:
  - Use Docker to containerize the application for consistent deployment across different environments.
- Orchestration:
  - Use Docker Compose for local development and testing. This can also simulate multiple replicas of the service.
