# Country Information Service

A Spring Boot microservice that accepts a country name over REST, resolves it through the
public [CountryInfoService](http://webservices.oorsprong.org/websamples.countryinfo/CountryInfoService.wso)
SOAP API, persists the result in MySQL and exposes full CRUD over the stored records.

## What it does

A single `POST /api/countries` request runs this pipeline:

1. **Normalize** the submitted name (`kenya` to `Kenya`). The upstream service is case sensitive
   and rejects anything that is not capitalised word-by-word.
2. **Short-circuit** if the country is already stored, so no SOAP call is made.
3. **Resolve the ISO code** via the `CountryISOCode` SOAP operation (`Kenya` to `KE`).
4. **Fetch the full record** via the `FullCountryInfo` operation using that ISO code.
5. **Persist** the country and its languages, then return the stored representation.

Steps 3 and 4 are the two chained SOAP calls required by the brief; they are deliberately one
pipeline behind one endpoint rather than two separately callable endpoints.

## Running locally

### Prerequisites

- JDK 25 or newer
- Docker (to run MySQL locally)

### 1. Start MySQL

```bash
docker compose up -d
```

This starts MySQL 8.4 on `localhost:3306` with database `country_information` and user
`country` / `country`, matching the `dev` profile defaults.

### 2. Run the application

```bash
./mvnw spring-boot:run
```

The `dev` profile is active by default, so no extra configuration is needed. Hibernate creates
the schema on first start (`ddl-auto: update`).

### 3. Try it

```bash
curl -X POST http://localhost:8080/api/countries \
  -H "Content-Type: application/json" \
  -d '{"name":"kenya"}'
```

| Resource | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |
| Prometheus metrics | http://localhost:8080/actuator/prometheus |

Full interactive API documentation is available in Swagger UI at the link above.

For manual testing of the country endpoints, import
[docs/country-information.postman_collection.json](docs/country-information.postman_collection.json)
into Postman. It covers the lookup, list, fetch, update and delete requests. Set the `baseUrl`
variable if the service is not on `http://localhost:8080`, and set `countryId` to the identifier
returned by the lookup request.

## Testing

```bash
./mvnw test
```

## Configuration

The active profile is set in `application.yaml`.

- **`dev`**: literal values pointing at `localhost`, schema auto-update, SQL logging off.
- **`prod`**: every value comes from an environment variable, supplied in Kubernetes by the
  ConfigMap and Secret.

## Container and Kubernetes

```bash
docker build -t country-information:latest .
```

Kubernetes manifests live in `k8s/`, and `deploy.ps1` builds the image, makes it available to the
target cluster and applies the manifests in order. It works against any cluster. Push to a
registry with `-Registry`.

```powershell
.\deploy.ps1 -Registry registry.example.com/myproject -Tag v1
```

See 
[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for the full deployment guide, and
[docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) when something goes wrong.
