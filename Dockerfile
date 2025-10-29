FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src/ /build/src/
COPY .git/ /build/.git/
RUN mvn clean install -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre
COPY --from=build /build/target/*.jar /app/my-app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/my-app.jar"]