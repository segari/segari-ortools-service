FROM eclipse-temurin:25-jdk AS build

# Install Maven
ARG MAVEN_VERSION=3.9.11
ARG MAVEN_BASE_URL=https://apache.osuosl.org/maven/maven-3/${MAVEN_VERSION}/binaries
RUN apt-get update && \
    apt-get install -y curl && \
    curl -fsSL ${MAVEN_BASE_URL}/apache-maven-${MAVEN_VERSION}-bin.tar.gz | tar xz -C /opt && \
    ln -s /opt/apache-maven-${MAVEN_VERSION} /opt/maven && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

ENV PATH=/opt/maven/bin:${PATH}

WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src/ /build/src/
RUN mvn clean install -Dmaven.test.skip=true

FROM openjdk:25-jre
COPY --from=build /build/target/*.jar /app/my-app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Dspring.profiles.active=production","-jar","/app/my-app.jar"]