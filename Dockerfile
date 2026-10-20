# ---------------------------------------------------------------------------
# Estágio de build: compila a biblioteca e a aplicação PoC.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build

WORKDIR /src

COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts ./
COPY lib ./lib
COPY poc-example ./poc-example

RUN chmod +x gradlew && ./gradlew :poc-example:installDist --no-daemon

# ---------------------------------------------------------------------------
# Estágio de execução: PostgreSQL + JRE + aplicação no mesmo container.
# ---------------------------------------------------------------------------
FROM postgres:16-alpine

RUN apk add --no-cache openjdk21-jre-headless

# Conexão do PostgreSQL (hardcoded, como na PoC).
ENV POSTGRES_USER=postgres \
    POSTGRES_PASSWORD=postgres \
    POSTGRES_DB=tacuapi \
    TACUAPI_DB_URL=jdbc:postgresql://localhost:5432/tacuapi \
    TACUAPI_DB_USER=postgres \
    TACUAPI_DB_PASSWORD=postgres \
    TACUAPI_PORT=8080

WORKDIR /app

COPY --from=build /src/poc-example/build/install/poc-example/ /app/
COPY docker/start.sh /usr/local/bin/start.sh
RUN chmod +x /usr/local/bin/start.sh

EXPOSE 8080 5432

ENTRYPOINT ["/usr/local/bin/start.sh"]
