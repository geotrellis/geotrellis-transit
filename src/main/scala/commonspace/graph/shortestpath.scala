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
  def apply(from:Int, startTime:Time, graph:PackedGraph) = {
    val initialPaths = mutable.Set[PathEdge]()
    val tripStart = startTime.toInt
    graph.foreachOutgoingEdge(from, tripStart) { (target,weight) =>
      initialPaths += PathEdge(target, tripStart + weight)
    }

    val spt = new ShortestPathTree(initialPaths,startTime,graph)
    spt.shortestPathTimes(from) = 0
    spt
  }

  def apply(initialPaths:Iterable[PathEdge],startTime:Time,graph:PackedGraph) =
    new ShortestPathTree(initialPaths,startTime,graph)
}

class ShortestPathTree(initialPaths:Iterable[PathEdge], val startTime:Time, graph:PackedGraph) {
  /**
   * Array containing arrival times of the current shortest
   * path to the index vertex.
   */
  private val shortestPathTimes = Array.fill[Int](graph.vertexCount)(-1)

  private val shortestPaths = Array.fill[mutable.ListBuffer[Int]](graph.vertexCount)(mutable.ListBuffer[Int]())

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

  for(path <- initialPaths) {
    shortestPathTimes(path.vertex) = path.duration
    queue += path.vertex
  }

  val tripStart = startTime.toInt

  while(!queue.isEmpty) {
    var currentVertex = queue.dequeue
    var currentTime = tripStart + shortestPathTimes(currentVertex)
    graph.foreachOutgoingEdge(currentVertex, currentTime) { (target,weight) =>
      if(shortestPathTimes(target) == -1) {
        shortestPathTimes(target) = currentTime + weight
        shortestPaths(target) += currentVertex
        queue += target
      } else if(shortestPathTimes(target) > currentTime + weight) {
        shortestPathTimes(target) = currentTime + weight
        shortestPaths(target) = shortestPaths(currentVertex) :+ currentVertex
        queue += target
      }
    }
  }

  def travelTimeTo(target:Int):Duration = {
    new Duration(shortestPathTimes(target) - startTime.toInt)
  }

  def travelPathTo(target:Int):Seq[Int] = {
    shortestPaths(target).toSeq
  }
}
