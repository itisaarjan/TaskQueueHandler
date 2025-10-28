# 🚀 End-to-End Deployment Guide

## Current Status ✅

Your cluster is **RUNNING** with:
- ✅ Minikube cluster: **Active**
- ✅ ArgoCD: **7 pods running**
- ✅ Backend services: **6 pods running** (email, image-worker, queue, task-db, postgres, redis)
- ✅ Monitoring: **Prometheus Operator, Grafana** running
- ✅ ServiceMonitors: **All 5 created**
- ✅ Grafana Dashboard: **Created**
- ✅ Services: **All backend services exposed**

## ⚠️ What Needs to Happen

The **current running pods use OLD images** (from 35 hours ago, before Actuator was added).

You need to:
1. **Push code to GitHub** → Triggers CI/CD
2. **New images build** with Actuator/Prometheus
3. **Update deployments** to use new images
4. **Test monitoring** end-to-end

---

## 📝 Step-by-Step Deployment

### **Step 1: Push Code Changes to GitHub**

```bash
cd /Users/arjansubedi/Downloads/taskqueue

# Stage all changes
git add .

# Commit with descriptive message
git commit -m "Add Prometheus, Grafana, and Actuator monitoring for all microservices"

# Push to main branch (triggers GitHub Actions)
git push origin main
```

**What happens automatically:**
- GitHub Actions workflow (`docker-build.yml`) triggers
- Builds all 5 services in parallel
- Each service now includes:
  - `spring-boot-starter-actuator`
  - `micrometer-registry-prometheus`
  - Actuator endpoints configured
- Pushes to Docker Hub:
  - `arjansubedi/emailservice:latest`
  - `arjansubedi/queueservice:latest`
  - `arjansubedi/imageworker:latest`
  - `arjansubedi/taskqueue:latest`
  - `arjansubedi/taskdbservice:latest`

---

### **Step 2: Monitor Build Progress**

```bash
# Check GitHub Actions
# Go to: https://github.com/YOUR_REPO/actions

# Or watch Docker Hub
# Go to: https://hub.docker.com/u/arjansubedi
```

**Wait for:** All 5 builds to complete (~5 minutes in parallel)

**You'll see:**
```
✅ EmailService (Port 8004)
✅ QueueService (Port 8001)
✅ ImageWorker (Port 8003)
✅ TaskQueue (Port 8000)
✅ TaskDBService (Port 8002)
```

---

### **Step 3: Update Deployments with New Images**

Once builds complete, update the running pods:

```bash
# Restart all deployments to pull new images
kubectl rollout restart deployment -n backend email-service
kubectl rollout restart deployment -n backend queue
kubectl rollout restart deployment -n backend image-worker
kubectl rollout restart deployment -n backend task-db-service

# Note: task-queue deployment might have different name, check:
kubectl get deployments -n backend

# Watch the rollout
kubectl get pods -n backend -w
```

**Expected:** Pods will terminate old ones and create new ones with `latest` images.

---

### **Step 4: Verify Actuator Endpoints**

Once new pods are running, test if Actuator is working:

```bash
# Test email-service (port 8004)
kubectl port-forward -n backend svc/email-service 8004:8004 &
curl http://localhost:8004/actuator/health
curl http://localhost:8004/actuator/prometheus | head -20

# Test task-queue-service (port 8000)
kubectl port-forward -n backend svc/task-queue-service 8000:8000 &
curl http://localhost:8000/actuator/health
curl http://localhost:8000/actuator/prometheus | head -20

# Kill port-forwards
pkill -f "port-forward"
```

**Expected Response:**

**Health endpoint:**
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

**Prometheus endpoint:**
```
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="G1 Survivor Space",} 1.234567E7
...
```

---

### **Step 5: Check Prometheus is Scraping**

```bash
# Port-forward Prometheus
kubectl port-forward -n monitoring svc/prometheus-app-kube-promet-prometheus 9090:9090 &

# Open browser: http://localhost:9090
# Go to: Status → Targets
```

**Look for:**
```
serviceMonitor/backend/email-service-monitor/0      UP
serviceMonitor/backend/task-queue-monitor/0         UP  
serviceMonitor/backend/queue-service-monitor/0      UP
serviceMonitor/backend/image-worker-monitor/0       UP
serviceMonitor/backend/task-db-service-monitor/0    UP
```

**If DOWN:** Check logs:
```bash
kubectl logs -n monitoring prometheus-prometheus-app-kube-promet-prometheus-0
```

---

### **Step 6: Access Grafana Dashboard**

```bash
# Port-forward Grafana
kubectl port-forward -n monitoring svc/prometheus-app-grafana 3000:80

# Open browser: http://localhost:3000
# Login: admin / admin
```

**Navigate to:**
1. Left sidebar → **Dashboards** → **Browse**
2. Look for: **"Spring Boot Microservices Metrics"**
3. Click to open

**You should see:**
- 📊 HTTP Request Rate (live)
- ⏱️ HTTP Request Duration P95
- 🔋 JVM Heap Usage
- 💾 JVM Memory Usage
- 🧵 JVM Threads
- ✅ Service Health (all showing UP)

---

### **Step 7: Generate Some Load (Optional)**

To see metrics flowing, generate some requests:

```bash
# Port-forward task-queue service
kubectl port-forward -n backend svc/task-queue-service 8000:8000 &

# Generate requests
for i in {1..100}; do
  curl -s http://localhost:8000/actuator/health > /dev/null
  echo "Request $i sent"
  sleep 0.5
done

# Watch metrics update in Grafana
```

