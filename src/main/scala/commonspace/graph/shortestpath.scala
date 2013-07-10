package commonspace.graph

import commonspace._

import scala.collection.mutable

import spire.syntax._

case class PathEdge(vertex:Int,duration:Int) {
  def toStr = {
    s"Path(Vertex: $vertex,Duration: $duration seconds)"
  }
}

object ShortestPathTree {
  def apply(from:Int, startTime:Time, graph:PackedGraph) = 
    new ShortestPathTree(from,startTime,graph,None)

  def apply(from:Int,startTime:Time,graph:PackedGraph,maxDuration:Duration) =
    new ShortestPathTree(from,startTime,graph,Some(maxDuration))
}

class ShortestPathTree(val startVertex:Int,
                       val startTime:Time,
                       graph:PackedGraph,
                       val maxDuration:Option[Duration]) {
  /**
   * Array containing arrival times of the current shortest
   * path to the index vertex.
   */
  private val shortestPathTimes = 
    if(Main.sptArray != null) { Main.sptArray }
    else { Array.fill[Int](graph.vertexCount)(-1) }

  // private val shortestPaths =
  //   Array.fill[mutable.ListBuffer[Int]](graph.vertexCount)(mutable.ListBuffer[Int]())  ///////DEBUG

  private val _reachableVertices = 
    mutable.ListBuffer[Int]()

  def reachableVertices:Set[Int] = _reachableVertices.toSet

  shortestPathTimes(startVertex) = 0

  // dijkstra's

  object VertexOrdering extends Ordering[Int] {
    def compare(a:Int, b:Int) = {
      val sA = shortestPathTimes(a)
      val sB = shortestPathTimes(b)
      if(sA == 0) { -1 }
      else if(sB == 0) { 1 }
      else {
        -(sA compare sB)
      }
    }
  }

  val queue = mutable.PriorityQueue[Int]()(VertexOrdering)

  val tripStart = startTime.toInt
  val duration = maxDuration.getOrElse(Duration(Int.MaxValue)).toInt + tripStart

  graph.foreachOutgoingEdge(startVertex,tripStart) { (target,weight) =>
    shortestPathTimes(target) = tripStart + weight
    queue += target
  }

  while(!queue.isEmpty) {
    val currentVertex = queue.dequeue
    val currentTime = shortestPathTimes(currentVertex)

    graph.foreachOutgoingEdge(currentVertex, currentTime) { (target,weight) =>
      val t = currentTime + weight
      if(t <= duration) {
        val currentTime = shortestPathTimes(target)
        if(currentTime == -1 || currentTime > t) {
          _reachableVertices += target
          shortestPathTimes(target) = t
          queue += target

//          shortestPaths(target) = shortestPaths(currentVertex) :+ currentVertex  ///////DEBUG
        }
      }
    }
  }

  def travelTimeTo(target:Int):Duration = {
    new Duration(shortestPathTimes(target) - startTime.toInt)
  }

  def travelPathTo(target:Int):Seq[Int] = {
    null//  shortestPaths(target).toSeq   ////////DEBUG
  }
}
