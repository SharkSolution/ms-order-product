# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Run Commands

```bash
# Run locally
./gradlew bootRun

# Build JAR
./gradlew clean build

# Build without tests
./gradlew clean bootJar -x test

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.suresell.orders.application.usecase.OrderHandlerTest"
```

**Application runs on port 8081**
- Swagger UI: `http://localhost:8081/swagger-ui.html`
- API docs: `http://localhost:8081/v3/api-docs`

## Architecture Overview

This is a **Spring Boot 3.4.1 microservice** (Java 17) for restaurant order management using **Hexagonal Architecture** (Ports & Adapters) with **Local-First Synchronization**.

### Package Structure

```
com.suresell.orders/
├── application/          # Use cases & DTOs
│   ├── dto/             # Request/Response objects
│   └── usecase/         # Business logic implementations
├── domain/              # Core business logic
│   ├── model/           # JPA entities
│   ├── port/in/         # Input port interfaces (OrderPort, DiscountPort, etc.)
│   ├── port/out/        # Output port interfaces (repositories, cloud sync)
│   └── service/         # Domain service implementations
├── infrastructure/      # Technical adapters
│   ├── config/          # Spring configurations (DataSource, Scheduler)
│   ├── persistence/     # Repository adapters implementing output ports
│   └── web/             # REST controllers
└── shared/              # Exceptions, enums, utilities
```

### Key Patterns

**Port/Adapter Pattern**: Controllers call input ports (e.g., `OrderPort`) → implemented by handlers (e.g., `OrderHandler`) → which use output ports (repository interfaces) → implemented by adapters (e.g., `OrderRepositoryAdapter`).

**Outbox Pattern for Sync**: All domain changes (orders, closures, discounts) queue events to `sync_outbox` table. `SyncOutboxScheduler` processes pending events every 5 seconds, with exponential backoff retry on failures. This enables offline-first operation.

**Dual Database**: SQLite for local persistence (single connection), PostgreSQL (Supabase) for cloud sync.

### Core Domain Models

- **Order**: Main aggregate with `OrderItem` children and `OrderDeliveryTracking` for delivery state
- **Pager System**: 16 pagers per color (AMARILLO, AZUL). Freed when `delivered=true` OR `pagerReturned=true`
- **DiscountCoupon**: Percentage discounts with date/weekday restrictions, linked via `CouponProduct`
- **DailyClosure**: Cash reconciliation with payment breakdown and shortage detection
- **SyncOutbox**: Event queue with status (PENDING, IN_PROGRESS, SYNCED, FAILED)

### Main Entry Points

| Feature | Controller | Use Case/Handler |
|---------|------------|------------------|
| Orders | `OrderController` | `OrderHandler` |
| Discounts | `DiscountController` | `DiscountHandler` |
| Menu Catalog | `MenuCatalogController` | `MenuCatalogHandler` |
| Daily Closure | `DailyClosureController` | `ExecuteDailyClosureUseCase` |

### Scheduled Tasks

- `SyncOutboxScheduler`: Syncs pending outbox events to cloud (5s interval)
- `OrderTrackingSyncScheduler`: Polls cloud for delivery updates (7s interval)

## Configuration

Main config: `src/main/resources/application.yml`

Key properties:
- `sync.cloud.enabled`: Enable/disable cloud sync
- `coupon.admin.password`: Admin password for discount operations
- `sync.scheduler.fixed-delay-ms`: Outbox sync interval
