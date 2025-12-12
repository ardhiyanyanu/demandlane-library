# Library Management System

This project is library management service. This service provides APIs to manage library operations including book inventory, user management, and borrowing/returning of books.
Full design document can be found [here](DESIGN.md).

## How to Run the Service

### Prerequisites

- Java 17 or higher
- Maven 3.6+ (for building from source)
- Docker and Docker Compose (for containerized deployment)

### Option 1: Local Development

1. **Prerequisites**:
   - PostgreSQL 12+ running and accessible
   - Set database environment variables (or use defaults):
     ```bash
     export DATABASE_URL=jdbc:postgresql://localhost:5432/library_db
     export DATABASE_USER=postgres
     export DATABASE_PASSWORD=postgres
     ```

2. **Build and Run**:
   ```bash
   # Build the application
   mvn clean package -DskipTests
   
   # Run the application (migrations will run automatically on startup)
   java -jar target/library-1.0.0.jar
   ```

   The application will:
   - Automatically create the database schema (if it doesn't exist)
   - Run all pending migrations from `src/main/resources/db/migration/`
   - Seed initial roles and users
   - Start the service on `http://localhost:8080`

3. **Default Credentials** (Development Only):
   - Admin: `admin@library.local` / `admin123`
   - Librarian: `librarian@library.local` / `librarian123`
   - Front Desk Staff: `frontdesk@library.local` / `frontdesk123`

### Option 2: Docker Compose

1. **Start the services**:
   ```bash
   docker-compose up --build
   ```

   This will:
   - Start PostgreSQL database
   - Build and run the Library Management Service
   - Flyway will automatically run migrations on startup

2. **Access the service**:
   ```
   http://localhost:8080
   ```

## Database Setup

The application uses **Flyway** for database migrations. Migrations are automatically executed on application startup.

### Migration Scripts
Located in `src/main/resources/db/migration/`:
- `V1__Create_initial_tables.sql` - Creates database schema
- `V2__Insert_initial_roles_and_users.sql` - Seeds roles and users
- `V3__Insert_sample_books.sql` - Populates sample books

For detailed migration documentation, see [Database Migrations](src/main/resources/db/migration/README.md)

### Manual Migration Commands
```bash
# Run migrations
mvn flyway:migrate

# Repair migration history (if needed)
mvn flyway:repair

# Clean database (caution: deletes all data)
mvn flyway:clean
```

## API Documentation

API documentation is available via Swagger UI at:
```
http://localhost:8080/swagger.html
```
