package commonspace.graph

import commonspace._

import scala.collection.mutable

import spire.syntax._

sealed abstract class VertexType

case object StationVertex extends VertexType {
  def apply(location:Location) = Vertex(location,StationVertex)
}

case object StreetVertex extends VertexType {
  def apply(location:Location) = Vertex(location,StreetVertex)
}

case class Vertex(location:Location, vertexType:VertexType) {
  val edgesToTargets = mutable.Map[Vertex,mutable.ListBuffer[Edge]]()

  def edges = {
    edgesToTargets.values.flatten
  }  

  private var _edgeCount = 0
  def edgeCount = _edgeCount

  def hasAnyTimeEdgeTo(target:Vertex) =
    if(!edgesToTargets.contains(target)) {
      false 
    } else {
      edgesToTargets(target).filter(_.time != Time.ANY).isEmpty
    }

  def addEdge(target:Vertex,time:Time,travelTime:Duration):Unit = {
    if(!edgesToTargets.contains(target)) { edgesToTargets(target) = mutable.ListBuffer[Edge]() }

    val edgesToTarget = edgesToTargets(target)
    var set = false

    cfor(0)( _ < edgesToTarget.length && !set, _ + 1) { i =>
      if(time > edgesToTarget(i).time) {
        edgesToTarget.insert(i,Edge(target, time, travelTime))
        set = true
      }   
    }
    if(!set) { edgesToTarget += Edge(target, time, travelTime) }

    _edgeCount += 1
  }

  override
  def toString = {
    s"V(${location})"
  }

  override 
  def hashCode = location.hashCode

  override 
  def equals(other: Any) = 
    other match { 
      case that: Vertex => this.location == that.location
      case _ => false 
    }
}

case class Edge(target:Vertex,time:Time,travelTime:Duration)

class UnpackedGraph(vertices:Iterable[Vertex]) {
  private val locationsToVertices = mutable.Map[Location,Vertex]()
  private var _edgeCount = 0

  // Count Edges.
  for(v <- vertices) { 
    _edgeCount += v.edgeCount
    locationsToVertices(v.location) = v
  }
  
  def edgeCount = _edgeCount

  def vertexCount = locationsToVertices.size

  def addEdge(source:Vertex,target:Vertex,time:Time,travelTime:Duration) = {
    _edgeCount += 1
    source.addEdge(target:Vertex,time:Time,travelTime:Duration)
  }

  def getVertices() = { locationsToVertices.values.toArray }

  def getVertexAtLocation(location:Location) = 
    locationsToVertices(location)

  def getLocations() = locationsToVertices.keys

  def pack():PackedGraph = {
    PackedGraph.pack(this)
  }

  override
  def toString = {
    var s = "(UNPACKED)\n"
    s += "Vertex\t\tEdges\n"
    s += "---------------------------------\n"
    for(v <- getVertices) {
      val edgeStrings = mutable.Set[String]()
      s += s"$v\t\t"
      for(e <- v.edges) {
        edgeStrings += s"$e"
      }
      s += edgeStrings.mkString(",") + "\n"
    }
    s
  }
}

object UnpackedGraph {
  def apply(vertices:Iterable[Vertex]) = {
    new UnpackedGraph(vertices)
  }

  def merge(g1:UnpackedGraph,g2:UnpackedGraph):UnpackedGraph = {
    new UnpackedGraph(g1.getVertices.union(g2.getVertices).toSeq)
  }
}
