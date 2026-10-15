FROM maven:3.9.9-eclipse-temurin-21 AS build-env

WORKDIR /src/java-function-app
COPY . .

RUN mvn clean package -DskipTests

FROM mcr.microsoft.com/azure-functions/java:4-java21

ENV AzureWebJobsScriptRoot=/home/site/wwwroot \
    AzureFunctionsJobHost__Logging__Console__IsEnabled=true

COPY --from=build-env /src/java-function-app/target/azure-functions/ /tmp/azure-functions/
RUN set -eux; \
    APP_DIR=$(find /tmp/azure-functions -mindepth 1 -maxdepth 1 -type d | head -n 1); \
    test -n "$APP_DIR"; \
    cp -a "$APP_DIR"/. /home/site/wwwroot/
