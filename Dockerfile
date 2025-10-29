FROM eclipse-temurin:25-jdk AS build

# Verify and set JAVA_HOME
RUN echo "JAVA_HOME: $JAVA_HOME" && \
    echo "Java location:" && \
    which java && \
    java -version

WORKDIR /build

# Copy Maven Wrapper files
COPY .mvn .mvn
COPY mvnw .
COPY pom.xml .

# Download dependencies
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# Copy source and build
COPY src/ /build/src/
RUN ./mvnw clean install -Dmaven.test.skip=true

FROM openjdk:25-jre
COPY --from=build /build/target/*.jar /app/my-app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Dspring.profiles.active=production","-jar","/app/my-app.jar"]