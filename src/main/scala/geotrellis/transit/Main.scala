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
    _context = Configuration.loadPath(configPath).graph.getContext.transit
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
          inContext(() => graphInfo(args))
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

  def graphInfo(args:Array[String]) = {
    val g = _context.graph
    println(s"""
   GeoTrellis Transit Graph Information:
  
   Vertex Count: ${g.vertexCount}
   Edge Count:   ${g.edgeCount}
""")
  }
}
