# Data API Endpoint Generator

A developer tool that turns CSV files into queryable PostgreSQL tables and exposes a generic REST API for filtering, sorting, and retrieving the data.

## Features

* Upload and preview CSV datasets
* Infer basic column types with optional type overrides
* Perform lightweight pre-database validation
* Create a PostgreSQL table for each dataset
* Query datasets through a generic REST API
* Support filtering, comparison operators, sorting, and result limits
* Retrieve schema information directly from PostgreSQL
* Temporarily store uploaded CSVs during the preview/create flow
* Roll back database changes when dataset creation fails

## Architecture

```text
CSV
 │
 ▼
Preview / Type Inference
 │
 ▼
Temporary CSV Storage
 │
 ▼
User Review + Type Overrides
 │
 ▼
PostgreSQL Table Creation
 │
 ▼
Generic Query API
```

The application uses PostgreSQL as the source of truth for generated dataset schemas. It performs basic validation before database operations but leaves database-level validation to PostgreSQL.

## Tech Stack

* **Backend:** Java, Spring Boot, Gradle
* **Frontend:** React, TypeScript
* **Database:** PostgreSQL
* **Serialization:** Jackson

## Project Structure

```text
src/
├── main/
│   ├── java/
│   │   └── com/example/dataserv/
│   │       ├── application/
│   │       ├── domain/
│   │       └── infrastructure/
│   └── resources/
└── test/
```

## Running Locally

### Prerequisites

* Java 25
* Gradle
* PostgreSQL
* Node.js and npm

### 1. Clone the repository

```bash
git clone https://github.com/ellayipinghou/data-api-endpoint-generator.git
cd data-api-endpoint-generator
```

### 2. Set up PostgreSQL

Create a PostgreSQL database named `dataserv`:

```sql
CREATE DATABASE dataserv;
```

Connect to the `dataserv` database and create the `datasets` metadata table:

```sql
CREATE TABLE datasets (
    id UUID NOT NULL,
    name TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

### 3. Configure database environment variables

The application reads its PostgreSQL connection details from environment variables.

Set the following variables using your system's environment variable configuration:

```text
DB_URL=jdbc:postgresql://localhost:5432/dataserv
DB_USERNAME=postgres
DB_PASSWORD=your-password
```

The backend uses these variables in `application.properties`:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
```

Do not commit database credentials to the repository.

### 4. Start the backend

```bash
./gradlew bootRun
```

On Windows:

```powershell
.\gradlew.bat bootRun
```

### 5. Start the frontend

From the frontend directory:

```bash
npm install
npm run dev
```

## Dataset Workflow

1. Upload a CSV.
2. The backend parses the file and infers column types.
3. The frontend displays the schema, preview rows, and validation issues.
4. The uploaded CSV is temporarily stored with a `previewId`.
5. The user can adjust inferred types.
6. The backend creates a PostgreSQL table and loads the data.
7. The temporary preview file is deleted.
8. The dataset can be queried through the API.

Previews expire after **30 minutes**. If a preview is missing or expired when creation is attempted, the user must upload the CSV again.

## Using the Application

Each dataset has three tabs:

### Overview

The Overview tab displays the dataset's schema, including column names, data types, and supported query operators. It also provides general information about the dataset.

### API

The API tab provides the dataset's base query endpoint and information needed to access it programmatically.

Datasets are queried through a generic endpoint:

```text
GET /datasets/{id}/query
```

For example:

```text
/datasets/123/query
```

The available columns and operators are based on the actual PostgreSQL table schema.

The endpoint can be used directly by another application or API client to retrieve data.

### Query

The Query tab provides an interactive way to build and test API queries. Users can select filters, comparison operators, sorting, and result limits, then execute the query to see the matching rows directly in the application.

The tab also generates the corresponding API endpoint for the selected query. This allows users to go from:

```text
Select query parameters
        ↓
Preview the results
        ↓
Get the corresponding API endpoint
```

For example, a query configured with an age greater than 30, a score greater than or equal to 90, an active status, and results sorted by score produces:

```text
/datasets/123/query?age_gt=30&score_gte=90&active=true&sort=score,desc&limit=5
```

The generated endpoint can then be used directly by another application or API client to retrieve the same results.

## Database Design

Each uploaded dataset receives its own PostgreSQL table:

```text
datasets
├── id
├── name
└── created_at

dataset_<id>
├── column_a
├── column_b
└── column_c
```

The `datasets` table stores dataset metadata while the generated table stores the actual rows.

The application queries PostgreSQL's `information_schema` when it needs the generated table's schema rather than maintaining a second copy of the schema in the metadata table.

## Error Handling

The application performs basic validation before database operations. PostgreSQL handles database-level validation.

If PostgreSQL rejects an operation, the transaction is rolled back and the error is returned through the API for the frontend to display.

```text
Application validation
        ↓
PostgreSQL operation
        ↓
   ┌────┴────┐
Success     Error
   ↓           ↓
Commit      Rollback
              ↓
         API error response
              ↓
           Frontend
```

## Future Work

* Dockerize the application with Docker Compose for simpler local setup
* Stream large CSV files instead of loading entire files into memory
* Support additional formats such as JSON/JSONL and Parquet
* Improve type inference and handling of ambiguous data
* Add more structured and actionable API errors
* Add dataset replacement, migration, and export functionality
* Expand query capabilities with pagination and aggregation
* Generate richer API documentation from dataset schemas
* Expand integration testing and observability
* Package the core API functionality as a reusable Java library so it can be embedded directly into existing applications without running a separate server