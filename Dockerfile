ARG SERVICE_MODULE=wms-core-service

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
ARG SERVICE_MODULE

# Ağ kesintilerinde wagon yeniden deneme (go-offline sırasında sık görülür)
ENV MAVEN_OPTS="-Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.httpconnectionManager.ttlSeconds=120"

COPY pom.xml .
COPY wms-common-events/pom.xml wms-common-events/
COPY wms-core-service/pom.xml wms-core-service/
COPY wms-localization-service/pom.xml wms-localization-service/
COPY wms-finance-service/pom.xml wms-finance-service/
COPY wms-billing-service/pom.xml wms-billing-service/
COPY wms-integration-service/pom.xml wms-integration-service/
COPY wms-inbound-service/pom.xml wms-inbound-service/
COPY wms-inventory-service/pom.xml wms-inventory-service/
COPY wms-outbound-service/pom.xml wms-outbound-service/
COPY wms-notification-service/pom.xml wms-notification-service/

# go-offline ağ hatalarına karşı 5 deneme; .m2 cache mount ile paralel build'lerde tekrar indirme azalır
RUN --mount=type=cache,target=/root/.m2 \
    set -e; \
    for attempt in 1 2 3 4 5; do \
      echo "Maven dependency:go-offline attempt ${attempt}/5 for ${SERVICE_MODULE}..."; \
      if mvn -Pops -pl "${SERVICE_MODULE}" -am dependency:go-offline -B; then \
        exit 0; \
      fi; \
      echo "Attempt ${attempt} failed, waiting before retry..."; \
      sleep $((attempt * 10)); \
    done; \
    echo "Maven dependency:go-offline failed after 5 attempts"; \
    exit 1

COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -Pops -pl "${SERVICE_MODULE}" -am package -DskipTests -B

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
ARG SERVICE_MODULE
COPY --from=build /workspace/${SERVICE_MODULE}/target/${SERVICE_MODULE}-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
