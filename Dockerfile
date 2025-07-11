# 1. Build stage
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

COPY ../../pom.xml ../../
COPY ../drawing-module/pom.xml ../drawing-module/
COPY ./pom.xml ./
COPY ../../.mvn ../../.mvn
COPY ../../mvnw ../../mvnw

RUN ../../mvnw -f ../../pom.xml dependency:go-offline -B

COPY ./src ./src

# Build
RUN ../../mvnw -f ../../pom.xml clean package -pl georef-module -am -DskipTests

# 2. Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
