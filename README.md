GeoTrellis Transit
==================

GeoTrellis Transit is a data loader and set of web services run in an
embedded Jetty server that answer questions about travel times and transit
sheds. The project also includes a demo client application that hits these
endpoints, and running example of which can be found at
[transit.geotrellis.com](http://transit.geotrellis.com).

This project was done in collaboration with TechImpact and with support from
the William Penn Foundation.

Dependencies
------------

- Vagrant 1.9.5
- VirtualBox 5.1+
- AWS CLI 1.11+
- AWS Account (to access S3)

Getting Started
-------------

To provision a VM and fetch our pre-ingested travelsheds made from OSM and GTFS data:

```bash
$ ./scripts/setup
$ vagrant ssh
```

This will download data into `./service/graph/`.

Scripts
-------

Helper and development scripts are located in the `./scripts` directory at the root of this project. These scripts are designed to encapsulate and perform commonly used actions such as starting a development server, accessing a development console, or running tests.

| Script Name             | Purpose                                                      |
|-------------------------|--------------------------------------------------------------|
| `update`                | Pulls/builds necessary containers                            |
| `setup`                 | Provisions the VM, fetch OSM/GTFS data.                      |
| `server`                | Starts a development server that listens at `http://localhost:9999`                                 |
| `console`               | Gives access to a running container via `docker-compose run` |
| `test`                  | Runs tests for project				                         |
| `cibuild`               | Invoked by CI server and makes use of `test`.                |
| `cipublish`             | Build JAR and publish container images to container image repositories.    |

Testing
-------

Run all the tests:

```bash
$ ./scripts/test
```
