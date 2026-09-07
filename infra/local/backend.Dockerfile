FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/backend
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle ./gradle
RUN ./gradlew dependencies --no-daemon
COPY backend/src ./src
RUN ./gradlew bootWar --no-daemon

FROM tomcat:10.1-jre21-temurin
RUN rm -rf /usr/local/tomcat/webapps/*
COPY --from=build /workspace/backend/build/libs/ROOT.war /usr/local/tomcat/webapps/ROOT.war
EXPOSE 8080
