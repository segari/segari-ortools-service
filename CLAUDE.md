# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot microservice that provides route optimization APIs using Google OR-Tools. It solves Vehicle Routing Problems (VRP) and Traveling Salesman Problems (TSP) for last-mile delivery optimization in the Segari logistics ecosystem.

**Tech Stack:**
- Java 25
- Spring Boot 3.5.6
- Google OR-Tools 9.8.3296
- Maven 3.9.6
- Java Records (all DTOs)
- RestClient for HTTP calls
- Virtual Threads enabled

## Common Development Commands

### Build and Run
```bash
# Build the project
./mvnw clean install

# Skip tests during build
./mvnw clean install -Dmaven.test.skip=true

# Run the application locally
./mvnw spring-boot:run

# Run with custom port
SERVER_PORT=8081 ./mvnw spring-boot:run
```

### Testing
```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=SegariRouteTest

# Run specific test method
./mvnw test -Dtest=SegariRouteTest#route
```

### Docker
```bash
# Build Docker image
docker build -t segari-ortools .

# Run Docker container
docker run -p 8080:8080 segari-ortools
```

## Architecture Overview

### Layer Structure
```
Controller Layer (REST APIs)
    ↓
Service Layer (Business orchestration)
    ↓
Core Algorithm Layer (OR-Tools integration)
    ↓
Utility Layer (GeoUtils, OrToolsLoader)
```

### Key Components

**1. RouteController** (`src/main/java/id/segari/ortools/controller/RouteController.java`)
- Exposes three REST endpoints for different routing problems
- Uses Jakarta Bean Validation with validation groups for endpoint-specific constraints

**2. RouteService/RouteServiceImpl** (`src/main/java/id/segari/ortools/service/`)
- Orchestrates routing operations
- Delegates to SegariRoute for optimization logic

**3. SegariRoute** (`src/main/java/id/segari/ortools/ortool/SegariRoute.java`)
- Core routing engine integrating Google OR-Tools
- Implements Builder pattern for constraint configuration
- Factory methods for different routing problem types:
  - `newVrpStartFromSpAndArbitraryFinish()` - Multi-vehicle from depot
  - `newVrpWithArbitraryStartAndFinish()` - Multi-vehicle flexible start
  - `newTspWithStartAndFinish()` - Single vehicle routing

**4. Validation Groups** (`src/main/java/id/segari/ortools/validation/group/`)
- Different validation rules per endpoint:
  - `VrpSpStartArbitraryFinish` - Strictest validation (all fields required)
  - `VrpArbitraryStartArbitraryFinish` - Moderate validation
  - `TspFixStartArbitraryFinish` - Flexible validation (optional fields)

## Routing Problem Types

### 1. VRP with SP Start and Arbitrary Finish
**Endpoint:** `POST /v1/routes/vrp/sp-start/arbitrary-finish`

Multiple vehicles start from a Service Point (SP/depot) and can end anywhere. Supports complex constraints including:
- Distance limits per vehicle
- Order count limits
- Max Instan/Turbo order counts
- Extension distribution balancing via ratio dimension

### 2. VRP with Arbitrary Start and Arbitrary Finish
**Endpoint:** `POST /v1/routes/vrp/arbitrary-start/arbitrary-finish`

Vehicles can start and end at any location. Simpler constraint model primarily based on total distance.

### 3. TSP with Fixed Start and Arbitrary Finish
**Endpoint:** `POST /v1/routes/tsp/fix-start/{index}/arbitrary-finish`

Single vehicle routing starting from a specific location index. Supports optional constraints for distance and order types.

## Special Node Types

The service uses a fixed indexing scheme:
- **Index 0:** DUMMY node (id=-1) - represents arbitrary start/end points
- **Index 1:** SP node (id=-2) - service point/depot
- **Index 2+:** Regular ORDER nodes (actual deliveries)

Order types:
- `ORDER` - Regular delivery
- `INSTAN` - Instant delivery (separate capacity constraint)
- `TURBO` - Express delivery (separate capacity constraint)
- `EXTENSION` - Extension orders (used for balanced distribution)

## Constraint Configuration

### Distance Constraints (Mutually Exclusive)
```java
// Max distance between consecutive orders (excludes SP/Dummy)
.addDistanceBetweenOrderDimension(5000)

// Max distance from Service Point (for SP-related routes)
.addDistanceWithSpDimension(15000)

// Max distance between any consecutive nodes (all types)
.addDistanceBetweenNodeDimension(8000)
```

### Capacity Constraints
```java
// Total orders per vehicle
maxOrderCount: 10

// Instan orders per vehicle
.addMaxInstanOrderCountDimension(3)

// Turbo orders per vehicle
.addMaxTurboOrderCountDimension(5)
```

### Extension Distribution
```java
// Enable weighted distribution of extension orders
.addExtensionTurboInstanRatioDimension(extensionCount)
// Extensions weighted at 1, regular orders at 100
// Forces ~equal extension distribution per vehicle
```

