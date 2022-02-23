FROM adoptopenjdk/openjdk8:alpine-jre
WORKDIR /ninja
COPY target/v2.jar /ninja/v2.jar
ENTRYPOINT [ "java", "-jar", "v2.jar" ]
