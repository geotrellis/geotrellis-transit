package commonspace.io

import commonspace.{NamedLocations,NamedWays}
import commonspace.graph.UnpackedGraph

case class ParseResult(graph:UnpackedGraph,namedLocations:NamedLocations,namedWays:NamedWays) {
  def merge(other:ParseResult) = {
    ParseResult(UnpackedGraph.merge(graph,other.graph),
                namedLocations.mergeIn(other.namedLocations),
                namedWays.mergeIn(other.namedWays))
  }
}

trait GraphFileSet {
  val name:String

  def parse():ParseResult
}
