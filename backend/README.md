# Backend — eMail-IA (Spring Boot)

Backend REST de eMail-IA: **Spring Boot 3.4.1 + Java 21**.

## Stack

- **Spring Boot 3.4.1** + Java 21 (web, validation, data-jpa, security)
- **H2 embebida** con `CIPHER=AES` + Spring Data JPA + Flyway (`V1`–`V7`)
- **Jakarta Mail** (IMAP/SMTP/XOAUTH2)
- **Weka** (`NaiveBayesMultinomial`) para spam/phishing, 100% local
- **Asistente IA**: `RestClient` contra API OpenAI-compatible (LM Studio u OpenAI)
- **OAuth2** Google + Microsoft (callback loopback)
- **Seguridad**: JWT HMAC-SHA256 y credenciales cifradas con AES-GCM (`enc1:`)

## Estructura

```
backend/
├── src/main/java/com/emailai/
│   ├── ai/            # AiService (LM Studio / OpenAI)
│   ├── common/        # utilidades compartidas
│   ├── config/        # SecurityConfig, DatabaseConfig, AppConfigStore
│   ├── domain/        # entidades JPA
│   ├── oauth/         # providers Google/Microsoft + callback server
│   ├── repository/    # Spring Data JPA
│   ├── security/      # JWT, cifrado de credenciales, rate limiter
│   ├── service/       # correo, spam, calendario, tareas, Todoist, remitentes
│   └── web/           # controllers + DTOs
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/  # Flyway V1..V7
└── pom.xml
```

## Ejecutar y probar

```bash
mvn spring-boot:run   # API en localhost:8080
mvn test              # 126 tests
mvn package           # emailai-backend-1.3.2.jar (embebe frontend/dist en static/)
```

`mvn package` necesita `frontend/dist` ya construido; `lanzadores/linux/sourcecode/build-package.sh` lo hace en orden.
