# Troubleshooting guide

Diagnosing problems with the Country Information Service on any Kubernetes cluster.

Commands assume the default namespace `country-info`; change `-n` if you deployed elsewhere.

## Contents

- [First commands to run](#first-commands-to-run)
- [Pod will not start](#pod-will-not-start)
- [Database connectivity problems](#database-connectivity-problems)
- [Autoscaling](#autoscaling)
- [Performance and resources](#performance-and-resources)
- [Tracing a specific request](#tracing-a-specific-request)

## First commands to run

Start here regardless of the symptom.

```bash
kubectl get pods -n country-info
kubectl describe pod -n country-info <pod-name>
kubectl logs -n country-info <pod-name>
kubectl get events -n country-info --sort-by=.lastTimestamp
```

For a pod that has already restarted, the interesting logs are from the previous container:

```bash
kubectl logs -n country-info <pod-name> --previous
```

`STATUS` tells you where to look next:

| Status | Likely cause | Section |
|---|---|---|
| `ImagePullBackOff`, `ErrImagePull`, `ErrImageNeverPull` | Cluster cannot obtain the image | [below](#image-cannot-be-pulled) |
| `CrashLoopBackOff` | Application fails during startup | [below](#crashloopbackoff) |
| `CreateContainerConfigError` | Missing ConfigMap or Secret | [below](#createcontainerconfigerror) |
| `Pending` | No node can schedule the pod | [below](#pod-stuck-in-pending) |
| `Running` but `0/1` | Readiness probe failing | [below](#pod-running-but-never-ready) |
| `OOMKilled` | Memory limit too low | [below](#pod-oomkilled) |

---

## Pod will not start

### Image cannot be pulled

```bash
kubectl describe pod -n country-info <pod-name> | tail -20
```

The event message distinguishes the causes.

**`repository does not exist` or `manifest unknown`**: the image name or tag is wrong. Check what
the Deployment actually asks for:

```bash
kubectl get deployment country-information -n country-info \
  -o jsonpath='{.spec.template.spec.containers[0].image}'
```

**`unauthorized` or `authentication required`**: the registry is private and the pod has no
credentials. Create a pull secret and reference it:

```bash
kubectl create secret docker-registry registry-credentials \
  --docker-server=<registry> --docker-username=<user> --docker-password=<token> \
  -n country-info
```

```yaml
spec:
  imagePullSecrets:
    - name: registry-credentials
```

### CrashLoopBackOff

Read the logs of the failed container first:

```bash
kubectl logs -n country-info <pod-name> --previous
```

**`FileNotFoundException: /mnt/configmap/logback/logback.xml`**

The most common cause. The `prod` profile reads its logging config from that path and has no
classpath fallback, so the application exits immediately if the ConfigMap is not mounted.

```bash
kubectl get configmap logback-configmap -n country-info
```

If it is missing:

```bash
kubectl apply -f k8s/logback-configmap.yml
kubectl rollout restart deployment/country-information -n country-info
```

If it exists, check the volume and mount still agree in `app-deployment.yml`: the volume name
`logback-mount`, the ConfigMap name `logback-configmap`, and the mount path
`/mnt/configmap/logback/`.

**`Could not resolve placeholder 'DB_URL'`** (or `DB_USERNAME`, `DB_PASSWORD`, `SOAP_ENDPOINT`)

These have no defaults by design, so a missing value stops startup instead of producing a
half-configured service. Verify what the container actually received:

```bash
kubectl exec -n country-info <pod-name> -- env | sort | grep -E "DB_|SOAP_|SPRING_"
```

Then check the ConfigMap and Secret exist and are referenced correctly in `envFrom`.

**`Schema-validation: missing table [countries]`**

`DDL_AUTO` is `validate`, so Hibernate expects the schema to already exist. Either create it with
a migration tool, or start once with `DDL_AUTO=update`:

```bash
kubectl set env deployment/country-information -n country-info DDL_AUTO=update
# once the pods are healthy, put it back
kubectl set env deployment/country-information -n country-info DDL_AUTO=validate
```

**`Invalid value for configuration property`**

The properties classes are `@Validated`, so an out-of-range or blank value fails fast. The message
names the offending key, so correct it in `configmap.yml`.

### CreateContainerConfigError

A referenced ConfigMap or Secret does not exist:

```bash
kubectl get configmap,secret -n country-info
```

You should see `country-information-configmap`, `logback-configmap` and
`country-information-secret`. This usually means the manifests were applied out of order. The
namespace, ConfigMaps and Secret must exist before the Deployment.

### Pod stuck in Pending

```bash
kubectl describe pod -n country-info <pod-name> | tail -20
```

**`Insufficient cpu` / `Insufficient memory`**: the node cannot satisfy the pod's request. Because
the Deployment sets limits without requests, Kubernetes copies the limits into the requests, so
each pod reserves 500m CPU and 1Gi memory. Lower the limits in `app-deployment.yml`, reduce
`replicas`, or add node capacity.

**`node(s) had untolerated taint`**: add the matching toleration, or target a different node pool.

**`pod has unbound immediate PersistentVolumeClaims`**: not applicable here, because this
application uses no PVCs. If you see it, something else in the namespace is at fault.

### Pod Running but never Ready

The readiness probe is failing. Check it from inside the pod:

```bash
kubectl exec -n country-info <pod-name> -- wget -qO- localhost:8080/actuator/health/readiness
```

If the application is simply slow to start, the startup probe allows 20s plus 18 x 10s, about
three minutes. A JVM exceeding that is usually blocked on something external, most often the
database. Check the logs for connection errors.

### Pod OOMKilled

```bash
kubectl describe pod -n country-info <pod-name> | grep -A3 "Last State"
```

The container exceeded its memory limit. The image sets `-XX:MaxRAMPercentage=75`, so with a 1Gi
limit the heap grows to roughly 768Mi and the rest is JVM overhead. Either raise the limit in
`app-deployment.yml` or lower the percentage. Consider `DB_POOL_SIZE` too, since each connection
carries buffers.

---

## Database connectivity problems

### Connection refused or communications link failure

Check what the pod is actually pointed at:

```bash
kubectl exec -n country-info <pod-name> -- env | grep DB_URL
```

**`localhost` will never work**, because inside a pod it refers to the pod itself. Use:

| Where MySQL runs | Host |
|---|---|
| Managed service | The provider's DNS endpoint |
| In the same cluster | `<service>.<namespace>.svc.cluster.local` |
| Elsewhere | Its DNS name or IP |

Test resolution and reachability from inside the cluster:

```bash
kubectl run netcheck --rm -it --restart=Never -n country-info \
  --image=busybox -- sh -c "nslookup <db-host>; nc -zv <db-host> 3306"
```

If DNS resolves but the port is closed, the path from the pod network to the database is blocked.
Check firewalls, security groups, and any NetworkPolicy in the namespace.

### Access denied for user

The credentials in the Secret do not match the database:

```bash
kubectl get secret country-information-secret -n country-info \
  -o jsonpath='{.data.DB_USERNAME}' | base64 -d
```

The account must also be permitted from the pod's address; one restricted to `localhost` will not
accept a connection from a pod.

### Unknown database

`DB_URL` names a schema that does not exist on the server. Correct the URL, or have the schema
created. See the deployment guide.

### Schema-validation: missing table

The tables have not been created. See [CrashLoopBackOff](#crashloopbackoff) above, and
*Database connectivity and schema* in [DEPLOYMENT.md](DEPLOYMENT.md).

### Connection pool timeouts under load

`HikariPool-1 - Connection is not available, request timed out` means the pool is exhausted.
`DB_POOL_SIZE` x replicas must stay under the server's `max_connections`. Lower `DB_POOL_SIZE`,
reduce replicas, or have the server limit raised.

---

## Autoscaling

### HPA shows `<unknown>` targets

```bash
kubectl get hpa -n country-info
kubectl top pods -n country-info
```

If `kubectl top` errors, metrics-server is not installed or not ready, and the HPA cannot scale
without it.

```bash
kubectl get deployment metrics-server -n kube-system
```

Some managed clusters use a different metrics provider; check your provider's documentation.

### HPA will not scale past a point

```bash
kubectl describe hpa country-information -n country-info
```

`maxReplicas` in `hpa.yml` is the ceiling. Also note that utilisation is a percentage of the CPU
*request*, which equals the limit (500m) because no explicit request is set, so 70% means 350m,
not 70% of a smaller baseline.

If replicas flap, the stabilisation window may need tuning via `spec.behavior`.

---

## Performance and resources

```bash
kubectl top pods -n country-info
```

A lookup for a country already in the database makes **no SOAP call**. It is a single indexed
read. If repeat lookups are slow, the problem is the database or the connection pool, not the
upstream.

Slow *first* lookups are dominated by the two SOAP round trips. `SOAP_CONNECT_TIMEOUT` and
`SOAP_READ_TIMEOUT` bound these at 5s and 10s; with retries, a worst-case failing request takes
about three attempts with exponential backoff before returning 502.

---

## Tracing a specific request

Every response carries `X-Correlation-Id`, and every log line includes it. You can supply your own:

```bash
curl -H "X-Correlation-Id: my-trace-123" \
  -X POST http://localhost:18080/api/countries \
  -H "Content-Type: application/json" -d '{"name":"Kenya"}'
```

Then search across all replicas:

```bash
kubectl logs -n country-info -l app=country-information --tail=-1 | grep my-trace-123
```

This is the fastest way to turn a user-reported failure into the exact stack trace, especially with
multiple replicas where logs interleave.

Follow logs live:

```bash
kubectl logs -n country-info -l app=country-information -f --max-log-requests=10
```

If your cluster ships logs to a central system, the correlation ID is the field to search on there
too.

