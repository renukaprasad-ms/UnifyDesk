FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package

FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S -g 10001 emailprovider && adduser -S -u 10001 -G emailprovider emailprovider

WORKDIR /app

COPY --from=build /workspace/target/email-provider-*.jar app.jar

USER 10001:10001

EXPOSE 8010

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
