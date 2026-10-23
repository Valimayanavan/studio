FROM maven:3.9-eclipse-temurin-21

WORKDIR /app

COPY . .

RUN mvn -B clean package -DskipTests

EXPOSE 10000

CMD ["sh", "-c", "java -jar target/*.jar"]
