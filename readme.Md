# TaskQueue - Distributed Image Processing System

A cloud-native, microservices-based image processing platform built with Spring Boot, deployed on Kubernetes with GitOps practices, and integrated with AWS services.

## Table of Contents

- [Overview](#overview)
- [System Architecture](#system-architecture)
- [Technology Stack](#technology-stack)
- [Microservices](#microservices)
- [Infrastructure](#infrastructure)
- [CI/CD Pipeline](#cicd-pipeline)
- [Deployment](#deployment)
- [Monitoring](#monitoring)
- [API Documentation](#api-documentation)
- [Development](#development)
- [Configuration](#configuration)

## Overview

TaskQueue is a distributed image processing system that enables users to upload images and apply various transformations (grayscale, resize, blur, invert, watermark). The system leverages AWS S3 for storage, Redis for queueing, PostgreSQL for metadata persistence, and implements a complete microservices architecture with centralized monitoring and GitOps deployment.

### Key Features

- Asynchronous image processing with queue-based task distribution
- Multiple image transformation operations
- Cloud-native storage with AWS S3
- RESTful API with Spring Boot
- Containerized deployment on Kubernetes
- GitOps-based continuous deployment with ArgoCD
- Comprehensive monitoring with Prometheus and Grafana
- Service discovery with Eureka
- Automated CI/CD pipelines

## System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              User Interface                                  │
│                         (React Frontend - Port 80)                           │
└────────────────────────────────┬────────────────────────────────────────────┘
                                 │
                                 │ HTTP/HTTPS
                                 │
┌────────────────────────────────▼────────────────────────────────────────────┐
│                         Kubernetes Cluster                                   │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Ingress Controller (nginx)                        │   │
│  └──────────────────────────────┬──────────────────────────────────────┘   │
│                                 │                                            │
│  ┌──────────────────────────────┴──────────────────────────────────────┐   │
│  │                    Frontend Namespace                                │   │
│  │  ┌──────────────────────────────────────────────────────────────┐  │   │
│  │  │  Frontend Service (NodePort) - 2 Replicas                     │  │   │
│  │  │  - Nginx serving React SPA                                    │  │   │
│  │  │  - Proxies /tasks/* to backend                                │  │   │
│  │  └──────────────────────────────────────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                 │                                            │
│  ┌──────────────────────────────┴──────────────────────────────────────┐   │
│  │                    Backend Namespace                                 │   │
│  │                                                                       │   │
│  │  ┌─────────────────────────────────────────────────────────────┐   │   │
│  │  │  Task Queue Service (Port 8000) - 3 Replicas                 │   │   │
│  │  │  - Handles image upload requests                             │   │   │
│  │  │  - Uploads to AWS S3                                          │   │   │
│  │  │  - Creates tasks in PostgreSQL                               │   │   │
│  │  │  - Publishes to Redis queue                                  │   │   │
│  │  └──────────────────┬────────────────────────────────────────────┘   │   │
│  │                     │                                                 │   │
│  │  ┌──────────────────┴─────────────────┬───────────────────────┐    │   │
│  │  │                                     │                        │    │   │
│  │  ▼                                     ▼                        ▼    │   │
│  │  ┌─────────────────┐  ┌──────────────────┐  ┌───────────────┐     │   │
│  │  │ Queue Service   │  │  Image Worker     │  │ Email Service │     │   │
│  │  │ (Port 8001)     │  │  (Port 8003)      │  │ (Port 8004)   │     │   │
│  │  │                 │  │                   │  │               │     │   │
│  │  │ - Polls Redis   │  │ - Downloads from  │  │ - Sends email │     │   │
│  │  │ - Dispatches    │  │   S3              │  │   notifications│    │   │
│  │  │   tasks         │  │ - Processes image │  │               │     │   │
│  │  │                 │  │ - Uploads result  │  │               │     │   │
│  │  │                 │  │   to S3           │  │               │     │   │
│  │  └────────┬────────┘  └─────────┬────────┘  └───────────────┘     │   │
│  │           │                      │                                  │   │
│  │           └──────────────────────┼──────────────────────────────┐  │   │
│  │                                  │                               │  │   │
│  │                                  ▼                               ▼  │   │
│  │                    ┌───────────────────────┐  ┌──────────────────┐ │   │
│  │                    │ TaskDB Service        │  │  Redis           │ │   │
│  │                    │ (Port 8002)           │  │  (Port 6379)     │ │   │
│  │                    │                       │  │                  │ │   │
│  │                    │ - Task metadata CRUD  │  │  - Task queue    │ │   │
│  │                    │ - Status tracking     │  │  - Pub/Sub       │ │   │
│  │                    └───────────┬───────────┘  └──────────────────┘ │   │
│  │                                │                                    │   │
│  │                                ▼                                    │   │
│  │                    ┌───────────────────────┐                       │   │
│  │                    │  PostgreSQL           │                       │   │
│  │                    │  (Port 5432)          │                       │   │
│  │                    │                       │                       │   │
│  │                    │  - Tasks table        │                       │   │
│  │                    │  - Persistent storage │                       │   │
│  │                    └───────────────────────┘                       │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Monitoring Namespace                              │   │
│  │  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐ │   │
│  │  │  Prometheus      │  │  Grafana         │  │ Alertmanager     │ │   │
│  │  │  (Port 9090)     │  │  (Port 3000)     │  │                  │ │   │
│  │  │                  │  │                  │  │                  │ │   │
│  │  │  - Metrics       │  │  - Dashboards    │  │  - Alerts        │ │   │
│  │  │    collection    │  │  - Visualization │  │                  │ │   │
│  │  └──────────────────┘  └──────────────────┘  └──────────────────┘ │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    ArgoCD Namespace                                  │   │
│  │  ┌──────────────────────────────────────────────────────────────┐  │   │
│  │  │  ArgoCD Server (Port 8080)                                    │  │   │
│  │  │  - GitOps continuous deployment                               │  │   │
│  │  │  - Application syncing                                         │  │   │
│  │  │  - Automated reconciliation                                    │  │   │
│  │  └──────────────────────────────────────────────────────────────┘  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                 │
                                 │
                        ┌────────▼─────────┐
                        │   AWS Cloud      │
                        │                  │
                        │  ┌────────────┐ │
                        │  │ S3 Bucket  │ │
                        │  │            │ │
                        │  │ - Original │ │
                        │  │   images   │ │
                        │  │ - Processed│ │
                        │  │   results  │ │
                        │  └────────────┘ │
                        │                  │
                        │  ┌────────────┐ │
                        │  │   Lambda   │ │
                        │  │            │ │
                        │  │ - Upload   │ │
                        │  │ - Download │ │
                        │  └────────────┘ │
                        └──────────────────┘
```

### Data Flow Architecture

```
Upload Flow:
───────────

User → Frontend → Task Queue Service → S3 (upload) → PostgreSQL (metadata)
                                      → Redis (queue task)
                                      → Response (task ID)

Processing Flow:
─────────────────

Redis Queue → Queue Service → Image Worker → S3 (download original)
                                            → Process image
                                            → S3 (upload result)
                                            → TaskDB Service (update status)
                                            → Email Service (notification)

Download Flow:
──────────────

User → Frontend → Lambda Function → S3 (download) → User
```

### Network Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Kubernetes Networking                        │
│                                                                  │
│  Namespace: frontend                                            │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Service: frontend (NodePort 31081)                       │  │
│  │  Pods: 2x frontend (10.244.0.x:80)                        │  │
│  └──────────────────────────────────────────────────────────┘  │
│                          │                                      │
│                          │ Cross-namespace communication        │
│                          ▼                                      │
│  Namespace: backend                                             │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Service: task-queue-service (NodePort 30080)             │  │
│  │  ClusterIP: 10.101.223.150:8000                           │  │
│  │  Pods: 3x task-queue                                      │  │
│  │                                                            │  │
│  │  Service: queue-service (ClusterIP)                       │  │
│  │  Service: image-worker (ClusterIP)                        │  │
│  │  Service: email-service (ClusterIP)                       │  │
│  │  Service: task-db-service (ClusterIP)                     │  │
│  │  Service: redis-service (ClusterIP)                       │  │
│  │  Service: postgres-service (ClusterIP)                    │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  Namespace: monitoring                                          │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  Service: prometheus-app-grafana (NodePort)               │  │
│  │  Service: prometheus-app-kube-promet-prometheus           │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  DNS Resolution:                                                │
│  - task-queue-service.backend.svc.cluster.local                │
│  - redis-service.backend.svc.cluster.local                     │
│  - postgres-service.backend.svc.cluster.local                  │
└─────────────────────────────────────────────────────────────────┘
```

### CI/CD Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                         GitHub Repository                               │
│                   github.com/itisaarjan/TaskQueueHandler               │
└────────────────────────┬───────────────────────────────────────────────┘
                         │
                         │ Git Push (main branch)
                         │
┌────────────────────────▼───────────────────────────────────────────────┐
│                      GitHub Actions Workflows                           │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────┐   │
│  │  Backend Services Build (.github/workflows/docker-build.yml)   │   │
│  │                                                                  │   │
│  │  Triggers:                                                       │   │
│  │    - Push to main/develop (service/**)                          │   │
│  │                                                                  │   │
│  │  Matrix Build:                                                   │   │
│  │    1. task-queue                                                │   │
│  │    2. QueueService                                              │   │
│  │    3. ImageWorker                                               │   │
│  │    4. EmailService                                              │   │
│  │    5. TaskDBService                                             │   │
│  │                                                                  │   │
│  │  Steps:                                                          │   │
│  │    - Checkout code                                              │   │
│  │    - Setup Docker Buildx                                        │   │
│  │    - Login to Docker Hub                                        │   │
│  │    - Build multi-platform images (amd64, arm64)                 │   │
│  │    - Push to Docker Hub (arjansubedi/*)                         │   │
│  │    - Cache layers for faster builds                             │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────┐   │
│  │  Frontend Build (.github/workflows/frontend-build.yml)         │   │
│  │                                                                  │   │
│  │  Triggers:                                                       │   │
│  │    - Push to main/develop (frontend/**)                         │   │
│  │                                                                  │   │
│  │  Steps:                                                          │   │
│  │    - Checkout code                                              │   │
│  │    - Setup Docker Buildx                                        │   │
│  │    - Build React app                                            │   │
│  │    - Build nginx image with built assets                        │   │
│  │    - Push to Docker Hub (arjansubedi/taskqueue-frontend)        │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└────────────────────────┬───────────────────────────────────────────────┘
                         │
                         │ Images pushed to Docker Hub
                         │
┌────────────────────────▼───────────────────────────────────────────────┐
│                          Docker Hub Registry                            │
│                      hub.docker.com/u/arjansubedi                      │
│                                                                          │
│  Images:                                                                │
│    - arjansubedi/task-queue:latest                                     │
│    - arjansubedi/queueservice:latest                                   │
│    - arjansubedi/imageworker:latest                                    │
│    - arjansubedi/emailservice:latest                                   │
│    - arjansubedi/taskdbservice:latest                                  │
│    - arjansubedi/taskqueue-frontend:latest                             │
└────────────────────────┬───────────────────────────────────────────────┘
                         │
                         │ ArgoCD pulls images
                         │
┌────────────────────────▼───────────────────────────────────────────────┐
│                     ArgoCD GitOps Deployment                            │
│                                                                          │
│  Applications:                                                          │
│  ┌────────────────────────────────────────────────────────────────┐   │
│  │  master-app (App of Apps Pattern)                              │   │
│  │  Path: infra/k8s/argoCD/                                        │   │
│  │                                                                  │   │
│  │  ├─ platform-app                                                │   │
│  │  │  └─ monitoring (Prometheus, Grafana)                         │   │
│  │  │                                                               │   │
│  │  ├─ backend-app                                                 │   │
│  │  │  ├─ Backend namespace                                        │   │
│  │  │  ├─ All 5 microservices                                      │   │
│  │  │  ├─ PostgreSQL                                               │   │
│  │  │  ├─ Redis                                                     │   │
│  │  │  └─ ServiceMonitors                                          │   │
│  │  │                                                               │   │
│  │  └─ frontend-app                                                │   │
│  │     ├─ Frontend namespace                                       │   │
│  │     ├─ Frontend deployment (2 replicas)                         │   │
│  │     ├─ Frontend service                                         │   │
│  │     └─ Ingress configuration                                    │   │
│  │                                                                  │   │
│  │  Sync Policy:                                                    │   │
│  │    - Automated sync enabled                                     │   │
│  │    - Self-heal enabled                                          │   │
│  │    - Prune enabled                                              │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└────────────────────────┬───────────────────────────────────────────────┘
                         │
                         │ Deploys to cluster
                         │
┌────────────────────────▼───────────────────────────────────────────────┐
│                    Kubernetes Cluster (Minikube)                        │
│                                                                          │
│  Running Applications:                                                  │
│    - Frontend (2 pods)                                                  │
│    - Backend services (11 pods total)                                  │
│    - Monitoring stack (Prometheus, Grafana)                            │
│    - Databases (PostgreSQL, Redis)                                     │
└─────────────────────────────────────────────────────────────────────────┘
```

### Monitoring Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    Prometheus Monitoring Stack                           │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────┐    │
│  │                   Metrics Collection Layer                      │    │
│  │                                                                  │    │
│  │  Spring Boot Services (Actuator Endpoints)                      │    │
│  │  ┌─────────────────────────────────────────────────────────┐  │    │
│  │  │  /actuator/prometheus on each service:                   │  │    │
│  │  │                                                           │  │    │
│  │  │  - task-queue-service:8000/actuator/prometheus          │  │    │
│  │  │  - queue-service:8001/actuator/prometheus               │  │    │
│  │  │  - task-db-service:8002/actuator/prometheus             │  │    │
│  │  │  - image-worker:8003/actuator/prometheus                │  │    │
│  │  │  - email-service:8004/actuator/prometheus               │  │    │
│  │  │                                                           │  │    │
│  │  │  Metrics Exposed:                                         │  │    │
│  │  │    - JVM metrics (memory, GC, threads)                   │  │    │
│  │  │    - HTTP metrics (requests, latency, errors)            │  │    │
│  │  │    - Application metrics (custom business metrics)       │  │    │
│  │  └─────────────────────────────────────────────────────────┘  │    │
│  └────────────────────────────┬───────────────────────────────────┘    │
│                                │                                        │
│                                │ Scraped every 30s                      │
│                                │                                        │
│  ┌────────────────────────────▼───────────────────────────────────┐    │
│  │               ServiceMonitor Custom Resources                   │    │
│  │                                                                  │    │
│  │  One ServiceMonitor per service:                                │    │
│  │    - email-service-monitor.yaml                                 │    │
│  │    - task-queue-monitor.yaml                                    │    │
│  │    - queue-service-monitor.yaml                                 │    │
│  │    - image-worker-monitor.yaml                                  │    │
│  │    - task-db-service-monitor.yaml                               │    │
│  │                                                                  │    │
│  │  Configuration:                                                  │    │
│  │    - Namespace: backend                                         │    │
│  │    - Interval: 30s                                              │    │
│  │    - Path: /actuator/prometheus                                 │    │
│  │    - Selector: matchLabels (app: service-name)                  │    │
│  └────────────────────────────┬───────────────────────────────────┘    │
│                                │                                        │
│                                │ Discovered by Prometheus Operator      │
│                                │                                        │
│  ┌────────────────────────────▼───────────────────────────────────┐    │
│  │                  Prometheus Server                              │    │
│  │                  (Port 9090)                                     │    │
│  │                                                                  │    │
│  │  Components:                                                     │    │
│  │    - Time-series database                                       │    │
│  │    - PromQL query engine                                        │    │
│  │    - Service discovery (Kubernetes)                             │    │
│  │    - Alert manager integration                                  │    │
│  │                                                                  │    │
│  │  Data Retention: 15 days                                        │    │
│  │  Storage: PersistentVolume                                      │    │
│  └────────────────────────────┬───────────────────────────────────┘    │
│                                │                                        │
│                                │ Queries                                │
│                                │                                        │
│  ┌────────────────────────────▼───────────────────────────────────┐    │
│  │                     Grafana                                     │    │
│  │                  (Port 3000)                                     │    │
│  │                                                                  │    │
│  │  Dashboards:                                                     │    │
│  │  ┌──────────────────────────────────────────────────────────┐ │    │
│  │  │  Spring Boot Microservices Metrics                       │ │    │
│  │  │                                                            │ │    │
│  │  │  Panels:                                                   │ │    │
│  │  │    1. HTTP Request Rate                                   │ │    │
│  │  │       - Requests/sec by service and endpoint              │ │    │
│  │  │                                                            │ │    │
│  │  │    2. HTTP Request Duration (P95)                         │ │    │
│  │  │       - 95th percentile latency per service               │ │    │
│  │  │                                                            │ │    │
│  │  │    3. JVM Heap Usage                                      │ │    │
│  │  │       - Heap memory utilization gauge                     │ │    │
│  │  │                                                            │ │    │
│  │  │    4. JVM Memory Usage                                    │ │    │
│  │  │       - Heap/Non-heap memory over time                    │ │    │
│  │  │                                                            │ │    │
│  │  │    5. JVM Threads                                         │ │    │
│  │  │       - Thread count over time                            │ │    │
│  │  │                                                            │ │    │
│  │  │    6. Service Health                                      │ │    │
│  │  │       - UP/DOWN status per service                        │ │    │
│  │  └──────────────────────────────────────────────────────────┘ │    │
│  │                                                                  │    │
│  │  Access: http://localhost:3000                                  │    │
│  │  Credentials: admin / admin                                     │    │
│  └──────────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────┘
```

## Technology Stack

### Backend Services
- **Language**: Java 17
- **Framework**: Spring Boot 3.5.6
- **Build Tool**: Maven
- **Service Discovery**: Spring Cloud Netflix Eureka
- **HTTP Client**: OpenFeign
- **Database**: PostgreSQL 15
- **Cache/Queue**: Redis 7
- **Object Storage**: AWS S3
- **Image Processing**: Java ImageIO, BufferedImage

### Frontend
- **Framework**: React 18.2.0
- **HTTP Client**: Axios
- **Build Tool**: React Scripts 5.0.1
- **Web Server**: Nginx (Alpine)

### Infrastructure
- **Container Runtime**: Docker
- **Orchestration**: Kubernetes (Minikube for development)
- **GitOps**: ArgoCD
- **Monitoring**: Prometheus, Grafana
- **Service Mesh**: Kubernetes native (ClusterIP, NodePort)
- **Ingress**: Nginx Ingress Controller

### CI/CD
- **Source Control**: GitHub
- **CI Pipeline**: GitHub Actions
- **Container Registry**: Docker Hub
- **CD Tool**: ArgoCD

### Cloud Services (AWS)
- **Storage**: S3
- **Compute**: Lambda (Upload/Download handlers)
- **API Gateway**: REST API for Lambda functions

## Microservices

### 1. Task Queue Service (Port 8000)
**Repository**: `service/task-queue/`

Main entry point for task submission.

**Responsibilities**:
- Accept image upload requests
- Upload images to AWS S3
- Create task records in PostgreSQL
- Publish tasks to Redis queue
- Return task ID to clients

**Endpoints**:
- `POST /tasks/upload` - Upload and queue image for processing
- `GET /actuator/health` - Health check
- `GET /actuator/prometheus` - Metrics endpoint

**Dependencies**:
- AWS S3 (for image storage)
- Redis (for task queue)
- TaskDB Service (for metadata)
- Queue Service (via Feign client)

### 2. Queue Service (Port 8001)
**Repository**: `service/QueueService/`

Manages task queue operations.

**Responsibilities**:
- Poll Redis for pending tasks
- Dispatch tasks to Image Worker
- Handle queue priority and retry logic

**Endpoints**:
- `POST /queue/publish` - Publish task to queue
- `GET /queue/consume` - Consume task from queue
- `GET /actuator/health` - Health check
- `GET /actuator/prometheus` - Metrics endpoint

**Dependencies**:
- Redis (queue storage)
- Image Worker (task processing)

### 3. Image Worker (Port 8003)
**Repository**: `service/ImageWorker/`

Performs image transformations.

**Responsibilities**:
- Download images from S3
- Apply transformations (grayscale, resize, blur, invert, watermark)
- Upload processed images to S3
- Update task status via TaskDB Service

**Supported Operations**:
- **Grayscale**: Convert to grayscale
- **Resize**: Resize to max 1024px (maintaining aspect ratio)
- **Blur**: Apply Gaussian blur
- **Invert**: Invert colors
- **Watermark**: Add text watermark

**Endpoints**:
- `POST /process` - Process image task
- `GET /actuator/health` - Health check
- `GET /actuator/prometheus` - Metrics endpoint

**Dependencies**:
- AWS S3 (download/upload)
- TaskDB Service (status updates)
- Email Service (notifications)

### 4. TaskDB Service (Port 8002)
**Repository**: `service/TaskDBService/`

Manages task metadata and persistence.

**Responsibilities**:
- CRUD operations for task records
- Task status tracking
- Query interface for task information

**Endpoints**:
- `POST /tasks` - Create task record
- `GET /tasks/{id}` - Get task by ID
- `PUT /tasks/{id}` - Update task status
- `GET /actuator/health` - Health check
- `GET /actuator/prometheus` - Metrics endpoint

**Database Schema**:
```sql
Table: tasks
  - id (UUID, primary key)
  - original_url (VARCHAR)
  - result_url (VARCHAR)
  - status (VARCHAR: pending, processing, completed, failed)
  - error_message (TEXT)
  - created_at (TIMESTAMP)
  - updated_at (TIMESTAMP)
```

**Dependencies**:
- PostgreSQL database

### 5. Email Service (Port 8004)
**Repository**: `service/EmailService/`

Handles notification delivery.

**Responsibilities**:
- Send task completion notifications
- Send error notifications
- Email queue management

**Endpoints**:
- `POST /email/send` - Send email notification
- `GET /actuator/health` - Health check
- `GET /actuator/prometheus` - Metrics endpoint

**Dependencies**:
- SMTP server configuration

### 6. Discovery Server (Port 8761)
**Repository**: `service/discovery-server/`

Eureka server for service discovery.

**Responsibilities**:
- Service registration
- Service discovery
- Health monitoring

**Endpoints**:
- `GET /eureka/apps` - List registered applications

## Infrastructure

### Kubernetes Namespaces

| Namespace | Purpose | Resources |
|-----------|---------|-----------|
| `frontend` | Frontend application | Deployment (2 pods), Service (NodePort), Ingress |
| `backend` | Backend microservices | 6 Deployments, 6 Services, PostgreSQL, Redis |
| `monitoring` | Observability stack | Prometheus, Grafana, Alertmanager |
| `argocd` | GitOps deployment | ArgoCD server, controllers |

### Persistent Storage

**PostgreSQL**:
- Storage: 1Gi PersistentVolume
- StorageClass: standard
- Access Mode: ReadWriteOnce

**Redis**:
- Storage: 1Gi PersistentVolume
- StorageClass: standard
- Access Mode: ReadWriteOnce

### Service Types

**NodePort Services** (External Access):
- `frontend`: 31081
- `task-queue-service`: 30080
- `prometheus-app-grafana`: Auto-assigned

**ClusterIP Services** (Internal Only):
- All other backend services
- Databases (PostgreSQL, Redis)

## CI/CD Pipeline

### GitHub Actions Workflows

#### 1. Backend Services Build
**File**: `.github/workflows/docker-build.yml`

**Trigger**:
- Push to `main` or `develop` branches
- Changes in `service/**` directory

**Build Matrix**:
```yaml
services:
  - task-queue
  - QueueService
  - ImageWorker
  - EmailService
  - TaskDBService
```

**Build Process**:
1. Checkout repository
2. Set up Docker Buildx (multi-platform support)
3. Login to Docker Hub
4. Build Docker images for linux/amd64 and linux/arm64
5. Push to Docker Hub with `latest` tag
6. Cache layers for subsequent builds

**Images Produced**:
- `arjansubedi/task-queue:latest`
- `arjansubedi/queueservice:latest`
- `arjansubedi/imageworker:latest`
- `arjansubedi/emailservice:latest`
- `arjansubedi/taskdbservice:latest`

#### 2. Frontend Build
**File**: `.github/workflows/frontend-build.yml`

**Trigger**:
- Push to `main` or `develop` branches
- Changes in `frontend/**` directory

**Build Process**:
1. Checkout repository
2. Set up Docker Buildx
3. Build React application
4. Build nginx image with compiled assets
5. Push to Docker Hub

**Image Produced**:
- `arjansubedi/taskqueue-frontend:latest`

### ArgoCD GitOps

**Repository**: https://github.com/itisaarjan/TaskQueueHandler

**Sync Strategy**:
- Automated sync enabled
- Self-heal enabled (auto-correct drift)
- Prune enabled (remove orphaned resources)

**Application Structure**:
```
master-app (App of Apps)
├── platform-app
│   └── monitoring (Prometheus stack)
├── backend-app
│   ├── Backend namespace
│   ├── All microservices
│   ├── Databases
│   └── ServiceMonitors
└── frontend-app
    ├── Frontend namespace
    ├── Frontend deployment
    └── Ingress
```

**Deployment Flow**:
1. Code pushed to GitHub (main branch)
2. GitHub Actions builds Docker images
3. Images pushed to Docker Hub
4. ArgoCD detects Git repository changes
5. ArgoCD syncs Kubernetes manifests
6. Pods pull new images from Docker Hub
7. Rolling update performed
8. Health checks verify deployment

## Deployment

### Prerequisites

1. **Minikube** (or any Kubernetes cluster)
```bash
minikube start --cpus=4 --memory=8192
```

2. **kubectl** configured to access cluster

3. **ArgoCD** installed
```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

4. **Nginx Ingress Controller**
```bash
minikube addons enable ingress
```

### Deployment Steps

#### 1. Deploy ArgoCD Master Application
```bash
kubectl apply -f infra/k8s/argoCD/master-app.yaml
```

This will automatically deploy:
- Platform applications (monitoring)
- Backend applications (all microservices)
- Frontend application

#### 2. Verify Deployments
```bash
# Check ArgoCD applications
kubectl get applications -n argocd

# Check backend pods
kubectl get pods -n backend

# Check frontend pods
kubectl get pods -n frontend

# Check monitoring pods
kubectl get pods -n monitoring
```

#### 3. Access Services

**Frontend**:
```bash
kubectl port-forward -n frontend svc/frontend 3001:80
# Access at http://localhost:3001
```

**ArgoCD UI**:
```bash
kubectl port-forward -n argocd svc/argocd-server 8080:443
# Access at https://localhost:8080

# Get admin password
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d
```

**Grafana**:
```bash
kubectl port-forward -n monitoring svc/prometheus-app-grafana 3000:80
# Access at http://localhost:3000
# Default credentials: admin / admin
```

**Prometheus**:
```bash
kubectl port-forward -n monitoring \
  svc/prometheus-app-kube-promet-prometheus 9090:9090
# Access at http://localhost:9090
```

### Manual Deployment (Alternative)

If not using ArgoCD:

```bash
# Deploy backend
kubectl apply -k infra/k8s/argoCD/backend/

# Deploy frontend
kubectl apply -k infra/k8s/argoCD/frontend/

# Deploy monitoring
kubectl apply -k infra/k8s/argoCD/platform/monitoring/
```

## Monitoring

### Prometheus Metrics

All Spring Boot services expose metrics at `/actuator/prometheus`.

**Key Metrics**:

**HTTP Metrics**:
- `http_server_requests_seconds_count` - Total requests
- `http_server_requests_seconds_sum` - Total request duration
- `http_server_requests_seconds_bucket` - Latency histogram

**JVM Metrics**:
- `jvm_memory_used_bytes` - Memory usage by area
- `jvm_memory_max_bytes` - Maximum memory
- `jvm_threads_live_threads` - Active threads
- `jvm_gc_pause_seconds` - GC pause duration

**System Metrics**:
- `system_cpu_usage` - CPU utilization
- `process_uptime_seconds` - Service uptime

### ServiceMonitors

Each backend service has a ServiceMonitor custom resource that configures Prometheus scraping:

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: <service-name>-monitor
  namespace: backend
spec:
  selector:
    matchLabels:
      app: <service-name>
  endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 30s
```

### Grafana Dashboards

**Spring Boot Microservices Dashboard**:
- Location: Pre-configured via ConfigMap
- Access: Dashboards → Browse → "Spring Boot Microservices Metrics"

**Panels**:
1. HTTP Request Rate (requests/sec by service)
2. HTTP Request Duration P95 (latency)
3. JVM Heap Usage (gauge)
4. JVM Memory Usage (time series)
5. JVM Threads (time series)
6. Service Health (UP/DOWN status)

### Useful PromQL Queries

```promql
# Total request rate across all services
sum(rate(http_server_requests_seconds_count{namespace="backend"}[5m]))

# 95th percentile latency per service
histogram_quantile(0.95, 
  sum(rate(http_server_requests_seconds_bucket{namespace="backend"}[5m])) 
  by (le, application)
)

# Memory usage per service
jvm_memory_used_bytes{namespace="backend"}

# Error rate (5xx responses)
sum(rate(http_server_requests_seconds_count{namespace="backend",status=~"5.."}[5m]))
```

## API Documentation

### Task Queue Service API

#### Upload Image
```http
POST /tasks/upload
Content-Type: multipart/form-data

Parameters:
  - type: string (required) - Task type (e.g., "image")
  - content: file (required) - Image file
  - grayscale: boolean (optional) - Apply grayscale
  - resize: boolean (optional) - Resize to max 1024px
  - blur: boolean (optional) - Apply blur
  - invert: boolean (optional) - Invert colors
  - watermark: string (optional) - Watermark text

Response:
{
  "id": "uuid",
  "status": "pending",
  "originalUrl": "s3://bucket/path/to/original",
  "createdAt": "2025-10-28T12:00:00Z"
}
```

### TaskDB Service API

#### Get Task Status
```http
GET /tasks/{id}

Response:
{
  "id": "uuid",
  "originalUrl": "s3://bucket/path/to/original",
  "resultUrl": "s3://bucket/path/to/result",
  "status": "completed",
  "error": null,
  "createdAt": "2025-10-28T12:00:00Z",
  "updatedAt": "2025-10-28T12:05:00Z"
}
```

#### Update Task
```http
PUT /tasks/{id}
Content-Type: application/json

{
  "status": "completed",
  "resultUrl": "s3://bucket/path/to/result"
}
```

### AWS Lambda Endpoints

#### Download Processed Image
```http
GET https://<api-gateway-url>/download?key=<s3-key>

Response: Binary image data
```

## Development

### Local Development Setup

#### Prerequisites
- Java 17
- Maven 3.8+
- Node.js 18+
- Docker
- Minikube or similar Kubernetes environment

#### Build Backend Services
```bash
cd service
mvn clean install

# Build specific service
cd task-queue
mvn clean package
```

#### Run Backend Service Locally
```bash
cd service/task-queue
mvn spring-boot:run
```

#### Build Frontend
```bash
cd frontend
npm install
npm run build
```

#### Run Frontend Locally
```bash
cd frontend
npm start
# Access at http://localhost:3000
```

### Docker Build

#### Backend Service
```bash
cd service/task-queue
docker build -t task-queue:local .
```

#### Frontend
```bash
cd frontend
docker build -t frontend:local .
```

### Testing

#### Run Backend Tests
```bash
cd service
mvn test
```

#### Run Frontend Tests
```bash
cd frontend
npm test
```

## Configuration

### Environment Variables

#### Task Queue Service
```properties
# AWS Configuration
aws.region=us-east-1
aws.s3.bucket=task-queue-s3-bucket

# Redis Configuration
spring.data.redis.host=redis-service
spring.data.redis.port=6379

# Service Discovery
eureka.client.service-url.defaultZone=http://discovery-server:8761/eureka
```

#### Image Worker
```properties
# AWS Configuration
aws.region=us-east-1
aws.s3.bucket=task-queue-s3-bucket

# Processing Configuration
image.max.size=10485760  # 10MB
image.max.dimension=4096
```

### Kubernetes ConfigMaps

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: backend-config
  namespace: backend
data:
  REDIS_HOST: "redis-service"
  POSTGRES_HOST: "postgres-service"
  AWS_REGION: "us-east-1"
```

### Secrets Management

```bash
# Create AWS credentials secret
kubectl create secret generic aws-credentials \
  --from-literal=access-key-id=<YOUR_ACCESS_KEY> \
  --from-literal=secret-access-key=<YOUR_SECRET_KEY> \
  -n backend

# Create database credentials
kubectl create secret generic postgres-credentials \
  --from-literal=username=postgres \
  --from-literal=password=<YOUR_PASSWORD> \
  -n backend
```

## Access Credentials

### Default Credentials

| Service | URL | Username | Password |
|---------|-----|----------|----------|
| ArgoCD | https://localhost:8080 | admin | (kubectl get secret in argocd namespace) |
| Grafana | http://localhost:3000 | admin | admin |
| PostgreSQL | postgres-service:5432 | postgres | postgres |
| Redis | redis-service:6379 | - | - |

## Troubleshooting

### Common Issues

**1. Pods not starting**
```bash
# Check pod status
kubectl get pods -n <namespace>

# Check pod logs
kubectl logs -n <namespace> <pod-name>

# Describe pod for events
kubectl describe pod -n <namespace> <pod-name>
```

**2. Image pull errors**
```bash
# Verify image exists in Docker Hub
docker pull arjansubedi/<image-name>:latest

# Check imagePullPolicy in deployment
kubectl get deployment -n <namespace> <deployment-name> -o yaml
```

**3. Service connectivity issues**
```bash
# Test DNS resolution
kubectl run -it --rm debug --image=busybox --restart=Never -- nslookup <service-name>.<namespace>.svc.cluster.local

# Test service endpoint
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -- curl http://<service-name>.<namespace>.svc.cluster.local:<port>/actuator/health
```

**4. ArgoCD sync issues**
```bash
# Check application status
kubectl get applications -n argocd

# View application details
kubectl describe application <app-name> -n argocd

# Force sync
argocd app sync <app-name>
```

### Logs

```bash
# Backend service logs
kubectl logs -n backend deployment/<service-name> -f

# Frontend logs
kubectl logs -n frontend deployment/frontend -f

# ArgoCD logs
kubectl logs -n argocd deployment/argocd-server -f

# Prometheus logs
kubectl logs -n monitoring prometheus-<pod-name> -f
```

## Architecture Decisions

### Why Microservices?
- **Scalability**: Each service can scale independently
- **Resilience**: Failure isolation prevents cascading failures
- **Technology flexibility**: Different services can use different tech stacks
- **Team autonomy**: Services can be developed independently

### Why ArgoCD?
- **GitOps**: Infrastructure as code with Git as single source of truth
- **Automated sync**: Continuous reconciliation between Git and cluster state
- **Rollback capability**: Easy rollback to previous Git commits
- **Multi-cluster support**: Can manage multiple clusters

### Why Prometheus + Grafana?
- **Industry standard**: Widely adopted in Kubernetes ecosystems
- **Pull-based**: Prometheus pulls metrics, reducing service complexity
- **PromQL**: Powerful query language for metrics analysis
- **Alerting**: Built-in alertmanager for proactive monitoring

### Why Redis for Queue?
- **Performance**: In-memory storage for fast operations
- **Pub/Sub**: Native support for message patterns
- **Persistence**: Optional data persistence
- **Simple**: Easy to deploy and operate

## Project Structure

```
taskqueue/
├── .github/
│   └── workflows/
│       ├── docker-build.yml          # Backend CI pipeline
│       └── frontend-build.yml        # Frontend CI pipeline
├── frontend/
│   ├── public/
│   ├── src/
│   │   ├── App.js                    # Main React component
│   │   ├── App.css                   # Styles
│   │   └── index.js                  # Entry point
│   ├── Dockerfile                    # Multi-stage build
│   ├── nginx.conf                    # Nginx configuration
│   └── package.json
├── service/
│   ├── task-queue/                   # Main API service
│   │   └── src/main/java/com/example/task_queue/
│   ├── QueueService/                 # Queue management
│   ├── ImageWorker/                  # Image processing
│   ├── EmailService/                 # Notifications
│   ├── TaskDBService/                # Database operations
│   ├── discovery-server/             # Eureka server
│   ├── task-shared/                  # Shared models
│   └── pom.xml                       # Parent POM
├── infra/
│   ├── k8s/
│   │   └── argoCD/
│   │       ├── master-app.yaml       # App of Apps
│   │       ├── backend/              # Backend manifests
│   │       ├── frontend/             # Frontend manifests
│   │       └── platform/             # Infrastructure manifests
│   ├── aws-infra/                    # CDK infrastructure
│   │   └── lambda/                   # Lambda functions
│   └── monitoring/
│       └── dashboards/               # Grafana dashboards
└── README.md
```

## Contributing

### Development Workflow

1. Fork the repository
2. Create a feature branch
3. Make changes and test locally
4. Build Docker images
5. Test in Kubernetes
6. Submit pull request

### Code Standards

- Follow Java code conventions
- Use meaningful variable names
- Write unit tests for business logic
- Document public APIs
- Update README for significant changes

## License

This project is part of a learning exercise and is not licensed for production use.

## Contact

For questions or support, please open an issue in the GitHub repository.

---

**Repository**: https://github.com/itisaarjan/TaskQueueHandler

**Last Updated**: October 2025
