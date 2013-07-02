package commonspace.graph

import commonspace.Location

import scala.collection.mutable
import spire.syntax._

class PackedLocations(val vertexCount:Int) extends Serializable {
  /**
   * 'locations' is an Array that contains latitude and longitude
   * coordinates, where the index of the latitude coordinate is 2 times
   * the ID of the vertex at that location.
   */
  val locations = Array.ofDim[Double](vertexCount*2)

  private val locationsToVertices = mutable.Map[(Double,Double),Int]()
  private val verticesToLocations = mutable.Map[Int,(Double,Double)]()

  def getLocations() = {
    for(t <- locationsToVertices.keys) yield { Location(t._1,t._2) }
  }

  def setLocation(vertex:Int,lat:Double,long:Double) = {
    val l = (lat,long)
    verticesToLocations(vertex) = l
    locationsToVertices(l) = vertex
  }

  def getVertexAt(lat:Double,long:Double):Int = 
    locationsToVertices.getOrElse((lat,long),-1)

  def getLocation(vertex:Int) = {
    val t = verticesToLocations(vertex)
    Location(t._1,t._2)
  }
}
