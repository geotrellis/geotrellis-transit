# Makefile to simply the creation of the Docker image
TAG := 1.0.0

# ASSUMPTION: The JSON config sets the graph to write to `./graph/`
graph: geotrellis-transit.json
	./sbt "run buildgraph geotrellis-transit.json"

image: graph/transit.edges graph/transit.graph graph/transit.vertices
	docker build -t geotrellis-transit:${TAG} .
