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
    Array.fill[Int](graph.vertexCount)(-1)

  private val shortestPaths = 
    Array.fill[mutable.ListBuffer[Int]](graph.vertexCount)(mutable.ListBuffer[Int]())

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

    // val l = Main.context.graph.locations.getLocation(currentVertex)
    // val osm = Main.context.namedLocations(l)

    graph.foreachOutgoingEdge(currentVertex, currentTime) { (target,weight) =>
      // val tl = Main.context.graph.locations.getLocation(target)
      // val tosm = Main.context.namedLocations(tl)
      val t = currentTime + weight
      if(t <= duration) {
        if(shortestPathTimes(target) == -1 || shortestPathTimes(target) > t) {
          shortestPathTimes(target) = t
//          shortestPaths(target) += currentVertex
          shortestPaths(target) = shortestPaths(currentVertex) :+ currentVertex
//          println(s"Saving shortest path for $tosm as ${t-tripStart} seconds")
          queue += target
        }
      }
    }
  }

  def travelTimeTo(target:Int):Duration = {
    new Duration(shortestPathTimes(target) - startTime.toInt)
  }

  def travelPathTo(target:Int):Seq[Int] = {
    shortestPaths(target).toSeq ++ Seq(target)
  }
}
