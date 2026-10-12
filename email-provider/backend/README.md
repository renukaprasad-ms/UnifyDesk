# UnifyDesk Email backend

Java 25 / Spring Boot 4.1.1, Maven wrapper, Flyway schema migrations, tests, Dockerfile and the original Java API services all live here. This is a source layout change only; Java package names, product IDs, database schema and API paths remain unchanged.

```bash
cd email-provider/backend
./mvnw clean package
```

To deploy only the backend, from `email-provider/` run `./k8s.sh restart app`. Do not move the root `.env` file or reset PostgreSQL volumes.
