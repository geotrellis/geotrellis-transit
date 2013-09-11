package geotrellis.transit

import geotrellis.transit.loader.Loader
import geotrellis.transit.loader.GraphFileSet
import geotrellis.transit.loader.gtfs.GtfsFiles
import geotrellis.transit.loader.osm.OsmFileSet
import geotrellis.network._
import geotrellis.network.graph._
import geotrellis.feature.SpatialIndex

import scala.collection.mutable

import geotrellis.rest.WebRunner

import java.io._

import com.wordnik.swagger.jaxrs.JaxrsApiReader

object Main {
  // Make swagger not do weird naming on API docs.
  JaxrsApiReader.setFormatString("")

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

  def mainServer(args:Array[String]) = {
    WebRunner.run { server =>
      server.context.addFilter(classOf[geotrellis.transit.services.ApiOriginFilter],
                               "/*",
                               java.util.EnumSet.noneOf(classOf[javax.servlet.DispatcherType]))
    }
  }

  def graphInfo() = {
    val graph = _context.graph
    var totalEdgeCount = 0
    Logger.log(s"Graph Info:")
    for(mode <- graph.anytimeEdgeSets.keys) {
      val ec = graph.anytimeEdgeSets(mode).edgeCount
      totalEdgeCount += ec
      Logger.log(s"  $mode Edge Count: ${ec}")
    }
    for(mode <- graph.scheduledEdgeSets.keys) {
      val ec = graph.scheduledEdgeSets(mode).edgeCount
      totalEdgeCount += ec
      Logger.log(s"  $mode Edge Count: ${ec}")
    }

    Logger.log(s"  Total Edge Count: ${totalEdgeCount}")
    Logger.log(s"  Vertex Count: ${graph.vertexCount}")
  }

  def debug() = {
    val graph = _context.graph
    val vc = graph.vertexCount

    Logger.log("Finding suspicious walk edges...")
    for(i <- 0 until vc) {
      val sv = graph.vertexFor(i)
      graph.getEdgeIterator(Walking,EdgeDirection.Incoming).foreachEdge(i,Time.ANY.toInt) { (t,w) =>
        val tv = graph.vertexFor(t)
        val d = Distance.distance(sv.location,tv.location)
        if(d > 2000) {
          println(s"$sv  ->  $tv is $d meters.")
        }
      }
    }
    Logger.log("Done.")
  }
}
