FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package

FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S emailprovider && adduser -S emailprovider -G emailprovider

WORKDIR /app

COPY --from=build /workspace/target/email-provider-*.jar app.jar

USER emailprovider

EXPOSE 8010

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
