# Architecture

A Spring Boot service that accepts a country name over REST, resolves it through the external
CountryInfoService SOAP API, stores the result in MySQL and exposes CRUD over the stored records.

## Contents

- [Layers](#layers)
- [The lookup pipeline](#the-lookup-pipeline)
- [Integration with the SOAP service](#integration-with-the-soap-service)
- [Failure handling](#failure-handling)
- [Error responses](#error-responses)
- [Observability](#observability)
- [Scalability](#scalability)
- [Key decisions and trade-offs](#key-decisions-and-trade-offs)

## Layers

The code follows MVC with one package per responsibility.

| Package | Responsibility |
|---|---|
| `controller` | HTTP concerns only: routing, status codes, request binding |
| `service` | Business pipeline and transaction boundaries |
| `repository` | Spring Data JPA persistence |
| `soap` | The external SOAP integration |
| `model` | JPA entities (`CountryInfo`, `Language`) |
| `dto` | Request, response and internal carrier records, plus the mapper |
| `exception` | Domain exceptions and the global handler |
| `config` | SOAP, resilience, correlation ID and OpenAPI wiring |
| `utils` | `CountryNameNormalizer` |

Dependencies point inwards: the controller depends on the service, the service depends on the
repository and the SOAP client. Nothing in the service layer knows about HTTP.

Two interfaces exist, placed at the points where the implementation could genuinely be swapped:

- `CountryService`, implemented by `CountryServiceImpl`
- `CountryInfoClient`, implemented by `CountryInfoSoapClient`

`CountryInfoClient` is the more important of the two. It lets the service layer be tested with a
mocked upstream, and it means replacing SOAP with a REST provider would not touch the service.

## The lookup pipeline

`POST /api/countries` runs five steps in `CountryServiceImpl.lookup`:

1. Normalize the submitted name. The upstream service is case sensitive, so `kenya` becomes
   `Kenya`.
2. Look the name up in the database. If it is already stored, return it and make no SOAP call.
3. Call `CountryISOCode` to resolve the name to an ISO code, for example `Kenya` to `KE`.
4. Call `FullCountryInfo` with that ISO code to fetch the full record.
5. Persist the country and its languages, then return the stored representation.

Step 2 is a read-through cache. A repeat lookup for a stored country costs one indexed query and
no network call. Step 3 has a second check against the ISO code, which catches the case where the
same country is reached under a different name, for example `Holland` resolving to `NL`.

The response is `201 Created` for a new record and `200 OK` for one already stored.

Steps 3 and 4 are the two chained SOAP calls the brief requires. They sit behind a single endpoint
rather than being exposed separately, because the second call is meaningless without the output of
the first.

## Integration with the SOAP service

The WSDL is compiled to JAXB types at build time by `jaxb2-maven-plugin`. That plugin generates
data types only and no service proxy, so the client is a hand written `WebServiceTemplate` in
`CountryInfoSoapClient`.

The upstream has three behaviours that shaped the client:

1. It is case sensitive, and every word must be capitalised. `kenya` and `South africa` are both
   rejected. Hence `CountryNameNormalizer`.
2. An unknown country returns HTTP 200 with a sentinel string, not a SOAP fault.
3. An invalid ISO code returns HTTP 200 with a blank result.

Because of points 2 and 3, the client cannot trust the status code. It validates the payload
against an ISO code pattern and raises `UnknownCountryException` when the response does not match,
which the handler turns into a 404.

## Failure handling

`CountryInfoSoapClient` wraps every call in a Resilience4j retry and circuit breaker:

```java
Retry.decorateSupplier(countryInfoRetry,
        CircuitBreaker.decorateSupplier(countryInfoCircuitBreaker, () -> send(request, operation)))
```

The retry is the outer decorator. That ordering is deliberate: each individual attempt is recorded
by the breaker, so a failing upstream trips it at the expected rate. If the breaker were outside,
three attempts would count as one call.

| Control | Default | Purpose |
|---|---|---|
| Connect and read timeouts | 5s and 10s | Stop a slow upstream from holding threads |
| Retry | 3 attempts, 500ms exponential backoff | Absorb transient faults |
| Circuit breaker | Opens at 50% failures over 10 calls, 30s open | Stop calling a service that is down |

`UnknownCountryException` is listed in `ignoreExceptions` for both. A country that does not exist
is a valid answer, not a fault, so it must neither be retried nor count against the breaker.

All values are configurable through the `RESILIENCE_*` environment variables.

## Error responses

`GlobalExceptionHandler` maps every failure to a status code and a consistent `ApiError` body.

| Condition | Status |
|---|---|
| Country not found, or unknown upstream | 404 |
| Validation failure, malformed body, bad path type | 400 |
| Circuit breaker open | 503 |
| SOAP transport failure | 502 |
| Anything unexpected | 500 |

The 500 case returns no internal detail. The cause is logged with the correlation ID instead, so
the client gets an identifier to quote rather than a stack trace.

## Observability

Logging is structured, with a correlation ID per request. `CorrelationIdFilter` places one in the
MDC, so every line produced while handling a request carries the same identifier, and it is
returned in the response body on errors.

Actuator exposes health, info, metrics and Prometheus. Liveness and readiness probes are enabled
separately, which matters in Kubernetes: readiness keeps traffic off a pod that cannot serve,
while liveness restarts one that is stuck.

Resilience4j is bound to Micrometer, so retry counts and breaker state transitions are visible as
metrics rather than only in logs.

## Scalability

The service is stateless. No session state, no in memory cache, no local files. Any pod can serve
any request, so scaling out is a matter of adding replicas. The database holds all state.

The read-through cache in step 2 is what protects the upstream under load. Once a country is
stored, repeat lookups never leave the cluster.

A HorizontalPodAutoscaler scales on CPU. The practical ceiling is the database rather than CPU:
each replica opens up to `DB_POOL_SIZE` connections, so pool size multiplied by replica count must
stay within the server limit.

## Key decisions and trade-offs

**Blocking SOAP calls with bounded resilience.** The application uses synchronous request
processing, which keeps the REST contract straightforward and returns a result in the same request.
The trade off is that each cold lookup occupies a servlet thread while the SOAP service responds.
Timeouts, bounded retries, and a circuit breaker limit the impact of upstream failures, but slow
calls can still reduce capacity for other requests. A bulkhead limiting concurrent SOAP calls is a
targeted improvement if load testing shows that upstream latency threatens overall service
availability. Asynchronous processing would be considered if the workload and API contract
justified its additional complexity.

**Circuit breaker state is local to each replica.** Each pod tracks upstream failures
independently, avoiding a shared state store and an additional runtime dependency. During an
outage, replicas may independently make calls before their circuits open. This is acceptable at the
expected replica count, provided timeouts, retry limits, and concurrency controls bound the
resulting traffic. Shared breaker state would be considered only if coordinated behaviour across a
substantially larger deployment were necessary.

**One circuit breaker protects both SOAP operations.** The country ISO lookup and full country
information lookup use the same upstream service and share a breaker. This reduces configuration
complexity and allows failures to trigger protection across the integration. The trade off is that
an operation specific failure can temporarily block both operations. Separate breakers would be
justified if monitoring showed materially different reliability or capacity characteristics between
the operations.

**MySQL is the durable store and the reuse mechanism.** Previously resolved country information is
persisted and reused, avoiding repeated SOAP calls without adding a distributed cache. This reduces
infrastructure and cache invalidation complexity. The trade off is that MySQL capacity and
availability constrain the service, and stored data is not refreshed automatically. Database
connection limits, backups, monitoring, and an appropriate availability strategy remain necessary
for production. Automatic refresh or a separate cache can be introduced if freshness or measured
database load requires it.
