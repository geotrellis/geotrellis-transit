package geotrellis.transit

import geotrellis.transit.loader.Loader
import geotrellis.transit.loader.GraphFileSet
import geotrellis.transit.loader.gtfs.GtfsFiles
import geotrellis.transit.loader.osm.OsmFileSet
import geotrellis.network._
import geotrellis.network.graph._
import geotrellis.network.index._

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
  val warmUp = true

  private var _context:GraphContext = null
  def context = _context

  def initContext(configPath:String) = {
    _context = Configuration.loadPath(configPath).graph.getContext
    println("Initializing shortest path tree array...")
    ShortestPathTree.initSptArray(context.graph.vertexCount)
  }

  def main(args:Array[String]):Unit = {
    if(args.length < 1) {
      Logger.error("Must use subcommand")
      System.exit(1)
    }

    def inContext(f:()=>Unit) = {
      val configPath = args(1)
      initContext(configPath)
      f
    }

    val call = 
      args(0) match {
        case "buildgraph" =>
          val configPath = args(1)
          () => buildGraph(configPath)
        case "server" =>
          inContext(() => mainServer(args))
        case "info" =>
          inContext(() => graphInfo())
        case "debug" =>
          inContext(() => debug())
        case s =>
          Logger.error(s"Unknown subcommand $s")
          System.exit(1)
          () => { }
      }

    call()
  }

  def buildGraph(configPath:String) = {
    Logger.log(s"Building graph data from configuration $configPath")
    val config = Configuration.loadPath(configPath)
    Loader.buildGraph(config.graph,config.loader.fileSets)
  }

  def mainServer(args:Array[String]) =
    WebRunner.main(args)

  def graphInfo() = {
    val graph = _context.graph
    val we = graph.walkEdges.edgeCount
    val be = graph.bikeEdges.edgeCount
    val te = graph.transitEdges.edgeCount

    Logger.log(s"Graph Info:")
    Logger.log(s"  Walk Edge Count: ${we}")
    Logger.log(s"  Bike Edge Count: ${be}")
    Logger.log(s"  Transit Edge Count: ${te}")
    Logger.log(s"  Total Edge Count: ${we+be+te}")
    Logger.log(s"  Vertex Count: ${graph.vertexCount}")
  }

  def debug() = {
    val graph = _context.graph
    val vc = graph.vertexCount

    Logger.log("Finding suspicious walk edges...")
    for(i <- 0 until vc) {
      val sv = graph.vertexFor(i)
      graph.foreachWalkEdge(i) { (t,w) =>
        val tv = graph.vertexFor(t)
        val d = Distance.distance(sv.location,tv.location)
        if(d > 2000) {
          println(s"WEIRD  $sv  ->  $tv is $d meters.")
        }
      }
    }
    Logger.log("Done.")
  }
}
