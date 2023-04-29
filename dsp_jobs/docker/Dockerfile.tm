FROM adoptopenjdk/openjdk11

COPY traffic_monitoring/producer/target/producer-1.0-SNAPSHOT.jar /opt/producer-1.0-SNAPSHOT.jar
WORKDIR /opt
CMD ["java", "-jar", "producer-1.0-SNAPSHOT.jar"]