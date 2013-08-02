package geotrellis.transit

import org.opentripplanner.routing.graph.Graph
import org.opentripplanner.routing.edgetype._
import org.opentripplanner.routing.vertextype._

import scala.collection.mutable
import scala.collection.JavaConversions._

object OpenTripPlanner {
  def mainOTP(args:Array[String]):Unit = {
    println("\n\t == GEOTRELLIS-TRANSIT GRAPH ENGINE ==\n")

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
