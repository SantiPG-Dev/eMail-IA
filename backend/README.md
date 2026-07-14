# Backend — eMail-IA (Spring Boot)

Backend REST de eMail-IA migrado a **Spring Boot 3.4.1 + Java 21**.

## Stack previsto (Fase 1+)

- **Spring Boot 3.4.1** + Java 21
- **H2 embebida cifrada** (AES) + Spring Data JPA + Flyway
- **Jakarta Mail** (IMAP/SMTP/XOAUTH2)
- **Weka** (filtro spam/phishing, ML local)
- **LangChain4j** → LM Studio / OpenAI (asistente IA)
- **OAuth2** Google + Microsoft (callback localhost)
- **Seguridad**: AES-256-GCM, PBKDF2 600k, BCrypt, sesión master-password

## Estructura prevista

```
backend/
├── src/main/java/com/emailai/
│   ├── config/          # SecurityConfig, AppConfigStore
│   ├── domain/          # entidades JPA
│   ├── repository/      # Spring Data JPA
│   ├── service/         # Mail, Spam (Weka), IA, OAuth, Calendar, Tasks
│   ├── security/        # SecureStorage, SecureSessionManager
│   ├── web/             # controllers, DTOs
│   └── oauth/           # OAuth2 providers + callback server
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/    # Flyway V1__init.sql
└── pom.xml
```

> Fase 0 completada — scaffolding. La implementación empieza en Fase 1.
