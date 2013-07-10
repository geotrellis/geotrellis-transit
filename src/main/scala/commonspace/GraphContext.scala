package commonspace

import commonspace.graph._
import commonspace.index._

import java.io._

case class Context(walking:GraphContext,transit:GraphContext)

case class GraphContext(graph:PackedGraph,
                        index:SpatialIndex[Int],
                        namedLocations:NamedLocations,
                        namedWays:NamedWays)
