package geotrellis.transit.services

import geotrellis.transit._

import geotrellis.network._
import geotrellis.network.graph._


case class SptInfo(spt: ShortestPathTree, vertices: Option[ReachableVertices])

import javax.xml.bind.annotation._
import scala.reflect.BeanProperty

//REFACTOR: spt.getSptInfo
object SptInfo {
  def apply(request:TravelShedRequest): SptInfo = {
    val TravelShedRequest(lat,lng,time,duration,modes,departing) = request
    val startVertex = Main.context.index.nearest(lat, lng)
    val spt =
      geotrellis.transit.Logger.timedCreate("Creating shortest path tree...",
        "Shortest Path Tree created.") { () =>
        if(departing) {
          ShortestPathTree.departure(startVertex, time, Main.context.graph, duration,modes:_*)
        } else {
          ShortestPathTree.arrival(startVertex, time, Main.context.graph, duration,modes:_*)
        }
      }

    SptInfo(spt, ReachableVertices.fromSpt(spt))
  }

}
