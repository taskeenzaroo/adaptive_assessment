FROM maven:3.9-eclipse-temurin-17

WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests

EXPOSE 10000

CMD ["sh", "-c", "java -jar target/*.jar"]