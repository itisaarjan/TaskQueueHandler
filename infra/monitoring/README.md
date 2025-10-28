# Monitoring Setup for TaskQueue Microservices

## 🎯 Overview

Complete monitoring stack with **Prometheus**, **Grafana**, and **Spring Boot Actuator** for all microservices.

## 📊 Components

### **1. Spring Boot Actuator**
- Exposes metrics endpoints at `/actuator/prometheus`
- Configured in all 5 microservices
- Metrics include:
  - HTTP request rates and latencies
  - JVM memory usage (heap/non-heap)
  - Thread counts
  - GC statistics
  - Custom application metrics

### **2. Prometheus**
- Deployed via `kube-prometheus-stack` Helm chart
- Auto-discovers services using ServiceMonitor CRDs
- Scrapes metrics every 30 seconds
- Stores time-series data

### **3. Grafana**
- Pre-configured dashboards for Spring Boot metrics
- Accessible via NodePort
- Default credentials: `admin/admin`
- Auto-imports dashboards from ConfigMaps

### **4. ServiceMonitors**
- One per microservice
- Configures Prometheus scraping
- Points to `/actuator/prometheus` endpoint

## 🚀 Deployment

### **Prerequisites**
1. Kubernetes cluster running
2. ArgoCD installed
3. `monitoring` namespace created

### **Deploy with ArgoCD**

```bash
# Apply the monitoring application
kubectl apply -f infra/k8s/argoCD/platform/monitoring/monitoring-app.yaml

# ArgoCD will automatically deploy:
# - Prometheus Operator
# - Prometheus
# - Grafana
# - Alertmanager
# - Spring Boot Dashboard
```

### **Deploy Backend Services**

```bash
# Deploy services with ServiceMonitors
kubectl apply -f infra/k8s/argoCD/backend/backend-app.yaml

# ArgoCD will deploy all services + ServiceMonitors
```

## 📍 Access Points

### **Grafana**
```bash
# Get Grafana service
kubectl get svc -n monitoring | grep grafana

# Port-forward to access locally
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80

# Access at: http://localhost:3000
# Username: admin
# Password: admin
```

### **Prometheus**
```bash
# Port-forward Prometheus
kubectl port-forward -n monitoring svc/prometheus-kube-prometheus-prometheus 9090:9090

# Access at: http://localhost:9090
```

### **Service Metrics Endpoints**
```bash
# Access actuator endpoints directly
kubectl port-forward -n backend svc/email-service 8004:8004
curl http://localhost:8004/actuator/prometheus

# Repeat for other services (ports: 8000, 8001, 8002, 8003, 8004)
```

## 📈 Available Metrics

### **HTTP Metrics**
- `http_server_requests_seconds_count` - Request count
- `http_server_requests_seconds_sum` - Total request duration
- `http_server_requests_seconds_bucket` - Latency histogram

### **JVM Metrics**
- `jvm_memory_used_bytes` - Memory usage by area (heap/non-heap)
- `jvm_memory_max_bytes` - Max memory available
- `jvm_threads_live_threads` - Active thread count
- `jvm_gc_pause_seconds` - GC pause duration

### **System Metrics**
- `system_cpu_usage` - CPU usage percentage
- `process_uptime_seconds` - Service uptime

## 🎨 Grafana Dashboards

### **Spring Boot Microservices Dashboard**
Location: Pre-installed via ConfigMap

**Panels:**
1. **HTTP Request Rate** - Requests per second by service and endpoint
2. **HTTP Request Duration (P95)** - 95th percentile latency
3. **JVM Heap Usage** - Heap memory utilization gauge
4. **JVM Memory Usage** - Memory usage over time
5. **JVM Threads** - Thread count over time  
6. **Service Health** - UP/DOWN status for each service

