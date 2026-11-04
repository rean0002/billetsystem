# Trin 1: byg jar-filen med Maven (Maven behøver ikke være installeret lokalt)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# Trin 2: kør jar-filen i et lille image
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Kør som en almindelig bruger, ikke root. Bliver programmet nogensinde misbrugt,
# kan angriberen så ikke ændre i resten af containeren.
RUN groupadd --system app && useradd --system --gid app --no-create-home app
USER app
EXPOSE 8081
CMD ["java", "-jar", "app.jar"]
