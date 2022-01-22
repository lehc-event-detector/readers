FROM maven:3.8-openjdk-8-slim

WORKDIR /app

COPY . .

CMD mvn clean compile exec:java -Dexec.mainClass=com.numaolab.App