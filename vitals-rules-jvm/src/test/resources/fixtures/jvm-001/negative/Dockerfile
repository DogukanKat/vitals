FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/orders-service.jar app.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
