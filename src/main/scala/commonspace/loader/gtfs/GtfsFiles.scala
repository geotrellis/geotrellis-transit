package commonspace.loader.gtfs

import commonspace.loader.{GraphFileSet,ParseResult}

import commonspace.{NamedLocations,NamedLocation,NamedWays}

case class GtfsFiles(name:String,stopsPath:String,stopTimesPath:String)
extends GraphFileSet {
  if(!new java.io.File(stopsPath).exists) { 
    sys.error(s"Stops file $stopsPath does not exist.")
  }
  if(!new java.io.File(stopTimesPath).exists) { 
    sys.error(s"Stop Times file $stopTimesPath does not exist.")
  }

  def parse():ParseResult = {
    val (stops,graph) = GtfsParser.parse(this)
    val namedLocations = 
      NamedLocations(
        for(location <- stops.locationToStop.keys) yield {
          NamedLocation(stops.locationToStop(location).name,location)
        }
      )
    ParseResult(graph,namedLocations,NamedWays.EMPTY)
  }
}