## OR-Tools Configuration

**Solver Settings:**
- Strategy: `PATH_CHEAPEST_ARC` (greedy heuristic)
- Time limit: 60 seconds
- Disjunction penalty: 100,000 (allows dropping orders with high penalty)

**Distance Calculation:**
- Haversine formula for great-circle distance
- Earth radius: 6371 km
- Returns meters for precision

## Key Patterns

### Builder/Fluent API Pattern
```java
SegariRoute.newVrpStartFromSpAndArbitraryFinish(dto)
    .addDistanceBetweenOrderDimension(5000)
    .addMaxInstanOrderCountDimension(3)
    .setResultMinimum(4)
    .setResultMustContainExtension()
    .route();
```

### Result Filtering
- `setResultMinimum(n)` - Only return routes with ≥n orders
- `setResultMustContainExtension()` - Only return routes containing extension orders
- Post-processing excludes DUMMY/SP nodes from results

### Two-Pass Optimization Pattern
The service supports running optimization twice:
1. First run: Determine optimal vehicle count
2. Second run: Use `alterVehicleNumbers()` to adjust vehicles and re-optimize with extension distribution

## Important Implementation Details

### Dynamic Vehicle Calculation
For VRP problems, vehicle count is automatically calculated:
```java
Math.ceilDiv(orderCount, maxOrderCount)
```
Can be overridden using `alterVehicleNumberValue` in the request.

### Distance Matrix Pre-computation
All pairwise distances are calculated upfront using Haversine formula and stored in a `long[][]` matrix. Distance constraints are encoded by setting prohibitive distances (`maxTotalDistance + 1`) in the matrix.

### Native Library Loading
OR-Tools requires native libraries. The `OrToolsLoader` class handles:
- Extracting native libraries from Spring Boot fat JAR
- Linux-specific JAR unpacking workaround
- Static initialization in `SegariRoute` ensures libraries are loaded before use

### Extension Balancing Logic
When `isUsingRatioDimension=true`:
- Extensions weighted at 1, regular orders at 100
- Vehicle count set to `extensionCount`
- Capacity calculated to force equal extension distribution
- Results filtered to require extensions and minimum 4 orders

## OSRM Integration

The service integrates with Open Source Routing Machine (OSRM) for real-world distance/duration calculations.

### Configuration
- **Local**: `application-local.properties` sets `osrm.base-url=http://localhost:5000`
- **Production**: `application-production.properties` (configure with actual OSRM server URL)
- Profile switching: Use `-Dspring.profiles.active=local` or `=production`

### OSRMRestService
Located in `external` package, provides distance matrix calculation:

```java
OSRMTableResponseDTO getDistanceMatrix(List<LatLong> locations)
```

**Implementation details:**
- Uses Spring's modern **RestClient** (not RestTemplate)
- Calls OSRM Table API: `/table/v1/driving/{coordinates}?annotations=duration,distance`
- Coordinates format: `longitude,latitude` (note: longitude first!)
- Converts API response from `double[][]` to `long[][]` using `Math.round()`
- Works seamlessly with **Virtual Threads** for high concurrency

**Virtual Threads:**
- Enabled via `spring.threads.virtual.enabled=true`
- RestClient blocking calls become cheap (millions of threads possible)
- No need for reactive programming complexity

### Usage Example
```java
List<LatLong> locations = List.of(
    new LatLong(-6.1751, 106.8272),
    new LatLong(-6.1349, 106.8133),
    new LatLong(-6.1927, 106.8215)
);
OSRMTableResponseDTO response = osrmRestService.getDistanceMatrix(locations);
long[][] distances = response.distances(); // in meters
long[][] durations = response.durations(); // in seconds
```

## Error Handling

All errors are wrapped in `ResponseDTO`:
- Validation errors → 400 Bad Request
- Business logic errors → 500 Internal Server Error
- Custom exceptions extend `BaseException` with HTTP status codes
- Centralized handling via `@RestControllerAdvice` in `ExceptionsHandler`

## Deployment

The service is deployed to AWS ECS using `deploy-ortools.sh`:
- Builds Docker image and pushes to ECR
- Updates ECS task definition with new image
- Forces new deployment in ECS cluster
- Supports multiple environments via `CLUSTER_NAME` variable

Configuration:
- Default port: 8080 (configurable via `SERVER_PORT` env variable)
- Health check endpoint: Provided by Spring Boot Actuator (if enabled)

## Testing Strategy

Test stubs exist in `SegariRouteTest` for all major methods. When implementing tests:
1. Use sample coordinate data for realistic route scenarios
2. Test constraint violations (e.g., exceeding distance limits)
3. Verify result filtering logic (minimum orders, extension presence)
4. Test edge cases (single order, no feasible solution)
5. Validate distance matrix calculations against known coordinates