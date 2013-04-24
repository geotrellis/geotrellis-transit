package commonspace.graph

import commonspace._

import scala.collection.mutable

object ShortestPath {
  def getTree(fromLat:Double,fromLong:Double) = {
    
  }
}

class ShortestPathTree(val from:Int, val startTime:Time, graph:PackedGraph) {
  /**
   * Array containing arrival times of the current shortest
   * path to the index vertex.
   */
  val shortestPaths = Array.fill[Int](graph.vertexCount)(-1)
  shortestPaths(from) = 0

  // dijkstra's

  object VertexOrdering extends Ordering[Int] {
    def compare(a:Int, b:Int) = {
      val sA = shortestPaths(a)
      val sB = shortestPaths(b)
      if(sA == 0) { -1 }
      else if(sB == 0) { 1 }
      else {
        -(sA compare sB)
      }
    }
  }

  val queue = mutable.PriorityQueue[Int]()(VertexOrdering)

  val tripStart = startTime.toInt
  graph.foreachOutgoingEdge(from, tripStart) { (target,weight) =>
    shortestPaths(target) = tripStart + weight
    queue += target
  }

  while(!queue.isEmpty) {
    var currentVertex = queue.dequeue
    var currentTime = tripStart + shortestPaths(currentVertex)
    graph.foreachOutgoingEdge(currentVertex, currentTime) { (target,weight) =>
      if(shortestPaths(target) == -1) {
        shortestPaths(target) = currentTime + weight
        queue += target
      } else if(shortestPaths(target) > currentTime + weight) {
        shortestPaths(target) = currentTime + weight
        queue += target
      }
    }
  }

  def travelTimeTo(target:Int):Duration = {
    new Duration(shortestPaths(target) - startTime.toInt)
  }
}
