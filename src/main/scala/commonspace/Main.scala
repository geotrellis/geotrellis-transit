package commonspace

import commonspace.io._
import commonspace.io.gtfs.GtfsFiles
import commonspace.io.osm.OsmFileSet
import commonspace.graph._
import commonspace.index._

import scala.collection.mutable

import geotrellis.rest.WebRunner

import java.io._

/*
 * Read in GTFS data directly into a multigraph: each edge represents
 * an instance of a trip between two stops at a given time. The lookup
 * for outgoing edges would then take a time parameter, which would select
 * the most near time and return an edge that had a weight of
 * (waiting for departure) + (time to travel). OpenStreetMap data
 * would be read to find the shortest path between stations (possibly pruned
 * logically) as to account for connections between stations. This would
 * be a graph to do shortest path on using simple Dijkstra's, and later
 * more advanced speed-ups.
 *
 * The shortest path raster could then use OSM data for shortest path from
 * reachable station points, and min those rasters to the straight up
 * public transit one.
 */

// case class FileSetResult(stops:Stops,graph:UnpackedGraph) {
//   def merge(other:FileSetResult) =
//     FileSetResult(stops.mergeIn(other.stops),
//                   UnpackedGraph.merge(graph,other.graph))
// }

object Main {
  private var _context:GraphContext = null
  def context = _context

  val fileSets = List[GraphFileSet](
                   GtfsFiles("Bus",
                             "/home/rob/data/philly/gtfs/google_bus/stops.txt", 
                             "/home/rob/data/philly/gtfs/google_bus/stop_times.txt")
    ,
                   GtfsFiles("Train",
                             "/home/rob/data/philly/gtfs/google_rail/stops.txt", 
                             "/home/rob/data/philly/gtfs/google_rail/stop_times.txt")
    ,
                   OsmFileSet("Philadelphia",
                              "/home/rob/data/philly/osm/philadelphia.osm")
  )  

  def main(args:Array[String]) = {
    _context = GraphContext.getContext("/tmp/commonspacegraph.obj", fileSets)

    if(args.length == 2) {
      val lat = args(0).toDouble
      val long = args(1).toDouble
      Logger.log(s"Finding closest station to $lat, $long")
      mainCommandLine(lat,long)
    } else if(args.length == 1) {
      if(args(0).contains(",")) {
        val ll = args(0).split(",")
        val lat = ll(0).toDouble
        val long = ll(1).toDouble
        Logger.log(s"Finding closest station to $lat, $long")
        mainCommandLine(lat,long)
      } else {
        mainServer(args)
      }
    } else {
      mainServer(args)
    }
  }

  def mainServer(args:Array[String]) = WebRunner.main(args)

  def mainCommandLine(lat:Double,long:Double):Unit = {
    val vertex = context.index.nearest(lat,long)
    val location = context.graph.locations.getLocation(vertex)

    val namedLocation = 
      context.namedLocations.lookup(location) match {
        case Some(nl) => nl
        case None =>
          Logger.warn(s"There is no stop at $location")
          NamedLocation("UNKNOWN",location)
      }

    val distance = Projection.latLongToMeters(lat,long,location.lat,location.long)
    Logger.log(s"Nearest station:")
    Logger.log(s"  NAME: ${namedLocation.name}")
    Logger.log(s"  LOCATION: ${location.lat},${location.long}")
    Logger.log(s"  DISTANCE: $distance meters")

    val dist = 200 //meters
    val boundingBox = Projection.getBoundingBox(lat,long,dist)
    Logger.log("")
    Logger.log(s"Bounding Box for $dist meters: $boundingBox")
    Logger.log(s"Distance to:")
    val sw = Projection.latLongToMeters(lat,long,boundingBox.ymin,boundingBox.xmin)
    Logger.log(s"  SOUTHWEST CORNER: $sw")
    val ne = Projection.latLongToMeters(lat,long,boundingBox.ymax,boundingBox.xmax)
    Logger.log(s"  SOUTHWEST CORNER: $ne")
    Logger.log(s"Stations within $dist meters:")
    for(v <- context.index.pointsInExtent(boundingBox)) {
      val l = context.graph.locations.getLocation(v)
      val nl = context.namedLocations(l)
      val d = Projection.latLongToMeters(lat,long,l.lat,l.long)
      Logger.log(s"  NAME: ${nl.name}")
      Logger.log(s"  LOCATION: ${l.lat},${l.long}")
      Logger.log(s"  DISTANCE: $d meters")
    }
  }
}
