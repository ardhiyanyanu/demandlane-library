# Loan Integration Test Documentation

## Overview

`LoanIntegrationTest` is a comprehensive end-to-end integration test that validates the complete library loan workflow including book management, member registration, loan operations, and return processes.

## Test Scenarios Covered

### 1. Librarian Adds Books (Single Endpoint)
- **Action**: Librarian logs in and adds 4 books individually
- **Books Added**:
  - The Great Gatsby (5 copies)
  - 1984 (3 copies)
  - To Kill a Mockingbird (4 copies)
  - Pride and Prejudice (2 copies)
- **Validation**: Each book creation returns a valid book ID

### 2. Librarian Uploads Books via CSV
- **Action**: Librarian uploads a CSV file with bulk book data
- **Books Added**:
  - The Catcher in the Rye (3 copies)
  - Brave New World (4 copies)
- **Validation**: Import confirms books were added successfully

### 3. Front Desk Registers First Member
- **Action**: Front desk staff logs in and registers "John Doe"
- **Member Details**:
  - Email: john.doe@test.com
  - Address: 123 Main St
  - Phone: 555-1001
- **Validation**: Registration returns valid member ID

### 4. Member Exceeds Maximum Loan Limit (Error Expected)
- **Action**: Member1 attempts to loan 4 books (exceeds limit of 3)
- **Expected**: HTTP 4xx error
- **Error Message**: "Cannot loan more than 3 books at once"
- **Validates**: Maximum borrowing limit enforcement

### 5. Member Loans Maximum Allowed Books
- **Action**: Member1 successfully loans exactly 3 books
- **Books Loaned**: The Great Gatsby, 1984, To Kill a Mockingbird
- **Validation**: Loan IDs are returned for all 3 books

### 6. Member Tries to Loan While Having Active Loans (Error Expected)
- **Action**: Member1 attempts to loan another book (Pride and Prejudice)
- **Expected**: HTTP 4xx error
- **Error Message**: "Member already has active loan for book"
- **Validates**: Duplicate loan prevention

### 7. Front Desk Registers Second Member
- **Action**: Front desk registers "Jane Smith"
- **Member Details**:
  - Email: jane.smith@test.com
  - Address: 456 Oak Ave
  - Phone: 555-1002
- **Validation**: Registration returns valid member ID

### 8. Member Tries to Loan Unavailable Book (Error Expected)
- **Setup**: Librarian updates CSV Book 1 to have only 1 copy
- **Action 1**: Member2 loans the last copy successfully
- **Action 2**: Member2 tries to loan the same book again
- **Expected**: HTTP 4xx error
- **Error Message**: "not available"
- **Validates**: Book availability checking

### 9. First Member Returns Some Books
- **Action**: Member1 returns 2 out of 3 loaned books
- **Books Returned**: The Great Gatsby, 1984
- **Books Still Loaned**: To Kill a Mockingbird (1 book remaining)
- **Validation**: Return dates are set for returned books

### 10. First Member Loans New Book After Partial Return
- **Action**: Member1 loans Pride and Prejudice (now has 2 active loans: 1 old + 1 new)
- **Validation**: Loan succeeds since member is under the limit (2 < 3)
- **Validates**: Partial returns allow new loans

### 11. Second Member Borrows Previously Returned Book
- **Action**: Member2 loans The Great Gatsby (previously returned by Member1)
- **Validation**: Loan succeeds
- **Validates**: Returned books become available for other members

### 12. First Member Tries to Return Already Returned Book (Error Expected)
- **Action**: Member1 attempts to return The Great Gatsby again
- **Expected**: HTTP 4xx error
- **Error Message**: "already returned"
- **Validates**: Double-return prevention

## Test Configuration

### Containers
- **PostgreSQL**: Version 17-alpine (database)
- **Redis**: Version 8-alpine (distributed locking & caching)

### Library Settings
- **Max Books Per Member**: 3
- **Loan Period**: 14 days

