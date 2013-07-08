package commonspace

import commonspace.loader.Loader
import commonspace.loader.GraphFileSet
import commonspace.loader.gtfs.GtfsFiles
import commonspace.loader.osm.OsmFileSet
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

object Main {
  private var _context:GraphContext = null
  def context = _context

  val contextPath = "/tmp/commonspacegraph.obj"

  val fileSets = List[GraphFileSet](
    //                GtfsFiles("Bus",
    //                          "/home/rob/data/philly/gtfs/google_bus/stops.txt", 
    //                          "/home/rob/data/philly/gtfs/google_bus/stop_times.txt")
    // ,
    //                GtfsFiles("Train",
    //                          "/home/rob/data/philly/gtfs/google_rail/stops.txt", 
    //                          "/home/rob/data/philly/gtfs/google_rail/stop_times.txt")
    // ,
                   OsmFileSet("Philadelphia",
                              "/home/rob/data/philly/osm/philadelphia.osm")
  )  

  def main(args:Array[String]):Unit = {
    if(args.length < 1) {
      Logger.error("Must use subcommand")
      System.exit(1)
    }

    def inContext(f:()=>Unit) = {
      val configPath = args(1)
      _context = Configuration.loadPath(configPath).graph.getContext
      f
    }

    val call = 
      args(0) match {
        case "buildgraph" =>
          () => buildGraph(args(1))
        case "nearest" =>
          inContext(() => nearest(args(2).toDouble,args(3).toDouble))
        case "spt" =>
          inContext(() => spt(args(2).toDouble,
                              args(3).toDouble,
                              Time(args(4).toInt),
                              Duration(args(5).toInt)))
        case "traveltime" =>
          inContext(() => traveltime(args(2).toDouble,
                                     args(3).toDouble,
                                     args(4).toDouble,
                                     args(5).toDouble,
                                     Time(args(6).toInt),
                                     Duration(args(7).toInt)))
        case "list" =>
          inContext(() => printList(args(2).toDouble,
                                     args(3).toDouble,
                                     Time(args(4).toInt),
                                     Duration(args(5).toInt)))
        case "getoutgoing" =>
          inContext(() => getoutgoing(args(2)))
        case "server" =>
          inContext(() => mainServer(args))
        case s =>
          Logger.error(s"Unknown subcommand $s")
          System.exit(1)
          () => { }
      }

    call()
  }

  def mainServer(args:Array[String]) = WebRunner.main(args)

  def buildGraph(configPath:String) = {
    Logger.log(s"Building graph data from configuration $configPath")
    val config = Configuration.loadPath(configPath)
    Loader.buildGraph(config.graph,config.loader.fileSets)
  }

  def nearest(lat:Double,lng:Double) = {
    Logger.log(s"Getting nearest OSM vertex to ($lat,$lng)")
    val v = context.index.nearest(lat,lng)
    val l = context.graph.locations.getLocation(v)
    val nl = context.namedLocations(l)
    Logger.log(s"NEAREST OSM VERTEX: ${nl.name}")
  }

  def spt(lat:Double,lng:Double,starttime:Time,duration:Duration) = {
    Logger.log(s"Getting the shortest path tree for ($lat,$lng) " + 
               s"at $starttime with max duration of $duration")
    val v = context.index.nearest(lat,lng)
    val l = context.graph.locations.getLocation(v)
    val nl = context.namedLocations(l)

    val spt =
      commonspace.Logger.timedCreate("Creating shortest path tree...",
        "Shortest Path Tree created.") { () =>
        ShortestPathTree(v,starttime,context.graph,duration)
      }
  }

  def traveltime(slat:Double,slng:Double,elat:Double,elng:Double,starttime:Time,duration:Duration) = {
    val sv = context.index.nearest(slat,slng)
    val sl = context.graph.locations.getLocation(sv)
    val snl = context.namedLocations(sl)

    val ev = context.index.nearest(elat,elng)
    val el = context.graph.locations.getLocation(ev)
    val enl = context.namedLocations(el)

    Logger.log(s"Getting the shortest path from osm node ${snl.name} to ${enl.name} " + 
               s"at $starttime with max duration of $duration")

    val spt =
      commonspace.Logger.timedCreate("Creating shortest path tree...",
        "Shortest Path Tree created.") { () =>
        ShortestPathTree(sv,starttime,context.graph,duration)
      }

    Logger.log("Outgoing edges:")
    context.graph.foreachOutgoingEdge(sv,starttime.toInt) { (t,w) =>
      val l = context.graph.locations.getLocation(t)
      val nl = context.namedLocations(l)
      Logger.log(s"  $t  $w  NODE ${nl.name} $l")

    }

    Logger.log(s"Travel time takes: ${spt.travelTimeTo(ev)}")
    Logger.log("Travel path: ")
    for(v <- Seq(sv) ++ spt.travelPathTo(ev) ++ Seq(ev)) {
      val l = context.graph.locations.getLocation(v)
      val nl = context.namedLocations(l)
      Logger.log(s"  NODE ${nl.name}   $l")
    }
  }

  def getoutgoing(osmnode:String) = {
    context.namedLocations.findName(osmnode) match {
      case Some(namedLocation) =>
        val v = 
          context.graph.locations.getVertexAt(namedLocation.location.lat, namedLocation.location.long)
        Logger.log("Outgoing edges:")
        context.graph.foreachOutgoingEdge(v,0) { (t,w) =>
          val l = context.graph.locations.getLocation(t)
          val nl = context.namedLocations(l)
          Logger.log(s"  $t  $w  NODE ${nl.name} $l")

        }
      case None =>
        Logger.error(s"Cannot find node $osmnode")
    }
  }

  def printList(lat:Double,lng:Double,starttime:Time,duration:Duration) = {
    val sv = context.index.nearest(lat,lng)
    val sl = context.graph.locations.getLocation(sv)
    val snl = context.namedLocations(sl)

    Logger.log(s"Getting the shortest paths from osm node ${snl.name}" + 
               s"at $starttime with max duration of $duration")

    val spt =
      commonspace.Logger.timedCreate("Creating shortest path tree...",
        "Shortest Path Tree created.") { () =>
        ShortestPathTree(sv,starttime,context.graph,duration)
      }

    val distance = Walking.walkDistance(duration)
    val extent = Projection.getBoundingBox(lat,lng,distance)
    val nodes = 
      (for(v <- context.index.pointsInExtent(extent)) yield {
        val t = spt.travelTimeTo(v)
        val l = Main.context.graph.locations.getLocation(v)
        val osm = Main.context.namedLocations(l)
        (osm.name,t)
      })
       .filter(_._2.toInt > 0)
       .sortBy(t => t._2.toInt)

    Logger.log("     NODE\t\t\tTIME")
    Logger.log("     ----\t\t\t----")
    for(x <- nodes) {
      Logger.log(s"  ${x._1}\t\t${x._2}")
    }
  }

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
