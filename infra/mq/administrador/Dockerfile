FROM maven:3.9-eclipse-temurin-21 AS compilacion
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=compilacion /app/target/*.jar app.jar
USER app
EXPOSE 8086
ENTRYPOINT ["java","-XX:MaxRAMPercentage=60","-jar","app.jar"]
