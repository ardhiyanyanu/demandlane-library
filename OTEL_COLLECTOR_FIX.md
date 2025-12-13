# OTEL Collector Configuration Fix

## ✅ Problem Solved

**Error Message:**
```
collector server run finished with error: failed to get config: 
cannot resolve the configuration: scheme "ENVIRONMENT" is not supported 
for uri "ENVIRONMENT:production"
```

## Root Cause

The OpenTelemetry Collector uses a specific syntax for environment variable expansion: `${env:VARIABLE_NAME}`, not the standard `${VARIABLE_NAME:default}` syntax.

## Changes Made

### 1. Fixed otel-collector-config.yaml

All environment variable references were updated to use the correct `${env:VAR}` syntax:

#### Before (❌ Incorrect):
```yaml
deployment.environment:
  value: ${ENVIRONMENT:production}  # Wrong!
  
endpoint: ${GRAFANA_PROMETHEUS_ENDPOINT}  # Wrong!
Authorization: "Basic ${GRAFANA_PROMETHEUS_AUTH}"  # Wrong!
```

#### After (✅ Correct):
```yaml
deployment.environment:
  value: ${env:ENVIRONMENT}  # Correct!
  
endpoint: ${env:GRAFANA_PROMETHEUS_ENDPOINT}  # Correct!
Authorization: ${env:GRAFANA_PROMETHEUS_AUTH}  # Correct!
```

### 2. Updated docker-compose.yml

Added the `ENVIRONMENT` variable to the otel-collector service environment section:

```yaml
environment:
  # ...existing vars...
  - ENVIRONMENT=${ENVIRONMENT:-production}  # Added this line
  - SERVICE_VERSION=${SERVICE_VERSION:-1.0.0}
```

## Fixed Locations

### otel-collector-config.yaml

1. **Line ~30** - `resource` processor:
   ```yaml
   - key: deployment.environment
     value: ${env:ENVIRONMENT}  # Fixed
   - key: service.version
     value: ${env:SERVICE_VERSION}  # Fixed
   ```

2. **Line ~46** - Prometheus exporter:
   ```yaml
   prometheusremotewrite:
     endpoint: ${env:GRAFANA_PROMETHEUS_ENDPOINT}  # Fixed
     headers:
       Authorization: ${env:GRAFANA_PROMETHEUS_AUTH}  # Fixed
   ```

3. **Line ~52** - Tempo exporter:
   ```yaml
   otlp/tempo:
     endpoint: ${env:GRAFANA_TEMPO_ENDPOINT}  # Fixed
     headers:
       Authorization: ${env:GRAFANA_TEMPO_AUTH}  # Fixed
   ```

4. **Line ~58** - Loki exporter:
   ```yaml
   loki:
     endpoint: ${env:GRAFANA_LOKI_ENDPOINT}  # Fixed
     headers:
       Authorization: ${env:GRAFANA_LOKI_AUTH}  # Fixed
   ```

5. **Line ~78** - Prometheus local exporter:
   ```yaml
   prometheus:
     const_labels:
       environment: ${env:ENVIRONMENT}  # Fixed
   ```

### docker-compose.yml

Added `ENVIRONMENT` variable to otel-collector service:
```yaml
environment:
  - ENVIRONMENT=${ENVIRONMENT:-production}  # Added
```

## Environment Variable Syntax Reference

### ❌ WRONG (causes error):
```yaml
${VARIABLE_NAME}                    # Missing env: prefix
${VARIABLE_NAME:default}            # Shell syntax, not OTEL syntax
"Basic ${VARIABLE_NAME}"            # Quoted incorrectly
```

### ✅ CORRECT:
```yaml
${env:VARIABLE_NAME}                # Correct OTEL syntax
${env:VARIABLE_NAME}                # No default values supported
${env:VAR1}${env:VAR2}             # Multiple vars can be concatenated
```

## Testing the Fix

### 1. Verify configuration syntax:
```bash
# Check YAML is valid
python3 -c "import yaml; yaml.safe_load(open('otel-collector-config.yaml'))"

# Validate docker-compose
docker-compose config
```

### 2. Start the collector:
```bash
# Start all services
docker-compose up -d

# Check collector logs
docker logs lib-otel-collector

# Should see:
# "Everything is ready. Begin running and processing data."
```

### 3. Verify collector is healthy:
```bash
# Check health endpoint
curl http://localhost:13133/health

# Expected response:
# {"status":"Server available","upSince":"..."}
```

### 4. Check metrics are being collected:
```bash
# Check collector's own metrics
curl http://localhost:8888/metrics | grep otelcol

# Check exported metrics
curl http://localhost:8889/metrics | grep library
```

## Expected Environment Variables

The following environment variables must be set (in `.env` file):

```bash
# Required for Grafana Cloud
GRAFANA_PROMETHEUS_ENDPOINT=https://prometheus-prod-52-prod-ap-southeast-2.grafana.net/api/prom/push
GRAFANA_PROMETHEUS_AUTH=Basic <base64-encoded-credentials>
GRAFANA_TEMPO_ENDPOINT=https://tempo-prod-23-prod-ap-southeast-2.grafana.net/tempo
GRAFANA_TEMPO_AUTH=Basic <base64-encoded-credentials>
GRAFANA_LOKI_ENDPOINT=https://logs-prod-032.grafana.net/loki/api/v1/push
GRAFANA_LOKI_AUTH=Basic <base64-encoded-credentials>

# Optional (have defaults)
ENVIRONMENT=production           # Defaults to "production"
SERVICE_VERSION=1.0.0           # Defaults to "1.0.0"
```

## Why This Happened

The OpenTelemetry Collector has its own configuration parser that doesn't use standard shell variable expansion. It requires the explicit `env:` prefix to identify environment variables.

### Common Mistakes:
1. Using shell syntax: `${VAR:-default}` ❌
2. Missing `env:` prefix: `${VAR}` ❌
3. Using quotes incorrectly: `"${env:VAR}"` (sometimes problematic)

### Best Practices:
1. Always use: `${env:VAR}` ✅
2. Set defaults in docker-compose: `ENVIRONMENT=${ENVIRONMENT:-production}` ✅
3. Keep quotes only when needed for string concatenation
4. Test configuration with `docker-compose config`

## Verification Steps

✅ **YAML Syntax**: Valid  
✅ **Docker Compose**: Valid  
✅ **Environment Variables**: Properly referenced  
✅ **All Exporters**: Fixed  
✅ **Resource Attributes**: Fixed  
✅ **Labels**: Fixed  

## Summary

The fix involved updating all environment variable references in `otel-collector-config.yaml` from `${VARIABLE}` to `${env:VARIABLE}` and ensuring the `ENVIRONMENT` variable is passed to the container in `docker-compose.yml`.

The OTEL collector should now start successfully without configuration errors! 🎉

