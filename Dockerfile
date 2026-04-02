FROM eclipse-temurin:21-jre

WORKDIR /app
COPY build/libs/search-api-optimization-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Xmx2g", "-jar", "app.jar"]