### **How to View**
1. Login to Grafana (http://localhost:3000)
2. Go to **Dashboards** → **Browse**
3. Select **Spring Boot Microservices Metrics**

## 🔍 Querying Prometheus

### **Useful PromQL Queries**

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
sum(rate(http_server_requests_seconds_count{namespace="backend", status=~"5.."}[5m]))
```

## 🔧 Troubleshooting

### **Metrics Not Showing Up**

1. **Check if ServiceMonitor is created:**
```bash
kubectl get servicemonitor -n backend
```

2. **Check if Prometheus discovered the target:**
```bash
kubectl port-forward -n monitoring svc/prometheus-kube-prometheus-prometheus 9090:9090
# Go to: http://localhost:9090/targets
# Look for your services under "serviceMonitor/backend/..."
```

3. **Check actuator endpoint is accessible:**
```bash
kubectl exec -it -n backend <pod-name> -- curl localhost:8004/actuator/prometheus
```

4. **Check Prometheus logs:**
```bash
kubectl logs -n monitoring prometheus-prometheus-kube-prometheus-prometheus-0
```

### **Grafana Dashboard Not Loading**

1. **Check if ConfigMap exists:**
```bash
kubectl get cm -n monitoring spring-boot-metrics-dashboard
```

2. **Check Grafana sidecar logs:**
```bash
kubectl logs -n monitoring deployment/prometheus-grafana -c grafana-sc-dashboard
```

3. **Manually import:**
   - Copy dashboard JSON from ConfigMap
   - Grafana UI → + → Import → Paste JSON

### **Services Showing as DOWN**

1. **Check if pods are running:**
```bash
kubectl get pods -n backend
```

2. **Check service endpoints:**
```bash
kubectl get endpoints -n backend
```

3. **Verify actuator health:**
```bash
kubectl exec -it -n backend <pod-name> -- curl localhost:8004/actuator/health
```

## 📁 File Structure

```
infra/
├── k8s/argoCD/
│   ├── backend/resources/
│   │   ├── servicemonitors/
│   │   │   ├── email-service-monitor.yaml
│   │   │   ├── task-queue-monitor.yaml
│   │   │   ├── queue-service-monitor.yaml
│   │   │   ├── image-worker-monitor.yaml
│   │   │   └── task-db-service-monitor.yaml
│   │   └── email-service.yaml (Service resource)
│   │
│   └── platform/monitoring/
│       ├── prom-operator/
│       │   ├── template/prom-app.yaml (Helm chart config)
│       │   └── values.yaml
│       ├── dashboards/
│       │   └── spring-boot-dashboard.yaml
│       ├── kustomization.yaml
│       └── monitoring-app.yaml

service/
├── pom.xml (Parent POM with Actuator dependencies)
├── EmailService/
│   └── src/main/resources/application.properties (Actuator config)
├── QueueService/
│   └── src/main/resources/application.properties (Actuator config)
├── ImageWorker/
│   └── src/main/resources/application.properties (Actuator config)
├── task-queue/
│   └── src/main/resources/application.properties (Actuator config)
└── TaskDBService/
    └── src/main/resources/application.properties (Actuator config)
```

## ✅ Verification Checklist

After deployment, verify:

- [ ] Prometheus is running: `kubectl get pods -n monitoring | grep prometheus`
- [ ] Grafana is running: `kubectl get pods -n monitoring | grep grafana`
- [ ] ServiceMonitors exist: `kubectl get servicemonitor -n backend`
- [ ] Prometheus targets are UP: Check Prometheus UI → Status → Targets
- [ ] Grafana dashboard is available: Check Grafana UI → Dashboards
- [ ] Metrics are flowing: Query Prometheus for `up{namespace="backend"}`

## 🔄 CI/CD Integration

When you push code changes:

1. **GitHub Actions** builds new Docker images with Actuator
2. Images are pushed to Docker Hub
3. **ArgoCD** detects changes and updates deployments
4. **Prometheus** automatically discovers new pods via ServiceMonitors
5. **Grafana** dashboards show updated metrics

No manual intervention needed! 🚀

## 📚 Additional Resources

- [Prometheus Operator Docs](https://prometheus-operator.dev/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Prometheus](https://micrometer.io/docs/registry/prometheus)
- [Grafana Dashboards](https://grafana.com/grafana/dashboards/)

## 🎉 Summary

You now have a complete monitoring stack:
- ✅ Spring Boot Actuator exposing metrics
- ✅ Prometheus scraping all 5 microservices
- ✅ Grafana dashboards visualizing performance
- ✅ ServiceMonitors auto-discovering services
- ✅ ArgoCD managing everything via GitOps

