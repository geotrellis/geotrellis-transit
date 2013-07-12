package commonspace.loader.osm

import commonspace._
import commonspace.loader.ParseResult
import commonspace.graph.{Vertex,StreetVertex,MutableGraph}

import scala.collection.mutable

import scales.utils._
import ScalesUtils._
import scales.xml._
import ScalesXml._

import java.io._

object OsmParser {
  def getAttrib(attrs:Attributes, name:String) = 
    attrs(name) match {
      case Some(attr) => attr.value
      case None => sys.error(s"Expected attribute $name does not exist")
    }

  def parseNode(attrs:Attributes,nodes:mutable.Map[String,Vertex]) = {
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

  def createWayEdges(wayNodes:Seq[Vertex],graph:MutableGraph) = {
    wayNodes.reduceLeft { (v1,v2) => 
      val w = Walking.walkDuration(v1.location,v2.location)
      addEdge(v1,v2,w,graph)
      addEdge(v2,v1,w,graph)
      v2 
    }
  }

  def parseWay(pull:XmlPull,
               wayAttribs:Attributes,
               nodes:mutable.Map[String,Vertex],
               graph:MutableGraph):List[Vertex] = {
    val wayNodes = mutable.ListBuffer[Vertex]()
    var break = !pull.hasNext
    var isHighway = false
    var wayEdges = 0

    val wayId = getAttrib(wayAttribs,"id")

    while(!break) {
      pull.next match {
        case Left(x) =>
          x match {
            case Elem(qname,attrs,ns) =>
              if(qname.local == "nd") {
                val id = getAttrib(attrs,"ref")
                if(nodes.contains(id)) {
                  val v = nodes(id)
                  wayNodes += v
                  if(!graph.contains(v)) {
                    graph += nodes(id)
                  }
                }
              } else if(qname.local == "tag") {
                val k = getAttrib(attrs,"k")
                if(k == "highway") { isHighway = true }
              }
            case item: XmlItem =>
              ()
          }
        case Right(EndElem(qname,ns)) =>
          if(qname.local == "way") {
            if(isHighway) {
              createWayEdges(wayNodes,graph)
              wayEdges += wayNodes.length - 1
            }
            break = true
          }
      }
      break = break || !pull.hasNext
    }
    if(isHighway) { wayNodes.toList } else { List[Vertex]() }
  }

  def parse(osmPath:String):ParseResult = {
    val nodes = mutable.Map[String,Vertex]()
    var ways = 0
    var wayEdges = 0
    val wayNodes = mutable.Set[Vertex]()

    val graph = MutableGraph()

    Logger.timed("Parsing OSM XML into nodes and edges...",
                 "OSM XML parsing complete.") { () =>
      val pull = pullXml(new FileReader(osmPath))

      try {
        while(pull.hasNext) {
          pull.next match {
            case Left(x) =>
              x match {
                case Elem(qname,attrs,ns) =>
                  if(qname.local == "node") {
                    parseNode(attrs,nodes)
                  } else if(qname.local == "way") {
                    val thisWayNodes = parseWay(pull,attrs,nodes,graph)
                    if(!thisWayNodes.isEmpty) {
                      ways += 1
                      wayEdges += thisWayNodes.size
                      wayNodes ++= thisWayNodes
                    }
                  }
                case item: XmlItem =>
                  ()
              }
            case Right(EndElem(qname,ns)) =>
              ()
          }
        }
      } finally {
        pull.close
      }
    }

    Logger.log(s"OSM File contains ${nodes.size} nodes, with ${ways} ways and ${wayEdges} edges.")

    val namedLocations = 
      nodes.keys
           .map { id => NamedLocation(id,nodes(id).location) }

    ParseResult(graph,NamedLocations(namedLocations),NamedWays.EMPTY)
  }
}
