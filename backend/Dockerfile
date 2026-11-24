# The backend image (Guide 8.3). Two stages: a JDK builds the jar, and only the jar goes into a
# small JRE image that runs as a non-root user. Tests aren't run here: CI runs them before this.

# --- build ---
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
# The Maven wrapper and pom first: dependencies download in their own layer, which Docker reuses
# until pom.xml changes, so a code-only change rebuilds in seconds.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Windows checkouts can give mvnw Windows line endings, which Linux can't run.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw dependency:go-offline --batch-mode --quiet
COPY src/ src/
RUN ./mvnw package -DskipTests --batch-mode --quiet && cp target/sme-commerce-*.jar app.jar

# --- run ---
FROM eclipse-temurin:25-jre
# A user with no rights beyond this app: a break-in through the app can't change the system.
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /build/app.jar app.jar
USER app
# Render (and most hosts) pass the port in PORT, which application.properties reads. Inside the
# container we default it to 8080; 8081 is only the local-development default.
ENV PORT=8080
EXPOSE 8080
# Use up to 75% of the container's memory for the heap, whatever size the host gives it.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
