FROM eclipse-temurin:21.0.9_10-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x mvnw && ./mvnw -B -DskipTests package

FROM eclipse-temurin:21.0.9_10-jre
WORKDIR /app
COPY --from=build /workspace/target/rehabilitacare-0.0.1-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
