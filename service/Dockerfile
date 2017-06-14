# The project uses Scala 2.10 and GT 0.9, but it runs fine with Java 8.
FROM openjdk:8-jre

WORKDIR /usr/local/src
COPY . /usr/local/src

ADD target/scala-2.10/geotrellis-transit-assembly-0.1.0-SNAPSHOT.jar /opt/

EXPOSE 9999

CMD java -jar /opt/geotrellis-transit-assembly-0.1.0-SNAPSHOT.jar server /usr/local/src/production.json
