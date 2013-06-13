package commonspace.index

import com.vividsolutions.jts.index.strtree.STRtree
import com.vividsolutions.jts.geom.Coordinate
import com.vividsolutions.jts.geom.Envelope

case class Point(x:Double, y:Double) {
  def envelope:Envelope = {
    new Envelope(new Coordinate(x,y))
  }

  override
  def toString = s"Point($x,$y)"
}

object Main {

  def main(args:Array[String]) = {
    val index = new STRtree

    val points = List(
      Point(1.0,2.0)
    )

    for(point <- points) { index.insert(point.envelope, point) }    
  }
}
