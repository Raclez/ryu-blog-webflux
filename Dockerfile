FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# 产物名由 pom.xml 决定：artifactId=ryu-blog-monolith, version=1.0-SNAPSHOT
COPY target/ryu-blog-monolith-1.0-SNAPSHOT.jar app.jar

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS="-Xms256m -Xmx512m"
ENV SPRING_PROFILES_ACTIVE="prod"

EXPOSE 5300

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=$SPRING_PROFILES_ACTIVE"]