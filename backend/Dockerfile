# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Khong dung 'mvn dependency:go-offline': no bo qua <exclusions> trong pom (itext 2.1.7.js2 cua jasperreports)
# va doi tai tu kho http cu -> loi. Thu vien Maven giu trong cache cua Docker (BuildKit) -> build lai tren VPS van nhanh.
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
# Jasper can fontconfig de do chu khi xuat PDF
RUN apt-get update && apt-get install -y --no-install-recommends fontconfig && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/shop-backend-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
