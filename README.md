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

- `make`
- The AWS CLI tools
- (optional) `docker`

Fetching Data
-------------

To fetch our pre-ingested travelsheds made from OSM and GTFS data:

```bash
> make fetch
```

This will download data into `./graph/`.

Running a Test Server
---------------------

To start a server that exposes transit information API endpoints:

```bash
> make start
```

After starting the server, you can go to http://localhost:9999/ for an
example client application.

Deployment
----------

We provide a Docker image to ease deployment. It can be built with:

```bash
> make image
```

but assumes that `make fetch` has been ran once already.
