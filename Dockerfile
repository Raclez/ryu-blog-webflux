FROM openjdk:17-slim

WORKDIR /app

COPY target/ryu-blog-1.0.0.jar app.jar

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV SPRING_PROFILES_ACTIVE="prod"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=$SPRING_PROFILES_ACTIVE"]