# pki-ra — PKI Registration Authority

Multi-module Spring Boot application for PKI Registration Authority (RA) operations.

## Tech Stack

| Component | Version |
|---|---|
| Java | 25 |
| Spring Boot | 4.0.0 |
| Gradle | 9.4.0 |
| PostgreSQL | 42.7.5 |
| Flyway | 11.3.4 |
| Quartz | 2.5.0 |
| SpringDoc OpenAPI | 2.8.5 |

## Project Structure

```
pki-ra/
├── gradle/
│   ├── libs.versions.toml       ← ALL versions centralized here
│   └── wrapper/
├── subprojects/
│   ├── common/                  ← Shared models, audit, exceptions
│   ├── documentation/           ← OpenAPI / Swagger UI config
│   ├── gui/                     ← Thymeleaf UI + AD authentication
│   ├── uiservice/               ← REST API business logic
│   └── scheduler/               ← Quartz background jobs
├── build.gradle                 ← Root shared config
└── settings.gradle              ← Module declarations
```

## Run Modules

```bash
# GUI (port 8080)
./gradlew :gui:bootRun

# UIService REST API (port 8081)
./gradlew :uiservice:bootRun

# Scheduler (port 8082)
./gradlew :scheduler:bootRun
```

## Required Environment Variables

```bash
DB_URL=jdbc:postgresql://localhost:5432/pki_ra
DB_USERNAME=pki_ra_user
DB_PASSWORD=<secret>
AD_DOMAIN=corp.example.com
AD_URL=ldap://ad.corp.example.com:389
AD_ROOT_DN=dc=corp,dc=example,dc=com
```

## Build All

```bash
./gradlew build
```
