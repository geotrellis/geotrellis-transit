package commonspace.io.osm

import commonspace._
import commonspace.io.ParseResult
import commonspace.graph.{Vertex,StreetVertex,UnpackedGraph}

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

    nodes(id) = StreetVertex(Location(lat,lon))
  }

  def parseWay(wayNodes:Seq[Vertex]) = {
    wayNodes.reduceLeft { (v1,v2) => 
      v1.addEdge(v2,Time.ANY,Walking.walkDuration(v1.location,v2.location))
      v2 
    }
  }

  def parse(osmPath:String):ParseResult = {
    val nodes = mutable.Map[String,Vertex]()
    var ways = 0
    var wayEdges = 0
    var nodesMissing = 0

    Logger.timed("Parsing OSM XML into nodes and edges...",
                 "OSM XML parsing complete.") { () =>
      val pull = pullXml(new FileReader(osmPath))

      try {
        val wayNodes = mutable.ListBuffer[Vertex]()

        for(event <- pull) {
          event match {
            case Left(x) =>
              x match {
                case Elem(qname,attrs,ns) =>
                  if(qname.local == "node") {
                    parseNode(attrs,nodes)
                  } else if(qname.local == "nd") {
                    // Node in way
                    val id = getAttrib(attrs,"ref")
                    if(nodes.contains(id)) { 
                      wayNodes += nodes(id) 
                    } else {
                      nodesMissing += 1
                    }
                  }
                case item: XmlItem =>
                  ()
              }
            case Right(EndElem(qname,ns)) =>
              if(qname.local == "way") {
                parseWay(wayNodes)
                ways += 1
                wayEdges += wayNodes.length - 1
                wayNodes.clear
              }
          }
        }
      } finally {
        pull.close
      }
    }
    if(nodesMissing > 0) {
      Logger.warn(s"  Ways contained $nodesMissing node ids that were not found in the nodes block.")
    }
    Logger.log(s"OSM File contains ${nodes.size} nodes, with ${ways} ways and ${wayEdges} edges.")

    val graph = 
      UnpackedGraph(nodes.values)

    ParseResult(graph,NamedLocations.EMPTY,NamedWays.EMPTY)
  }
}
