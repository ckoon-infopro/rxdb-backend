FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY . /app
RUN ./mvnw package -DskipTests
CMD ["java", "-jar", "target/rxdb-backend-0.0.1-SNAPSHOT.jar"]