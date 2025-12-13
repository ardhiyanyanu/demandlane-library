# Library Management System

This project is library management service. This service provides APIs to manage library operations including bookEntity inventory, user management, and borrowing/returning of bookEntities.
Full design document can be found [here](DESIGN.md).

## How to Run the Service

### Prerequisites

- Java 17 or higher
- Maven 3.6+ (for building from source)
- Docker and Docker Compose (for containerized deployment)

### Option 1: Local Development

1. **Prerequisites**:
   - PostgreSQL 12+ running and accessible
   - Redis 6+ running and accessible
   - Set environment variables (or use defaults):
     ```bash
     # Database
     export DATABASE_URL=jdbc:postgresql://localhost:5432/library_db
     export DATABASE_USER=postgres
     export DATABASE_PASSWORD=postgres
     
     # Redis
     export REDIS_HOST=localhost
     export REDIS_PORT=6379
     export REDIS_PASSWORD=
     export REDIS_TIMEOUT=2000ms
     export REDIS_DATABASE=0
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
   - Start Redis cache server
   - Build and run the Library Management Service (3 replicas)
   - Start OpenTelemetry Collector for observability
   - Flyway will automatically run migrations on startup

2. **Access the service**:
   ```
   http://localhost:8080
   http://localhost:8081
   http://localhost:8082
   ```

## Infrastructure Services

### PostgreSQL Database
- **Version**: 17-alpine
- **Port**: 5432
- **Database**: library_db
- **User**: postgres
- **Purpose**: Primary data store for all library data

### Redis Cache
- **Version**: 8-alpine
- **Port**: 6379
- **Purpose**: 
  - Distributed locking for concurrent book loans
  - Caching loan/return request results
  - Session management
- **Persistence**: Append-only file (AOF) enabled
- **Data Volume**: Persisted in `redis_data` volume

### Services Architecture
```
┌─────────────────────────────────────────────────┐
│  Load Balancer (Ports 8080-8082)                │
└─────────────────┬───────────────────────────────┘
                  │
        ┌─────────┴─────────┬─────────────┐
        │                   │             │
        v                   v             v
    ┌───────┐          ┌───────┐     ┌───────┐
    │ App 1 │          │ App 2 │     │ App 3 │
    └───┬───┘          └───┬───┘     └───┬───┘
        │                  │             │
        └──────────┬───────┴─────────────┘
                   │
        ┌──────────┴──────────┐
        │                     │
        v                     v
   ┌──────────┐         ┌────────┐
   │PostgreSQL│         │ Redis  │
   └──────────┘         └────────┘
```

## Database Setup

The application uses **Flyway** for database migrations. Migrations are automatically executed on application startup.

### Migration Scripts
Located in `src/main/resources/db/migration/`:
- `V1__Create_initial_tables.sql` - Creates database schema
- `V2__Insert_initial_roles_and_users.sql` - Seeds roles


### Manual Migration Commands
```bash
# Run migrations
mvn flyway:migrate

# Repair migration history (if needed)
mvn flyway:repair

# Clean database (caution: deletes all data)
mvn flyway:clean
```

## Redis Configuration

Redis is used for distributed locking and caching to prevent race conditions in concurrent operations.

### Configuration Properties

```yaml
spring:
  redis:
    host: localhost        # Redis server host
    port: 6379            # Redis server port
    password:             # Redis password (empty for local dev)
    timeout: 2000ms       # Connection timeout
    database: 0           # Redis database number
    lettuce:
      pool:
        max-active: 8     # Maximum active connections
        max-idle: 8       # Maximum idle connections
        min-idle: 0       # Minimum idle connections
        max-wait: -1ms    # Max wait time for connection
```

### Use Cases

1. **Distributed Locking**:
   - Prevents race conditions when multiple users loan the same book
   - Ensures data consistency across multiple application instances
   - Lock timeout: 30 seconds (configurable in `RedisLockUtil`)

2. **Request Caching**:
   - Caches loan/return operation results
   - TTL: 1 hour per request
   - Supports async request-response pattern

3. **Concurrency Control**:
   - Member-level locks prevent duplicate loans
   - Book-level locks (database) prevent overselling
   - Combined strategy ensures complete data integrity

### Monitoring Redis

```bash
# Connect to Redis CLI
docker exec -it library-redis redis-cli

# Check connection
PING

# Monitor real-time commands
MONITOR

# View all keys
KEYS *

# Check active locks
KEYS "loan:lock:*"

# View cached requests
KEYS "loan:request:*"
KEYS "return:request:*"
```

### Redis Persistence

- **AOF (Append-Only File)**: Enabled for data durability
- **Data Volume**: `redis_data` volume persists data across container restarts
- **Backup**: Volume is stored in Docker's volume directory

## API Documentation

API documentation is available via Swagger UI at:
```
http://localhost:8080/swagger.html
```
