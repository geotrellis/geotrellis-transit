package commonspace.graph

import spire.syntax._

import scala.collection.mutable

/**
 * A weighted, label based multi-graph.
 */
class PackedGraph(val locations:PackedLocations, val edgeCount:Int) { 
  /**
   * 'vertices' is an array that is indexed by vertex id,
   * that contains two peices of information:
   * the start index of the 'edges' array for
   * a given vertex, and the number of outbound
   * edges to read from that start index.
   *
   * ... [ i | n ] | [ i | n ] | [ i | n ] ...
   * where i = index in edges array
   *       n = number of edges to read
   */
  val vertexCount = locations.vertexCount
  val vertices = Array.ofDim[Int](vertexCount * 2)


  /**
   * 'edges' is an array of that is indexed based
   * on the 'vertices' array, and contains three peices
   * of information about an edge: the target vertex, the
   * start time associated with this edge, and the weight
   * of the edge.
   *
   * ... [ v | t | w ] | [ v | t | w ] | [ v | t | w ] ...
   * where v = the connected vertex
   *       t = time edge can be traversed (negative if all time)
   *       w = weight of traversal
   * 
   * Weight is defined as time taken to traverse the given edge.
   */
  val edges = Array.ofDim[Int](edgeCount * 3)

  /**
   * Given a source vertex, and a time, call a function which takes
   * in the target vertex and the weight of the edge for each outbound
   * edge from the source
   */
  def foreachOutgoingEdge(source:Int,time:Int)(f:(Int,Int)=>Unit):Unit = {
    val start = vertices(source * 2)
    if(start == -1) { return }

    val end = vertices(source * 2 + 1) + start
    var target = -1

    cfor(start)( _ < end, _ + 3 ) { i =>
      if(edges(i + 1) >= time || edges(i + 1) == -2) {
        if(target == -1 || target != edges(i)) {
          target = edges(i)
          f(edges(i),edges(i+2))
        }
      }
    }
  }

  override
  def toString() = {
    var s = "(PACKED)\n"
    s += "Vertex\t\tEdges\n"
    s += "---------------------------------\n"
    cfor(0)( _ < vertexCount, _ + 1) { v =>
      val edgeStrings = mutable.Set[String]()
      s += s"$v (at ${locations.getLocation(v)})\t\t"
      val start = vertices(v * 2)
      if(start == -1) {
        s += "\n"
      } else {                   
        val end = vertices(v * 2 + 1) + start
        cfor(start)(_ < end, _ + 3) { i =>
          edgeStrings += s"(${edges(i)},${edges(i+1)},${edges(i+2)})"
                                   }
        s += edgeStrings.mkString(",") + "\n"
      }
    }
    s
  }
}

object PackedGraph {
  def pack(unpacked:UnpackedGraph):PackedGraph = {
    val vertices = unpacked.getVertices
    val size = vertices.length

    val vertexLookup = vertices.zipWithIndex.toMap
    // Pack locations
    val locations = new PackedLocations(size)
    val packed = new PackedGraph(locations, unpacked.edgeCount)
    
    // Pack edges
    var edgesIndex = 0
    cfor(0)(_ < size, _ + 1) { i =>
      locations.setLocation(i,vertices(i).location.lat,
                              vertices(i).location.long)
      val v = vertices(i)
      if(v.edgeCount == 0) {
        packed.vertices(i*2) = -1
        packed.vertices(i*2+1) = 0
      } else {
        packed.vertices(i*2) = edgesIndex
        packed.vertices(i*2+1) = v.edgeCount*3
        for(e <- v.edges) {
          packed.edges(edgesIndex) = vertexLookup(e.target)
          edgesIndex += 1
          packed.edges(edgesIndex) = e.time.toInt
          edgesIndex += 1
          packed.edges(edgesIndex) = e.travelTime.toInt
          edgesIndex += 1        
        }
      }
    }
    
    packed
  }
}
