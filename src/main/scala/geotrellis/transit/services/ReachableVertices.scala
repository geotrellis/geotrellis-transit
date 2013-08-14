package geotrellis.transit.services

import geotrellis._
import geotrellis.transit._
import geotrellis.network._
import geotrellis.network.graph._
import geotrellis.network.index.SpatialIndex

case class ReachableVertices(index: SpatialIndex[Int], extent: Extent)

object ReachableVertices {
  def fromSpt(spt: ShortestPathTree): Option[ReachableVertices] = {
    var xmin = Double.MaxValue
    var ymin = Double.MaxValue
    var xmax = Double.MinValue
    var ymax = Double.MinValue

    val subindex =
      geotrellis.transit.Logger.timedCreate("Creating subindex of reachable vertices...",
        "Subindex created.") { () =>
          val reachable = spt.reachableVertices.toList
          SpatialIndex(reachable) { v =>
            val l = Main.context.graph.location(v)
            if (xmin > l.long) {
              xmin = l.long
            }
            if (xmax < l.long) {
              xmax = l.long
            }
            if (ymin > l.lat) {
              ymin = l.lat
            }
            if (ymax < l.lat) {
              ymax = l.lat
            }
            (l.lat, l.long)
          }
        }
    if (xmin == Double.MaxValue)
      None
    else {
      val extent = Extent(xmin, ymin, xmax, ymax)
      Some(ReachableVertices(subindex, extent))
    }
  }
}
