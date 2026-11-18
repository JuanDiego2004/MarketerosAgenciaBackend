FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src/main src/main
RUN ./mvnw -B -ntp -DskipTests package \
    && cp target/*.jar /workspace/app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/app.jar /app/app.jar
USER 10001:10001
# Render inyecta PORT; 10000 también permite ejecutar la imagen localmente.
ENV PORT=10000 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 10000
ENTRYPOINT ["java","-jar","/app/app.jar"]
