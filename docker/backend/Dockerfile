# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Copy the wrapper + pom first so dependency resolution is cached as its own
# layer and only re-runs when the pom actually changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Don't run as root.
RUN useradd --system --create-home --uid 1001 aika
USER aika

COPY --from=build --chown=aika:aika /build/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
