# Deployment guide

How to build the image and deploy the Country Information Service to a Kubernetes cluster.

## Contents

- [What gets deployed](#what-gets-deployed)
- [Prerequisites](#prerequisites)
- [Before you deploy: three values you must set](#before-you-deploy-three-values-you-must-set)
- [Deploying](#deploying)
- [Making the image available to the cluster](#making-the-image-available-to-the-cluster)
- [Configuration reference](#configuration-reference)
- [Database connectivity and schema](#database-connectivity-and-schema)
- [Verifying the deployment](#verifying-the-deployment)
- [Updating and rolling back](#updating-and-rolling-back)
- [Tearing down](#tearing-down)

## What gets deployed

Everything lives in the `country-info` namespace. MySQL is treated as an external or managed
database. Kubernetes runs only the stateless application.

| File | Resource | Purpose |
|---|---|---|
| `namespace.yml` | Namespace | `country-info` |
| `logback-configmap.yml` | ConfigMap | Logging config mounted at `/mnt/configmap/logback/` |
| `configmap.yml` | ConfigMap | Non-sensitive environment variables |
| `app-secret.yml` | Secret | `DB_USERNAME`, `DB_PASSWORD` |
| `app-deployment.yml` | Deployment | 2 replicas, startup/readiness/liveness probes |
| `app-service.yml` | Service | ClusterIP on port 8080, named port `http` |
| `app-ingress.yml` | Ingress | Host-based routing |
| `hpa.yml` | HorizontalPodAutoscaler | CPU-based |

**Apply order matters.** The namespace must exist first, and the ConfigMaps and Secret must exist
before the Deployment or the pods will fail to start. `deploy.ps1` handles this.

## Prerequisites

- Docker, to build the image
- `kubectl` configured for your target cluster
- A container registry the cluster can pull from (or a local cluster, see below)
- A reachable MySQL 8.4 instance, with credentials and a schema (see
  [Database connectivity and schema](#database-connectivity-and-schema))
- An ingress controller, if you want external access
- metrics-server, if you want the HPA to work

Confirm you are pointed at the right cluster before you start:

```bash
kubectl config get-contexts
kubectl config current-context
```

## Before you deploy: three values you must set

The manifests ship with placeholder values that are **not** correct for an arbitrary cluster.
Change these first.

### 1. Container image: `k8s/app-deployment.yml`

```yaml
image: country-information:latest
imagePullPolicy: IfNotPresent
```

The committed value is a bare local tag that a cluster cannot pull. **Push the image to a registry
and use a fully qualified reference**, which the kubelet pulls automatically when the pod is
scheduled:

```yaml
image: registry.example.com/myproject/country-information:v1
imagePullPolicy: IfNotPresent
```

See [Making the image available to the cluster](#making-the-image-available-to-the-cluster) for
both options in full.

Add `imagePullSecrets` to the pod spec if the registry is private:

```yaml
spec:
  imagePullSecrets:
    - name: registry-credentials
  containers:
    - name: country-information
      ...
```

```bash
kubectl create secret docker-registry registry-credentials \
  --docker-server=registry.example.com \
  --docker-username=<user> \
  --docker-password=<token> \
  -n country-info
```

`deploy.ps1` overrides the image automatically via `kubectl set image`, so with the script you
only need to edit the manifest if you deploy by hand.

### 2. Ingress class and host: `k8s/app-ingress.yml`

```yaml
spec:
  ingressClassName: traefik
  rules:
    - host: country-information.example.com
```

`ingressClassName` must match a class your cluster actually has. Check:

```bash
kubectl get ingressclass
```

Common values are `nginx`, `traefik`, `alb`, `gce` and `azure-application-gateway`. Set the `host`
to a DNS name that resolves to your ingress controller. If you do not need external access, delete
the Ingress and reach the Service with `kubectl port-forward`.

Some controllers need annotations, for example `nginx.ingress.kubernetes.io/*` for ingress-nginx,
or `alb.ingress.kubernetes.io/*` for the AWS Load Balancer Controller. Add them to the Ingress
metadata as your controller requires.

### 3. Database connection: `k8s/configmap.yml` and `k8s/app-secret.yml`

```yaml
DB_URL: jdbc:mysql://<host>:3306/country_information
```

Set this to a hostname the pods can resolve. See
[Database connectivity and schema](#database-connectivity-and-schema).

Put the real credentials in the Secret. The committed file contains a throwaway value:

```bash
kubectl create secret generic country-information-secret \
  --from-literal=DB_USERNAME=country \
  --from-literal=DB_PASSWORD='<real-password>' \
  -n country-info --dry-run=client -o yaml | kubectl apply -f -
```

> Do not commit real credentials. Use a sealed secret, an external secret store, or create the
> Secret out of band as above.

## Deploying

### With the script

```powershell
# build, push to a registry and deploy
.\deploy.ps1 -Registry registry.example.com/myproject -Tag v1

# re-apply manifests only, no rebuild
.\deploy.ps1 -SkipBuild

# target a specific context and namespace
.\deploy.ps1 -Context prod-cluster -Namespace country-info -Registry registry.example.com/myproject
```

The script builds the image, pushes it to the registry, applies the manifests in order,
points the Deployment at the image and waits for the rollout. If the rollout fails it prints pod
status and the last 50 log lines before exiting.

### Manual deployment

```bash
docker build -t registry.example.com/myproject/country-information:v1 .
docker push registry.example.com/myproject/country-information:v1
```

Then, in this order:

```bash
kubectl apply -f k8s/namespace.yml
kubectl apply -f k8s/logback-configmap.yml
kubectl apply -f k8s/configmap.yml
kubectl apply -f k8s/app-secret.yml
kubectl apply -f k8s/app-deployment.yml
kubectl apply -f k8s/app-service.yml
kubectl apply -f k8s/app-ingress.yml
kubectl apply -f k8s/hpa.yml

kubectl rollout status deployment/country-information -n country-info --timeout=300s
```

Validate without applying:

```bash
kubectl apply --dry-run=server -f k8s/
```

## Making the image available to the cluster

The kubelet on each node must be able to resolve the image named in the Deployment. Push the image
to a registry the cluster can reach, and reference it by its full name. The kubelet pulls it
automatically when the pod is scheduled. There is no manual import step.

```bash
docker build -t registry.example.com/myproject/country-information:v1 .
docker push registry.example.com/myproject/country-information:v1
```

Then point the Deployment at that exact reference:

```yaml
image: registry.example.com/myproject/country-information:v1
```

That is the whole process. If the registry is private, give the pod credentials with
`imagePullSecrets` as shown in [1. Container image](#1-container-image-k8sapp-deploymentyml);
the pull still happens automatically, it just authenticates first.

With the script:

```powershell
.\deploy.ps1 -Registry registry.example.com/myproject -Tag v1
```

## Configuration reference

### From `configmap.yml`

| Variable | Shipped value | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `DB_URL` | *environment specific* | JDBC URL |
| `DB_POOL_SIZE` | `20` | Hikari maximum pool size |
| `DDL_AUTO` | `validate` | Hibernate schema handling |
| `SOAP_ENDPOINT` | public CountryInfoService URL | Upstream endpoint |
| `SOAP_CONNECT_TIMEOUT` | `5s` | Connect timeout |
| `SOAP_READ_TIMEOUT` | `10s` | Read timeout |
| `RESILIENCE_MAX_ATTEMPTS` | `3` | Total retry attempts |
| `RESILIENCE_BACKOFF` | `500ms` | Initial retry backoff |
| `RESILIENCE_BACKOFF_MULTIPLIER` | `2.0` | Backoff growth factor |
| `RESILIENCE_FAILURE_RATE_THRESHOLD` | `50` | Percent failures that open the breaker |
| `RESILIENCE_WAIT_DURATION_OPEN` | `30s` | Time the breaker stays open |
| `RESILIENCE_SLIDING_WINDOW_SIZE` | `10` | Breaker window size |
| `RESILIENCE_MIN_CALLS` | `5` | Calls before the breaker evaluates |
| `RESILIENCE_PERMITTED_CALLS_HALF_OPEN` | `3` | Trial calls when half-open |
| `SWAGGER_ENABLED` | `false` | Swagger UI and `/v3/api-docs` |

### From `app-secret.yml`

| Variable | Purpose |
|---|---|
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |

### Logging

The `prod` profile reads its logging config from `/mnt/configmap/logback/logback.xml`, supplied by
`logback-configmap.yml`. **If that ConfigMap is not mounted the container will not start.** There
is no classpath fallback. The failure is deliberate and immediate.

To change log levels, edit the ConfigMap and restart:

```bash
kubectl apply -f k8s/logback-configmap.yml
kubectl rollout restart deployment/country-information -n country-info
```

The ConfigMap pins `SoapLoggingInterceptor` to `info`, so SOAP envelopes are not logged by default.
Set it to `debug` if you need to see them, then set it back, because they are verbose.

## Database connectivity and schema

The database is external to this deployment. Provisioning, hosting and operating the MySQL server
are out of scope. The application only needs a reachable instance, credentials, and a schema that
matches its entities.

### Pointing `DB_URL` at the server

`localhost` never works: inside a pod it refers to the pod itself.

| Where MySQL runs | Host to use |
|---|---|
| Managed service (RDS, Cloud SQL, Azure Database) | The provider's DNS endpoint |
| In the same cluster | `<service>.<namespace>.svc.cluster.local` |
| Elsewhere, reachable by DNS or IP | That DNS name or IP |

For an external database you can also create a Service without a selector so pods address it by a
stable in-cluster name:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: mysql
  namespace: country-info
spec:
  type: ExternalName
  externalName: mysql.prod.example.com
```

Then `DB_URL: jdbc:mysql://mysql:3306/country_information`.

The account in `app-secret.yml` needs privileges on the target schema and must be permitted from
the pod network. An account restricted to `localhost` will not accept a connection from a pod.

### Schema and tables

The application creates two tables:

| Table | Contents |
|---|---|
| `countries` | One row per country; unique on `iso_code`, indexed on `name` |
| `languages` | One row per language, foreign key `country_id` to `countries` |

If you prefer to create them explicitly:

```sql
CREATE TABLE countries (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    iso_code          VARCHAR(3)   NOT NULL,
    name              VARCHAR(150) NOT NULL,
    capital_city      VARCHAR(150),
    phone_code        VARCHAR(20),
    continent_code    VARCHAR(10),
    currency_iso_code VARCHAR(10),
    flag_url          VARCHAR(500),
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_countries_iso_code UNIQUE (iso_code),
    INDEX idx_countries_name (name)
);

CREATE TABLE languages (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    iso_code   VARCHAR(10),
    name       VARCHAR(150) NOT NULL,
    country_id BIGINT       NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_languages_country_id (country_id),
    CONSTRAINT fk_languages_country FOREIGN KEY (country_id) REFERENCES countries (id)
);
```

## Verifying the deployment

```bash
kubectl get pods,svc,ingress,hpa -n country-info
```

All pods should be `Running` and `1/1`. Then port-forward and check health:

```bash
kubectl port-forward -n country-info svc/country-information 18080:8080
```

```bash
curl http://localhost:18080/actuator/health
```

Expect `{"status":"UP",...}`. The overall status includes the database, so `UP` confirms the
connection works.

End-to-end check:

```bash
curl -X POST http://localhost:18080/api/countries \
  -H "Content-Type: application/json" \
  -d '{"name":"Kenya"}'
```

A `201` with a populated body confirms the SOAP chain and persistence both work from inside the
cluster. Repeating the call should return `200` without a SOAP round trip.

### Through the Ingress

```bash
kubectl get ingress -n country-info
```

Once `ADDRESS` is populated and DNS points at it:

```bash
curl https://country-information.example.com/actuator/health
```

To test before DNS exists, send the `Host` header explicitly:

```bash
curl -H "Host: country-information.example.com" http://<ingress-address>/actuator/health
```

## Updating and rolling back

```bash
docker build -t registry.example.com/myproject/country-information:v2 .
docker push registry.example.com/myproject/country-information:v2
kubectl set image deployment/country-information -n country-info \
  country-information=registry.example.com/myproject/country-information:v2
kubectl rollout status deployment/country-information -n country-info
```

Roll back:

```bash
kubectl rollout undo deployment/country-information -n country-info
kubectl rollout history deployment/country-information -n country-info
```

## Tearing down

```bash
kubectl delete namespace country-info
```

This removes every resource in one step. To remove only the workload and keep the configuration:

```bash
kubectl delete -f k8s/app-deployment.yml
```

If something goes wrong, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).
