FROM maven:3.9-eclipse-temurin-17

WORKDIR /app

COPY assessment_system/ .

RUN mvn clean package -DskipTests

EXPOSE 10000

CMD ["java", "-jar", "target/adaptive_assessment-0.0.1-SNAPSHOT.jar"]