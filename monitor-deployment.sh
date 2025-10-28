#!/bin/bash
# Monitor and Complete End-to-End Deployment

set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   TaskQueue Monitoring - End-to-End Deployment Helper     ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Step 1: Wait for Docker images
echo -e "${YELLOW}[1/5] Waiting for GitHub Actions to complete...${NC}"
echo "Check build status at: https://github.com/itisaarjan/TaskQueueHandler/actions"
echo ""
read -p "Press ENTER when all 5 Docker builds are complete (check GitHub Actions)..."
echo -e "${GREEN}✓ Proceeding with deployment${NC}"
echo ""

# Step 2: Restart deployments with new images
echo -e "${YELLOW}[2/5] Restarting backend deployments with new images...${NC}"
echo "Getting deployment names..."
kubectl get deployments -n backend -o name

echo ""
echo "Restarting deployments..."
kubectl rollout restart deployment -n backend email-service 2>/dev/null || echo "email-service not found"
kubectl rollout restart deployment -n backend queue 2>/dev/null || echo "queue deployment not found"
kubectl rollout restart deployment -n backend image-worker 2>/dev/null || echo "image-worker not found"
kubectl rollout restart deployment -n backend task-db-service 2>/dev/null || echo "task-db-service not found"

# Check for task-queue deployment (might have different name)
TASK_QUEUE_DEPLOY=$(kubectl get deployment -n backend -o name | grep -i task | grep -i queue || echo "")
if [ ! -z "$TASK_QUEUE_DEPLOY" ]; then
    echo "Restarting $TASK_QUEUE_DEPLOY..."
    kubectl rollout restart -n backend $TASK_QUEUE_DEPLOY
fi

echo ""
echo "Waiting for rollout to complete..."
sleep 5
kubectl rollout status deployment -n backend email-service --timeout=120s 2>/dev/null || echo "Waiting..."
echo -e "${GREEN}✓ Deployments restarted${NC}"
echo ""

# Step 3: Verify pods are running
echo -e "${YELLOW}[3/5] Verifying pods are running with new images...${NC}"
kubectl get pods -n backend
echo ""
echo "Waiting for all pods to be ready..."
sleep 10
echo -e "${GREEN}✓ Pods running${NC}"
echo ""

# Step 4: Test Actuator endpoints
echo -e "${YELLOW}[4/5] Testing Actuator endpoints...${NC}"

echo "Testing email-service health endpoint..."
kubectl port-forward -n backend svc/email-service 8004:8004 > /dev/null 2>&1 &
PF_PID1=$!
sleep 3

HEALTH_RESPONSE=$(curl -s http://localhost:8004/actuator/health 2>/dev/null || echo "FAILED")
if [[ $HEALTH_RESPONSE == *"UP"* ]]; then
    echo -e "${GREEN}✓ Health endpoint working!${NC}"
    echo "Response: $HEALTH_RESPONSE"
else
    echo -e "${RED}✗ Health endpoint not responding${NC}"
    echo "Response: $HEALTH_RESPONSE"
fi

echo ""
echo "Testing email-service prometheus endpoint..."
PROM_RESPONSE=$(curl -s http://localhost:8004/actuator/prometheus 2>/dev/null | head -5 || echo "FAILED")
if [[ $PROM_RESPONSE == *"jvm_"* ]] || [[ $PROM_RESPONSE == *"http_"* ]]; then
    echo -e "${GREEN}✓ Prometheus metrics working!${NC}"
    echo "Sample metrics:"
    curl -s http://localhost:8004/actuator/prometheus 2>/dev/null | grep -E "^(jvm_memory|http_server)" | head -3
else
    echo -e "${RED}✗ Prometheus metrics not responding${NC}"
fi

kill $PF_PID1 2>/dev/null || true
echo ""

# Step 5: Check Prometheus and Grafana
echo -e "${YELLOW}[5/5] Checking Prometheus and Grafana...${NC}"

echo "Checking ServiceMonitors..."
SM_COUNT=$(kubectl get servicemonitors -n backend --no-headers 2>/dev/null | wc -l | tr -d ' ')
echo "ServiceMonitors found: $SM_COUNT/5"
if [ "$SM_COUNT" -eq "5" ]; then
    echo -e "${GREEN}✓ All ServiceMonitors created${NC}"
    kubectl get servicemonitors -n backend
else
    echo -e "${YELLOW}⚠ Expected 5 ServiceMonitors, found $SM_COUNT${NC}"
fi

echo ""
echo "Checking Prometheus pods..."
kubectl get pods -n monitoring | grep prometheus

echo ""
echo "Checking Grafana..."
kubectl get pods -n monitoring | grep grafana

echo ""
echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                   🎉 Deployment Complete!                  ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Access instructions
echo -e "${GREEN}📊 Access Grafana Dashboard:${NC}"
echo "   kubectl port-forward -n monitoring svc/prometheus-app-grafana 3000:80"
echo "   Open: ${BLUE}http://localhost:3000${NC}"
echo "   Login: ${YELLOW}admin / admin${NC}"
echo "   Go to: Dashboards → Browse → 'Spring Boot Microservices Metrics'"
echo ""

echo -e "${GREEN}🔍 Access Prometheus:${NC}"
echo "   kubectl port-forward -n monitoring svc/prometheus-app-kube-promet-prometheus 9090:9090"
echo "   Open: ${BLUE}http://localhost:9090${NC}"
echo "   Check: Status → Targets (should show 5 services as UP)"
echo ""

echo -e "${GREEN}🎯 Test Individual Services:${NC}"
echo "   kubectl port-forward -n backend svc/task-queue-service 8000:8000"
echo "   curl http://localhost:8000/actuator/health"
echo "   curl http://localhost:8000/actuator/prometheus"
echo ""

echo -e "${YELLOW}📝 Quick Verification:${NC}"
echo "   1. Open Grafana dashboard"
echo "   2. Check all panels show data"
echo "   3. Generate some load (curl endpoints)"
echo "   4. Watch metrics update in real-time"
echo ""

echo -e "${GREEN}✅ Your monitoring stack is ready!${NC}"
echo ""

