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

  case class SPInfo(osmName:String,
                    travelTime:Duration,
                    location:Location,
                    vertexId:Int) {
    override
    def toString = {
      s"${osmName}\t\t${travelTime}\t\t${location}"
    }
  }

  def printList(lat:Double,lng:Double,starttime:Time,duration:Duration) = {
    val sv = context.index.nearest(lat,lng)
    val sl = context.graph.locations.getLocation(sv)
    val snl = context.namedLocations(sl)

    Logger.log(s"Getting the shortest paths from osm node ${snl.name} " + 
               s"at $starttime with max duration of $duration")

    val spt =
      commonspace.Logger.timedCreate("Creating shortest path tree...",
        "Shortest Path Tree created.") { () =>
        ShortestPathTree(sv,starttime,context.graph,duration)
      }

    val original = SPInfo(snl.name,Duration(0),sl,sv)

    Logger.log(s"  From $original")

    val distance = Walking.walkDistance(duration)
    val extent = Projection.getBoundingBox(lat,lng,distance+1000)
    val nodes = 
      (for(v <- context.index.pointsInExtent(extent)) yield {
        val t = spt.travelTimeTo(v)
        val l = Main.context.graph.locations.getLocation(v)
        val osm = Main.context.namedLocations(l)
        SPInfo(osm.name,t,l,v)
      })
       .filter(_.travelTime.isReachable)
       .sortBy(t => t.travelTime)

    Logger.log("     NODE\t\t\tTIME\t\t\tLOCATION")
    Logger.log("     ----\t\t\t----\t\t\t--------")
    for(x <- nodes) {
      if(x.osmName == "109837119") {
        val path = spt.travelPathTo(x.vertexId)
        Logger.log(s"Path to ${x}:")
        var prev = 0.0
        for(v <- path) { 
          val loc = context.graph.locations.getLocation(v)
          val nloc = context.namedLocations(loc)
          val sp = spt.travelTimeTo(v)
          val totald = Projection.toFeet(Projection.distance(sl,loc))
          val d = totald - prev
          prev = totald
          val walktime = d / Projection.toFeet(Walking.WALKING_SPEED)
          Logger.log(s"  ${nloc.name}\t${sp}\t${loc}\t${d}\t${walktime}")
        }
        Logger.log(s"shortest path to the node:  ${x.travelTime}")
      }
//      Logger.log(s"$x")
    }
  }
}
