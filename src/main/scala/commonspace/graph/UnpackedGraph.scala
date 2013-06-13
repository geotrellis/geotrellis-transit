package commonspace.graph

import commonspace._

import scala.collection.mutable

import spire.syntax._

case class Location(lat:Double,long:Double)
case class Edge(target:Vertex,time:Time,travelTime:Duration)

case class Vertex(location:Location) {
  val edgesToTargets = mutable.Map[Vertex,mutable.ListBuffer[Edge]]()

  def edges = {
    edgesToTargets.values.flatten
  }  

  private var _edgeCount = 0
  def edgeCount = _edgeCount

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

class UnpackedGraph(vertices:Seq[Vertex]) {
  private val _vertices = mutable.Set[Vertex]()
  private var _edgeCount = 0

  // Count Edges.
  for(v <- vertices) { 
    _edgeCount += v.edgeCount
    _vertices += v
  }
  
  def edgeCount = _edgeCount

  def vertexCount = _vertices.size

  def addEdge(source:Vertex,target:Vertex,time:Time,travelTime:Duration) = {
    _edgeCount += 1
    source.addEdge(target:Vertex,time:Time,travelTime:Duration)
  }

  def getVertices() = { _vertices.toArray }

  def pack():PackedGraph = {
    PackedGraph.pack(this)
  }

  override
  def toString = {
    var s = "(UNPACKED)\n"
    s += "Vertex\t\tEdges\n"
    s += "---------------------------------\n"
    for(v <- _vertices) {
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
  def apply(vertices:Seq[Vertex]) = {
    new UnpackedGraph(vertices)
  }

  def merge(g1:UnpackedGraph,g2:UnpackedGraph):UnpackedGraph = {
    new UnpackedGraph(g1._vertices.union(g2._vertices).toSeq)
  }
}
