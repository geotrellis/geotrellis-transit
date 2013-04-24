package commonspace.graph

import commonspace._

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers

import scala.collection.mutable

class PackedGraphSpec extends FunSpec
                         with ShouldMatchers {
  describe("PackedGraph") {
    it("should pack a graph correctly.") {
      val unpacked = SampleGraph.noTimes
      val packed = unpacked.pack()
      
      val packedToUnpacked = (for(v <- 0 until packed.vertexCount) yield {
        val location = packed.locations.getLocation(v)
        unpacked.getVertices.find(_.location == location) match {
          case Some(vertex) =>
            (v,vertex)
          case _ =>
            sys.error(s"Could not find vertex $v in unpacked graph.")
        }
      }).toMap

      for(v <- packedToUnpacked.keys) {
        val unpackedEdges = packedToUnpacked(v).edges
        val packedEdges = mutable.ListBuffer[Edge]()
        packed.foreachOutgoingEdge(v,0) { (t,w) =>
          packedEdges += Edge(packedToUnpacked(t),Time.ANY,Duration(w))
        }
        packedEdges.toSeq should be (unpackedEdges.toSeq)
      }
    }
  }                           
}

