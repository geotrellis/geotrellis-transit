package commonspace.loader.osm

import commonspace._
import commonspace.loader.ParseResult
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

  def addEdge(v1:Vertex,v2:Vertex,w:Duration) = {
    if(!v1.hasAnyTimeEdgeTo(v2)) {
      v1.addEdge(v2,Time.ANY,w)
    }
  }

  def createWayEdges(wayNodes:Seq[(String,Vertex)]) = {
    wayNodes.reduceLeft { (v1,v2) => 
      val w = Walking.walkDuration(v1._2.location,v2._2.location)
      addEdge(v1._2,v2._2,w)
      addEdge(v2._2,v1._2,w)
      if(v1._1 == "765755083" || v2._1 == "765755083") {
        println(s"CREATING EDGE BETWEEN ${v1._1} and ${v2._1}")
        val edges = 
          if(v1._1 == "765755083") {
            v1._2.edges
          } else {
            v2._2.edges
          }
        for(e <- edges) { println(e) }

      }
      v2 
    }
  }

  def parseWay(pull:XmlPull,wayAttribs:Attributes,nodes:mutable.Map[String,Vertex]):List[Vertex] = {
    val wayNodes = mutable.ListBuffer[(String,Vertex)]()
    var break = !pull.hasNext
    var isHighway = false
    var wayEdges = 0

    var prev = ""

    val wayId = getAttrib(wayAttribs,"id")

    while(!break) {
      pull.next match {
        case Left(x) =>
          x match {
            case Elem(qname,attrs,ns) =>
              if(qname.local == "nd") {
                val id = getAttrib(attrs,"ref")
                if(nodes.contains(id)) {
                  wayNodes += ((id,nodes(id)))
                  if(id == "765755083" || prev == "769425229") {
                    if(id == "769425229" || prev == "765755083") {
                      println(s"   ERROR AT WAY $wayId")
                    }
                  }
                  prev = id
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
              createWayEdges(wayNodes)
              wayEdges += wayNodes.length - 1
            }
            break = true
          }
      }
      break = break || !pull.hasNext
    }
    if(isHighway) { wayNodes.map(_._2).toList } else { List[Vertex]() }
  }

  def parse(osmPath:String):ParseResult = {
    val nodes = mutable.Map[String,Vertex]()
    var ways = 0
    var wayEdges = 0
    val wayNodes = mutable.Set[Vertex]()

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
                    val thisWayNodes = parseWay(pull,attrs,nodes)
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

    val graph = 
      UnpackedGraph(wayNodes)

    val namedLocations = 
      nodes.keys
           .map { id => NamedLocation(id,nodes(id).location) }

    ParseResult(graph,NamedLocations(namedLocations),NamedWays.EMPTY)
  }
}
