FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY src ./src
COPY config ./config
RUN mkdir -p out && find src -name "*.java" | xargs javac -d out

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/out ./out
COPY --from=build /app/config ./config
EXPOSE 7001 7002 7003
ENTRYPOINT ["java", "-cp", "out", "com.dynamo.lite.DynamoNode"]