package commonspace

import commonspace.graph._

import com.github.mdr.ascii._

import scala.collection.mutable

case class ShortestPathTestCase(graph:PackedGraph, source:Int, expected:Map[Location,Int])

object ShortestPathGraphs {
  def simple = {
    val graph = TestGraph.fromDiagram("""
     +-+   [7]    +-+
 ----|1|--------->|2|----
 |   +-+          +-+   |
 |    | [9]   [10] |    |[15]
 |    ------ -------    |
 |[14]     | |          |
 v         v v          v
+-+  [2] +----+  [11]  +-+
|6|<-----| 3  |------->|4|
+-+      +----+        +-+
 |                      |
 |[9]    +-+    [6]     |
 ------->|5|<------------
         +-+
                                      """, s => Location(s.toDouble,s.toDouble))
    val shortestPaths = Map[Location,Int](
      Location(1.0,1.0) -> 0,
      Location(2.0,2.0) -> 7,
      Location(3.0,3.0) -> 9,
      Location(4.0,4.0) -> 20,
      Location(5.0,5.0) -> 20,
      Location(6.0,6.0) -> 11
    )
    val packed = graph.pack
    val source = packed.locations.getVertexAt(1.0,1.0)
    ShortestPathTestCase(packed,source,shortestPaths)
  }
}

object SampleGraph {
  def noTimes = {
    TestGraph.fromDiagram("""
     +-+   [7]    +-+
 ----|1|--------->|2|----           +-+  [10]   +-+
 |   +-+          +-+   |           |8|---------|9|
 |    | [10]   [9] |    |[14]       +-+         +-+
 |    ------ -------    |            |           |
 |[15]     | |          |            |[8]        |
 v         v v          v            |           |
+-+      +----+  [3]   +-+    [10]  +-+          |
|6|      | 3  |------->|4|----------|7|          |[22]
+-+      +----+        +-+          +-+          |
 |                      |                        |
 |[6]    +-+    [9]     |                        |
 ------->|5|<------------                        |
         +-+                                     |
          ^                                      |
          |                                      |
          ----------------------------------------
                           """, s => Location(s.toDouble,s.toDouble))
  }

  def withTimes = {
    val vertices = List(
      Vertex(Location(1.0,1.0)),
      Vertex(Location(2.0,1.0)),
      Vertex(Location(3.0,1.0)),
      Vertex(Location(4.0,1.0)),
      Vertex(Location(5.0,1.0)),
      Vertex(Location(6.0,1.0)),
      Vertex(Location(7.0,1.0)),
      Vertex(Location(8.0,1.0)),
      Vertex(Location(9.0,1.0)),
      Vertex(Location(10.0,1.0))
    )

    for(u <- vertices) {
      for(v <- vertices) {
        if(u != v) {
          u.addEdge(v, Time(u.location.lat.toInt*10),Duration(u.location.lat.toInt))
        }
      }
    }
    UnpackedGraph(vertices.toSeq)
  }

  def withTimesAndAnyTimes = {
    val vertices = List(
      Vertex(Location(1.0,1.0)),
      Vertex(Location(2.0,1.0)),
      Vertex(Location(3.0,1.0)),
      Vertex(Location(4.0,1.0)),
      Vertex(Location(5.0,1.0)),
      Vertex(Location(6.0,1.0)),
      Vertex(Location(7.0,1.0)),
      Vertex(Location(8.0,1.0)),
      Vertex(Location(9.0,1.0)),
      Vertex(Location(10.0,1.0))
    )

    for(u <- vertices) {
      for(v <- vertices) {
        if(u != v) {
          u.addEdge(v, Time(u.location.lat.toInt*10),Duration(u.location.lat.toInt))
          u.addEdge(v, Time.ANY,Duration(20))
        }
      }
    }
    UnpackedGraph(vertices.toSeq)
  }
}

object TestGraph {
  def fromDiagram(diagram:String):UnpackedGraph = fromDiagram(diagram, s => Location(0.0,0.0))

  def fromDiagram(diagram:String, locationSet:(String)=>Location):UnpackedGraph = {
    val d = Diagram(diagram)
    val vertices = new mutable.HashMap[Box,Vertex]()
    d.allBoxes.map { b =>
      if(!vertices.contains(b)) { vertices(b) = new Vertex(locationSet(b.text)) }
      
      b.edges
       .filter { e =>
         if(e.box1 == b) {
           e.hasArrow2
         } else {
           e.hasArrow1
         }
       }
       .foreach { e =>
         val targetBox = 
           if(e.box1 == b) {
             e.box2
           } else {
             e.box1
           }

         if(!vertices.contains(targetBox)) { vertices(targetBox) = new Vertex(locationSet(targetBox.text)) }

         val weight = Duration(e.label.get.toInt)
         vertices(b).addEdge(vertices(targetBox),Time.ANY, weight)
       }
     }

    UnpackedGraph(vertices.values.toSeq)
  }
}
