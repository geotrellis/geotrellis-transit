package commonspace.graph

import spire.syntax._

import scala.collection.mutable

/**
 * A weighted, label based multi-graph.
 */
class PackedGraph(val locations:PackedLocations, val edgeCount:Int) extends Serializable { 
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
  private val vertices = Array.ofDim[Int](vertexCount * 2)

  def foreach(f:Int=>Unit) = 
    cfor(0)(_<vertexCount, _+1)(f)

  /**
   * 'edges' is an array of that is indexed based
   * on the 'vertices' array, and contains three peices
   * of information about an edge: the target vertex, the
   * start time associated with this edge, and the weight
   * of the edge.
   *
   * ... [ v | t | w ] | [ v | t | w ] | [ v | t | w ] ...
   * where v = the connected vertex
   *       t = time edge can be traversed (-2 if all time)
   *       w = weight of traversal
   * 
   * Weight is defined as time taken to traverse the given edge,
   * plus the waiting time for that edge traversal to occur.
   * 
   * Edges with -2 start time must be in the beginning of the list
   * of edges to a specific target. Each edge to a specific target
   * are grouped together, with the edge start time increasing.
   * There is only one -2 start time edge allowed per target per vertex.
   * 
   * For example, for edges to vertices 5,6,7, the chain might be:
   * ... [ 5 | -2 | 100 ] | [ 5 | 3000 | 2000 ] | [ 6 | 1000 | 400 ] |
         [ 6 | 6000 | 234 ] | [ 7 | -2 | 41204 ] ...
   */
  val edges = Array.ofDim[Int](edgeCount * 3)

  /**
   * Given a source vertex, and a time, call a function which takes
   * in the target vertex and the weight of the edge for each outbound
   * edge from the source. If that source has both an AnyTime edge and
   * a time edge to a target, call the function against the edge with
   * the lower weight.
   */
  def foreachOutgoingEdge(source:Int,time:Int)(f:(Int,Int)=>Unit):Unit = {
    val start = vertices(source * 2)
    if(start == -1) { return }

    val end = vertices(source * 2 + 1) + start
    var target = -1

    var anyTime = false
    var anyTimeWeight = 0
    var targetSpent = false

    cfor(start)( _ < end, _ + 3 ) { i =>
      val edgeTime = edges(i + 1)
      if(edgeTime == -2) {
        if(anyTime && target != edges(i)) {
          // Call the previous target's anyTime edge
          f(target,anyTimeWeight)
        }
        anyTime = true
        anyTimeWeight = edges(i+2)
        target = edges(i)
      } else if(edgeTime >= time) {
        if(anyTime) {
          val w = edges(i+2) + (edgeTime - time)
          if(w < anyTimeWeight) {
            f(target,w)
          } else {
            f(target,anyTimeWeight)
          }
          anyTime = false
          target = edges(i)
        } else if(target == -1 || target != edges(i) || anyTime) {
          target = edges(i)
          f(edges(i),edges(i+2) + (edgeTime - time))
        }
      }
    }
    if(anyTime) {
      // Call the previous target's anyTime edge
      f(target,anyTimeWeight)
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
    var doubleAnies = 0
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
        val edges = v.edges.toList.sortBy(e => (vertexLookup(e.target),e.time.toInt))
        var anyTimeTargets = mutable.Set[Vertex]()
        var continue = false
        for(e <- edges) {
          if(!(e.time.toInt == -2 && anyTimeTargets.contains(e.target))) {
            packed.edges(edgesIndex) = vertexLookup(e.target)
            edgesIndex += 1
            packed.edges(edgesIndex) = e.time.toInt
            edgesIndex += 1
            packed.edges(edgesIndex) = e.travelTime.toInt
            edgesIndex += 1
          }
          if(e.time.toInt == -2) {
            // Only allow one anytime edge
            if(anyTimeTargets.contains(e.target)) {
              doubleAnies += 1
            } else { anyTimeTargets += e.target }
          }
        }
      }
    }

    if(doubleAnies > 0) {
      commonspace.Logger.warn(s"There were $doubleAnies cases where there were mutliple AnyTime edges.")
    }
    
    packed
  }
}
