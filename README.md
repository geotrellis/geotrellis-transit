GeoTrellis Transit
===========

API and libraries for generating travelsheds from OSM &amp; GTFS data.

The 'buildgraph' command, along with the appropriate json config file, will create serialized graph files that represent a geotrellis.network.graph.TransitGraph object and related information:

```bash
./sbt "run buildgraph geotrellis-transit.json"
```

where the configuration json looks like this:

```javascript
{
    "loader": {
        "gtfs": [
            {
                "name": "bus",
                "path": "/var/data/philly/gtfs/google_bus" 
            },
            {
                "name": "train",
                "path": "/var/data/philly/gtfs/google_rail"
            }
        ],
        "osm": [
            {
                "name": "Philadelphia",
                "path": "/var/data/philly/osm/philadelphia.osm"
            }
        ]
    },
    "graph": {
        "data": "/var/data/transit/graph/"
    }
}
```

The data can then be used with the 'server' command to start up a GeoTrellis server that exposes transit information API endpoints. 

```bash
./sbt "run server geotrellis-transit.json"
```

After running the server, you can go to http://localhost:9999/ for an example client application.
