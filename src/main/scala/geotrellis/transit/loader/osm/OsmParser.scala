package geotrellis.transit.loader.osm

import geotrellis.transit._
import geotrellis.transit.loader.ParseResult
import geotrellis.network._
import geotrellis.network.graph.{Vertex,StreetVertex,MutableGraph}

import scala.collection.mutable

import scala.io.Source
import scala.xml.MetaData
import scala.xml.pull._

import java.io._

object OsmParser {
  def getAttrib(attrs:MetaData, name:String) = {
    val attr = attrs(name)
    if(attr == null) {
      sys.error(s"Expected attribute $name does not exist")
    }
    if(attr.length > 1) {
      sys.error(s"Expected attribute $name has more than one return.")
    }
    attr(0).text
  }

  def parseNode(attrs:MetaData,nodes:mutable.Map[String,Vertex]) = {
    val id = getAttrib(attrs, "id")
    val lat = getAttrib(attrs,"lat").toDouble
    val lon = getAttrib(attrs,"lon").toDouble

    nodes(id) = StreetVertex(Location(lat,lon),id)
  }

  def addEdge(v1:Vertex,v2:Vertex,w:Duration,graph:MutableGraph) = {
    val edgeSet = graph.edges(v1)
    if(!edgeSet.hasAnyTimeEdgeTo(v2)) {
      edgeSet.addEdge(v2,Time.ANY,w)
    }
  }

  def createWayEdges(wayNodes:Seq[Vertex],wayInfo:WayInfo,graph:MutableGraph) = {
    wayNodes.reduceLeft { (v1,v2) =>
      if(!graph.contains(v1)) { graph += v1 }
      if(!graph.contains(v2)) { graph += v2 }
      if(wayInfo.isWalkable) {
        val w = Walking.walkDuration(v1.location,v2.location)
        addEdge(v1,v2,w,graph)
        addEdge(v2,v1,w,graph)
      }

      // TODO: Implement biking
      // if(wayInfo.isBikable) {
      //   wayInfo.direction match {
      //     case OneWay =>
      //       addEdge(v1,v2,w,graph)
      //     case OneWayReverse =>
      //       addEdge(v2,v1,w,graph)
      //     case BothWays =>
      //       addEdge(v1,v2,w,graph)
      //       addEdge(v2,v1,w,graph)
      //   }
      // }

      v2 
    }
  }

  def parseWay(parser:XMLEventReader,
               wayAttribs:MetaData,
               nodes:mutable.Map[String,Vertex],
               graph:MutableGraph):List[Vertex] = {
    val wayNodes = mutable.ListBuffer[Vertex]()
    var break = !parser.hasNext
    var wayInfo:WayInfo = null
    var wayEdges = 0

    val wayId = getAttrib(wayAttribs,"id")

    val tags = mutable.Map[String,String]()

    while(!break) {
      parser.next match {
        case EvElemStart(_,"nd",attrs,_) =>
          val id = getAttrib(attrs,"ref")
          if(nodes.contains(id)) {
            val v = nodes(id)
            wayNodes += v
          }
        case EvElemStart(_,"tag",attrs,_) =>
          val k = getAttrib(attrs,"k")
          val v = getAttrib(attrs,"v")
          tags(k) = v
        case EvElemEnd(_,"way") =>
          wayInfo = WayInfo.fromTags(tags.toMap)
          if(wayInfo.isWalkable) {
            createWayEdges(wayNodes,wayInfo,graph)
            wayEdges += wayNodes.length - 1
          }
          break = true
        case _ => // pass
      }
      break = break || !parser.hasNext
    }

    wayInfo match {
      case _:Walkable => wayNodes.toList
      case x => 
        List[Vertex]()
    }
  }

  def parse(osmPath:String):ParseResult = {
    val nodes = mutable.Map[String,Vertex]()
    var ways = 0
    var wayEdges = 0
    val wayNodes = mutable.Set[Vertex]()

    val graph = MutableGraph()

    Logger.timed("Parsing OSM XML into nodes and edges...",
                 "OSM XML parsing complete.") { () =>
      val source = Source.fromFile(osmPath)

      try {
        val parser = new XMLEventReader(source)
        while(parser.hasNext) {
          parser.next match {
            case EvElemStart(_,"node",attrs,_) =>
              parseNode(attrs,nodes)
            case EvElemStart(_,"way",attrs,_) =>
              val thisWayNodes = parseWay(parser,attrs,nodes,graph)
              if(!thisWayNodes.isEmpty) {
                ways += 1
                wayEdges += thisWayNodes.size
                wayNodes ++= thisWayNodes
              }
            case _ => //pass
          }
        }
      } finally {
        source.close
      }
    }

    Logger.log(s"OSM File contains ${nodes.size} nodes, with ${ways} ways and ${wayEdges} edges.")

    val namedLocations = 
      nodes.keys
           .map { id => NamedLocation(id,nodes(id).location) }

    ParseResult(graph,NamedLocations(namedLocations),NamedWays.EMPTY)
  }
}
