# ── Étape build : Maven + JDK 21 ──
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Dépendances en cache (couche séparée des sources)
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Sources puis packaging (tests skip — ils tournent en CI)
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true

# ── Étape runtime : JRE 21 seul ──
FROM eclipse-temurin:21-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build /app/target/remi-preparateur-0.0.1-SNAPSHOT.jar app.jar

# Dossier de stockage des documents médicaux (monté en volume en prod)
RUN mkdir -p /app/data/documents-medicaux && chown -R app:app /app
USER app

# Heap borné (cohabitation VPS) + port applicatif
ENV JAVA_OPTS="-Xmx512m"
EXPOSE 8080

# Healthcheck via Actuator (busybox wget présent dans l'image alpine)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
