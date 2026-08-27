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
* Interactively build and test API queries through the frontend
* Generate reusable API endpoints from configured queries
* Run the frontend, backend, and PostgreSQL database together with Docker Compose

## Architecture

```text
                         Docker Compose
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
          Frontend          Backend        PostgreSQL
           React          Spring Boot       Database
              │               │               │
              └───────────────┼───────────────┘
                              │
                         Dataset API
````

The dataset workflow is:

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
* **Frontend:** React, TypeScript, Vite
* **Database:** PostgreSQL
* **Containerization:** Docker, Docker Compose
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

docker/
└── init.sql

frontend/
├── src/
├── Dockerfile
└── nginx.conf

Dockerfile
docker-compose.yml
```

## Running Locally

There are two ways to run the application:

1. **Docker Compose (recommended):** Starts the frontend, Spring Boot backend, and PostgreSQL database together.
2. **Manual setup:** Runs the frontend and backend directly and connects to a locally installed PostgreSQL instance.

### Option 1: Docker Compose (Recommended)

#### Prerequisites

* Docker Desktop

#### 1. Clone the repository

```bash
git clone https://github.com/ellayipinghou/data-api-endpoint-generator.git
cd data-api-endpoint-generator
```

#### 2. Configure database credentials

Create a `.env` file in the project root:

```text
DB_USERNAME=postgres
DB_PASSWORD=your-password
```

The Docker Compose configuration uses these values to configure both PostgreSQL and the Spring Boot backend.

Do not commit `.env` or database credentials to the repository.

#### 3. Start the application

```bash
docker compose up --build
```

This starts:

* **Frontend:** `http://localhost:3000`
* **Backend:** `http://localhost:8080`
* **PostgreSQL:** `localhost:5432`

The PostgreSQL container automatically creates the `dataserv` database and initializes the `datasets` metadata table using `docker/init.sql`.

PostgreSQL data is stored in a Docker named volume, so datasets persist when the containers are stopped and restarted.

To stop the application:

```bash
docker compose down
```

To stop the application and delete the PostgreSQL data volume:

```bash
docker compose down -v
```

The `-v` option permanently deletes the stored database data and should only be used when a fresh database is desired.

### Option 2: Run Without Docker

#### Prerequisites

* Java 25
* Gradle
* PostgreSQL
* Node.js and npm

#### 1. Clone the repository

```bash
git clone https://github.com/ellayipinghou/data-api-endpoint-generator.git
cd data-api-endpoint-generator
```

#### 2. Set up PostgreSQL

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

#### 3. Configure database environment variables

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

#### 4. Start the backend

```bash
./gradlew bootRun
```

On Windows:

```powershell
.\gradlew.bat bootRun
```

#### 5. Start the frontend

From the frontend directory:

```bash
npm install
npm run dev
```

The frontend will be available at:

```text
http://localhost:5173
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

Each dataset has three tabs: **Overview**, **API**, and **Query**.

### Overview

The Overview tab displays information about the dataset, including its schema, column names, data types, and supported query operators.

The schema is retrieved directly from PostgreSQL, which serves as the source of truth for the generated dataset table.

### API

The API tab provides the dataset's base query endpoint and information needed to access the dataset programmatically.

Datasets are queried through a generic endpoint:

```text
GET /datasets/{id}/query
```

For example:

```text
/datasets/123/query
```

The available columns and operators are based on the actual PostgreSQL table schema.

The endpoint can be used directly by another application or API client to retrieve dataset data.

### Query

The Query tab provides an interactive way to build and test API queries.

Users can select filters, comparison operators, sorting, and result limits, then execute the query to see the matching rows directly in the application.

The tab also generates the corresponding API endpoint for the selected query. This allows users to build and validate a query visually before using the resulting endpoint in another application.

The workflow is:

```text
Select query parameters
        ↓
Execute query
        ↓
Preview the results
        ↓
Get the corresponding API endpoint
```

For example, a query configured with:

* `age > 30`
* `score >= 90`
* `active = true`
* Sort by `score` descending
* Limit results to 5

produces:

```text
/datasets/123/query?age_gt=30&score_gte=90&active=true&sort=score,desc&limit=5
```

The generated endpoint can then be copied and used directly by another application or API client to retrieve the same results.

## Query API

The generic query endpoint is:

```text
GET /datasets/{id}/query
```

Example:

```text
/datasets/123/query?age_gt=30&score_gte=90&active=true&sort=score,desc&limit=5
```

Supported query functionality includes:

* Comparison operators such as `gt`, `gte`, `lt`, and `lte`
* Equality filtering
* Boolean filtering
* Sorting
* Result limits

The available columns and operators are based on the actual PostgreSQL table schema.

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

## Docker Architecture

The Docker Compose setup runs three services:

```text
┌───────────────────────────────────────────────┐
│                 Docker Compose                │
│                                               │
│  ┌─────────────┐    ┌─────────────┐           │
│  │  Frontend   │───▶│   Backend   │           │
│  │    Nginx    │    │ Spring Boot │           │
│  │    :3000    │    │    :8080    │           │
│  └─────────────┘    └──────┬──────┘           │
│                            │                  │
│                     ┌──────▼──────┐           │
│                     │ PostgreSQL  │           │
│                     │    :5432    │           │
│                     └──────┬──────┘           │
│                            │                  │
│                     postgres-data             │
│                         volume                │
└───────────────────────────────────────────────┘
```

The frontend is built with Vite and served as static files through Nginx. Nginx is configured to support client-side React routing.

The backend runs as a Spring Boot application in its own container.

PostgreSQL runs using the official PostgreSQL Docker image. The `docker/init.sql` script is automatically executed when a new PostgreSQL data directory is initialized.

A named Docker volume is used to persist PostgreSQL data independently of the database container.

## Potential Future Work

* Broaden data sources and formats (registry pattern) - support JSON/JSONL, Parquet, and generating APIs from existing database tables
* Expand query capabilities - add more advanced filtering, as well as pagination and aggregation
* Improve scalability - stream large CSV files directly from temporary storage to PostgreSQL instead of loading entire files into memory
* Improve database error handling - translate PostgreSQL errors into clear, actionable messages for users
* Improve schema inference - handle ambiguous types and more complex column definitions (constraints for example)
* Support dataset lifecycle management - add replacement, migration, deletion, and export capabilities
* Package backend as a reusable Java library - allow the API-generation functionality to be embedded directly into existing applications without requiring a separate server