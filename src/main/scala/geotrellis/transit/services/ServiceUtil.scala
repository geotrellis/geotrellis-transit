package geotrellis.transit.services

import geotrellis._
import geotrellis.admin.{Reproject,Projections}
import geotrellis.feature.Point

trait ServiceUtil {
  // Constant value to increase the lat\long raster extent by from the bounding box of the
  // reachable vertices of the shortest path tree.
  val ldelta: Float = 0.0018f
  val ldelta2: Float = ldelta * ldelta

  def reproject(wmX: Double, wmY: Double) = {
    val rp = Reproject(Point(wmX, wmY, 0), Projections.WebMercator, Projections.LatLong)
      .asInstanceOf[Point[Int]]
      .geom
    (rp.getX, rp.getY)
  }

  def stringToColor(s: String) = {
    val ns =
      if (s.startsWith("0x")) {
        s.substring(2, s.length)
      } else { s }

    val (color, alpha) =
      if (ns.length == 8) {
        (ns.substring(0, ns.length - 2), ns.substring(ns.length - 2, ns.length))
      } else {
        (ns, "FF")
      }

    (Integer.parseInt(color, 16) << 8) + Integer.parseInt(alpha, 16)
  }

  def expandByLDelta(extent:Extent) = 
    Extent(extent.xmin - ldelta,
           extent.ymin - ldelta,
           extent.xmax + ldelta,
           extent.ymax + ldelta)

  def stripJson(json:String) = {
    val sb = new StringBuilder()
    val whitespace_characters = Set(' ','\t','\r','\n')
    var quoted = false
    for(c <- json) {
      if(quoted || !whitespace_characters.contains(c)) {
        sb += c
        if(c == '"') { quoted = !quoted }
      } 
    }

    sb.toString
  }
}
