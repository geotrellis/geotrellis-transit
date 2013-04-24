package commonspace

import org.opentripplanner.routing.graph.Graph
import org.opentripplanner.routing.edgetype._
import org.opentripplanner.routing.vertextype._

import scala.collection.mutable
import scala.collection.JavaConversions._
import scala.reflect.runtime.universe._

import commonspace.io._
import commonspace.graph._

/*
 * Proposal:
 *
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
  def main(args:Array[String]):Unit = mainCS(args)
  
  def mainCS(args:Array[String]):Unit = {
    println("\n\t == COMMONSPACE GRAPH ENGINE ==\n")

    val fileSets = List(
                     GtfsFiles("Bus",
                               "/home/rob/data/philly/gtfs/google_bus/stops.txt", 
                               "/home/rob/data/philly/gtfs/google_bus/stop_times.txt"),
                     GtfsFiles("Train",
                               "/home/rob/data/philly/gtfs/google_rail/stops.txt", 
                               "/home/rob/data/philly/gtfs/google_rail/stop_times.txt")
    )

    var graph:UnpackedGraph = null
    for(fileSet <- fileSets) {
      graph = 
        if(graph == null) {
          Logger.timedCreate(s"Loading GTFS ${fileSet.name} data into unpacked graph...","Upacked graph created.")({ () =>
            GtfsParser.parse(fileSet)
          })
        } else {
          UnpackedGraph.merge(graph,
            Logger.timedCreate(s"Loading GTFS ${fileSet.name} data into unpacked graph...","Upacked graph created.")({ () =>
              GtfsParser.parse(fileSet)
            }))
        }
    }

    Logger.log(s"Graph Info:")
    Logger.log(s"  Edge Count: ${graph.edgeCount}")
    Logger.log(s"  Vertex Count: ${graph.vertexCount}")

    val packed = Logger.timedCreate("Packing graph...", "Packed graph created.")({ () => 
      graph.pack
    })

    val spt = Logger.timedCreate("Creating shortest path tree...", "Shortest Path Tree created.")({ () => 
      new ShortestPathTree(0,Time(0),packed)
    })
  }

  def mainOTP(args:Array[String]):Unit = {
    println("\n\t == COMMONSPACE GRAPH ENGINE ==\n")

    val graphPath = if(args.length > 1) {
      args(0)
    } else {
      "/var/otp/graphs/Graph.obj"
    }

    logOpenTripPlannerData(graphPath)
  }

  def logOpenTripPlannerData(graphPath:String):Unit = {
    if(!new java.io.File(graphPath).isFile) {
      Logger.log(s"ERROR: OTP graph at $graphPath does not exist.")
      return
    }

    val graph = Logger.timedCreate("Loading OTP graph object...","OTP Graph object loaded.")({ () =>
      Graph.load(new java.io.File(graphPath), Graph.LoadLevel.FULL)
    })
    
    logGraphInfo(graph)
    logVertexAndEdgeInfo(graph)
    Logger.log("")
  }

  def logGraphInfo(g:Graph) = {
    Logger.log("Graph info:")
    Logger.log(s"\tNumber of Verticies: ${g.countVertices}")
    Logger.log(s"\tNumber of Edges: ${g.countEdges}")
  }

  def logVertexAndEdgeInfo(g:Graph) = {
    val vertexTypes = mutable.HashMap[String,Int]()
    val edgeTypes = mutable.HashMap[String,Int]()

    for(v <- g.getVertices) {
      val vname = v match {
        case _:BikeRentalStationVertex =>
          "BikeRentalStationVertex"
        case _:ElevatorOffboardVertex =>
          "ElevatorOffboardVertex"
        case _:ElevatorOnboardVertex =>
          "ElevatorOnboardVertex"
        case _:ExitVertex =>
          "ExitVertex"
        case _:IntersectionVertex =>
          "IntersectionVertex"
        case _:PatternArriveVertex =>
          "PatternArriveVertex"
        case _:PatternDepartVertex =>
          "PatternDepartVertex"
        case _:PatternStopVertex =>
          "PatternStopVertex"
        // case _:StreetVertex =>
        //   "StreetVertex"
        case _:TransitStopArrive =>
          "TransitStopArrive"
        case _:TransitStopDepart =>
          "TransitStopDepart"
        case _:TransitStop =>
          "TransitStop"
        // case _:TransitVertex =>
        //   "TransitVertex"
        case _ =>
          "Unknown"
      }

      if(!vertexTypes.contains(vname)) { vertexTypes(vname) = 0 }
      vertexTypes(vname) += 1

      for(e <- v.getOutgoing) {
        val ename = e.toString.split('(')(0).split('<')(0)
        if(!edgeTypes.contains(ename)) { edgeTypes(ename) = 0 }
        edgeTypes(ename) += 1
      }
    }      

    Logger.log("\tVertex types and counts")
    val vertices = g.countVertices.toDouble
    for(t <- vertexTypes.keys.toSeq.sortWith((k1,k2) => vertexTypes(k1) > vertexTypes(k2))) {
      val pct = "%.2f".format((vertexTypes(t) / vertices)*100)
      Logger.log(s"\t\t$t - ${vertexTypes(t)} ($pct%)")
    }

    Logger.log("\tEdge types and counts")
    val edges = g.countEdges.toDouble
    for(t <- edgeTypes.keys.toSeq.sortWith((k1,k2) => edgeTypes(k1) > edgeTypes(k2))) {
      val pct = "%.2f".format((edgeTypes(t) / edges)*100)
      Logger.log(s"\t\t$t - ${edgeTypes(t)} ($pct%)")
    }
  }
}