---

### **Step 8: Test Full Service Functionality**

```bash
# Check all pods are running
kubectl get pods -n backend

# Check all services
kubectl get svc -n backend

# Check ServiceMonitors
kubectl get servicemonitors -n backend

# Check Prometheus targets
# (In Prometheus UI at http://localhost:9090/targets)
```

---

## 🔍 Verification Checklist

After deployment, verify:

- [ ] GitHub Actions build completed successfully
- [ ] All 5 Docker images pushed to Docker Hub
- [ ] All backend pods restarted with new images
- [ ] Actuator `/actuator/health` endpoint responds
- [ ] Actuator `/actuator/prometheus` returns metrics
- [ ] ServiceMonitors exist in `backend` namespace
- [ ] Prometheus shows all targets as **UP**
- [ ] Grafana dashboard loads
- [ ] Grafana dashboard shows live metrics
- [ ] No errors in pod logs

---

## 🛠️ Quick Commands Reference

```bash
# Check cluster status
kubectl get nodes
kubectl get namespaces

# Check backend
kubectl get pods -n backend
kubectl get svc -n backend
kubectl logs -n backend <pod-name>

# Check monitoring
kubectl get pods -n monitoring
kubectl get servicemonitors -n backend
kubectl get configmaps -n monitoring | grep dashboard

# Port forwards (use separate terminals)
kubectl port-forward -n monitoring svc/prometheus-app-grafana 3000:80
kubectl port-forward -n monitoring svc/prometheus-app-kube-promet-prometheus 9090:9090
kubectl port-forward -n backend svc/task-queue-service 8000:8000
kubectl port-forward -n backend svc/email-service 8004:8004

# Restart deployments (after new images)
kubectl rollout restart deployment -n backend <deployment-name>
kubectl rollout status deployment -n backend <deployment-name>

# Check rollout history
kubectl rollout history deployment -n backend email-service

# View logs
kubectl logs -n backend -l app=email-service --tail=50 -f
```

---

## ❌ Troubleshooting

### **Problem: Actuator endpoints return 404**

**Cause:** Still using old images without Actuator

**Solution:**
```bash
# Check image tag
kubectl get deployment -n backend email-service -o jsonpath='{.spec.template.spec.containers[0].image}'

# Force pull new image
kubectl rollout restart deployment -n backend email-service
```

---

### **Problem: Prometheus targets show DOWN**

**Cause:** Services don't have Actuator or wrong port

**Solution:**
```bash
# Check if service endpoint exists
kubectl exec -n backend <pod-name> -- wget -O- http://localhost:8004/actuator/prometheus

# Check ServiceMonitor config
kubectl get servicemonitor email-service-monitor -n backend -o yaml

# Check if service has correct labels
kubectl get svc email-service -n backend -o yaml | grep -A5 labels
```

---

### **Problem: Grafana dashboard is empty**

**Cause:** No metrics data yet or wrong queries

**Solution:**
```bash
# Check if metrics exist in Prometheus
# Go to: http://localhost:9090
# Run query: up{namespace="backend"}

# Should return 5 services

# Check Grafana logs
kubectl logs -n monitoring deployment/prometheus-app-grafana
```

---

### **Problem: GitHub Actions build fails**

**Cause:** Maven build errors or Docker issues

**Solution:**
```bash
# Check GitHub Actions logs
# Go to: https://github.com/YOUR_REPO/actions

# Common fixes:
# 1. Parent POM has correct dependencies
# 2. application.properties syntax is correct
# 3. Docker Hub credentials are set in GitHub secrets
```

---

## 🎉 Success Criteria

You know it's working when:

1. ✅ All pods show `Running` status
2. ✅ Actuator endpoints return data (health, metrics, prometheus)
3. ✅ Prometheus UI shows all 5 targets as **UP**
4. ✅ Grafana dashboard displays live metrics
5. ✅ HTTP requests show up in "Request Rate" panel
6. ✅ JVM metrics show memory/heap usage
7. ✅ Service health gauges show **green** (value = 1)

---

## 📊 Expected Metrics Flow

```
┌─────────────────┐
│ Microservices   │
│ /actuator/      │
│ prometheus      │
└────────┬────────┘
         │ Every 30s
         ▼
┌─────────────────┐
│  Prometheus     │
│  Scrapes &      │
│  Stores         │
└────────┬────────┘
         │ PromQL
         ▼
┌─────────────────┐
│   Grafana       │
│   Visualizes    │
└─────────────────┘
```

---

## 🚀 Next Steps After Verification

Once everything works:

1. **Configure Alerts** - Set up Alertmanager rules
2. **Add Custom Metrics** - Instrument your code with Micrometer
3. **Create More Dashboards** - JVM, Redis, PostgreSQL specific
4. **Set up Logging** - Add ELK/Loki stack
5. **Add Tracing** - Integrate Jaeger/Tempo

---

## 📚 Resources

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3000 (admin/admin)
- **GitHub Actions**: https://github.com/YOUR_REPO/actions
- **Docker Hub**: https://hub.docker.com/u/arjansubedi

---

## ✅ Summary

**Current State:**
- Cluster is running ✅
- ServiceMonitors created ✅
- Dashboard created ✅
- Old images running ⚠️

**Action Required:**
1. **Push code** → Triggers GitHub Actions
2. **Wait for builds** → New images with Actuator
3. **Restart deployments** → Pods use new images
4. **Verify & test** → End-to-end monitoring works

**Total Time:** ~10 minutes (5min build + 5min deployment)

Let's get it done! 🎉

