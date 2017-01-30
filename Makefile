# Makefile to simply the creation of the Docker image
TAG := 1.0.0

fetch:
	mkdir -p graph
	aws s3 cp s3://geotrellis-transit/transit.edges graph/
	aws s3 cp s3://geotrellis-transit/transit.graph graph/
	aws s3 cp s3://geotrellis-transit/transit.vertices graph/

start:
	./sbt "run server geotrellis-transit.json"

image: graph/transit.edges graph/transit.graph graph/transit.vertices
	./sbt assembly
	docker build -t geotrellis-transit:${TAG} .
