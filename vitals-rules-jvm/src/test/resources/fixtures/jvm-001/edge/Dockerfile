FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
COPY --from=build /src/target/orders-service.jar /app.jar
ENTRYPOINT ["java", "-Xmx512m", "-jar", "/app.jar"]