### Test Users
- **Librarian**: librarian@library.local
  - Permissions: Create books, upload CSV, update books
- **Front Desk**: frontdesk@library.local
  - Permissions: Register members, manage loans/returns

## Test Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│  1. Librarian Login                                          │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  2. Add Books (Single) → 4 books                             │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  3. Upload Books (CSV) → 2 books                             │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  4. Front Desk Login                                         │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  5. Register Member1 (John Doe)                              │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  6. Member1 loans 4 books → ERROR (exceeds max)             │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  7. Member1 loans 3 books → SUCCESS                          │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  8. Member1 tries to loan again → ERROR (has active loans)  │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  9. Register Member2 (Jane Smith)                            │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  10. Update book to 1 copy, Member2 loans last copy         │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  11. Member2 tries same book → ERROR (not available)        │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  12. Member1 returns 2 books (keeps 1)                       │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  13. Member1 loans new book → SUCCESS (2 total < 3 max)     │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  14. Member2 loans returned book → SUCCESS                   │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       v
┌─────────────────────────────────────────────────────────────┐
│  15. Member1 returns already returned book → ERROR          │
└─────────────────────────────────────────────────────────────┘
```

## Key Validations

### Business Rules Tested
1. ✅ Maximum loan limit enforcement (3 books)
2. ✅ Duplicate loan prevention (same member, same book)
3. ✅ Book availability checking (0 copies available)
4. ✅ Partial return support (return some, keep some)
5. ✅ Active loan tracking (prevents re-loan while active)
6. ✅ Return validation (cannot return already returned book)
7. ✅ Book reuse (returned books available to others)

### Technical Features Tested
1. ✅ JWT authentication and authorization
2. ✅ Role-based access control (Librarian vs Front Desk)
3. ✅ Database transactions (PostgreSQL)
4. ✅ Distributed locking (Redis)
5. ✅ CSV bulk import
6. ✅ REST API endpoints
7. ✅ Error handling and messages

## Running the Test

```bash
# Run the specific test
mvn test -Dtest=LoanIntegrationTest#testCompleteLibraryLoanWorkflow

# Run all integration tests
mvn test -Dtest=*IntegrationTest

# Run with debug output
mvn test -Dtest=LoanIntegrationTest -X
```

## Expected Output

```
✅ All integration test scenarios passed successfully!

Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

## Helper Methods

The test includes several reusable helper methods:

- `loginAs(email, password)` - Authenticate and get JWT token
- `addBookViaEndpoint()` - Add single book via REST API
- `uploadBooksViaCsv()` - Bulk upload books from CSV
- `getBookIdByIsbn()` - Retrieve book ID by ISBN
- `registerMember()` - Register new library member
- `tryLoanBooksExpectError()` - Attempt loan expecting failure
- `loanBooksSuccessfully()` - Perform successful loan
- `updateBookCopies()` - Update book inventory
- `returnBooksSuccessfully()` - Return books
- `tryReturnBooksExpectError()` - Attempt return expecting failure

## Troubleshooting

### Test Fails at Login
- Ensure database migration scripts have seeded default users
- Check librarian and frontdesk credentials in migration file

### Test Fails at Book Creation
- Verify Librarian has BOOK:CREATE permission
- Check database constraints on books table

### Test Fails at Loan
- Verify Front Desk has BORROW:UPDATE permission
- Check Redis is running and accessible
- Verify distributed locking is working

### Test Fails at Return
- Check loan records exist in database
- Verify return logic in LoanService

## Notes

- Test uses Testcontainers for PostgreSQL and Redis
- Each test run starts fresh containers
- Data is not persisted between test runs
- Test validates both success and failure scenarios
- All REST API responses are printed for debugging (`andDo(print())`)

---

**Status**: ✅ Complete and comprehensive
**Coverage**: Business rules, technical features, edge cases
**Maintenance**: Update if business rules change (e.g., max books limit)

