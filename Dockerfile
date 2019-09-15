FROM openjdk:11-jdk-slim

COPY target/jar-dependencies/* /deployments/java/
COPY target/*.jar /deployments/java/

EXPOSE 8080
ENTRYPOINT exec java $JAVA_OPTS -cp "/deployments/java/*" org.apache.camel.cdi.Main