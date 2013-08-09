package geotrellis.transit.loader

import geotrellis.transit._
import geotrellis.network._
import geotrellis.network.graph._
import geotrellis.network.index._

import geotrellis.transit.loader.gtfs.GtfsFiles
import geotrellis.transit.loader.osm.OsmFileSet

import scala.collection.mutable

import java.io._

object Loader {
  def write[T](path:String,o:T) = {
    val file = new FileOutputStream(path)
    val buffer = new BufferedOutputStream(file)
    val output = new ObjectOutputStream(buffer)
    try {
      output.writeObject(o)
    } catch {
      case e:Exception =>
        val f = new File(path)
        if(f.exists) { f.delete }
        throw e
    } finally{
      output.close()
    }
    Logger.log(s"Wrote graph to $path")
  }

  def buildGraph(config:GraphConfiguration,fileSets:Iterable[GraphFileSet]) = {
    Logger.log("BUILDING GRAPH")
    val (transitGraph,transitVertices,transitEdges) = 
      build(fileSets.toSeq)

    write(new File(config.dataDirectory,"transit.graph").getPath, transitGraph)
    write(new File(config.dataDirectory,"transit.vertices").getPath, transitVertices)
    write(new File(config.dataDirectory,"transit.edges").getPath, transitEdges)

    Logger.log(s"Wrote graph data to ${config.dataDirectory}")
  }

  def loadFileSet(fileSet:GraphFileSet):ParseResult = {
    Logger.timedCreate(s"Loading ${fileSet.name} data into unpacked graph...",
                        "Upacked graph created.") { () =>
      fileSet.parse
    }
  }

  def build(fileSets:Seq[GraphFileSet]):(TransitGraph,NamedLocations,NamedWays) = {
    if(fileSets.length < 1) { sys.error("Argument error: Empty list of file sets.") }

    // Merge the graphs from all the File Sets into eachother.
    val mergedResult = 
      fileSets.drop(1)
              .foldLeft(loadFileSet(fileSets(0))) { (result,fileSet) =>
                 result.merge(loadFileSet(fileSet))
               }

    val index =     
      Logger.timedCreate("Creating location spatial index...", "Spatial index created.") { () =>
        SpatialIndex(mergedResult.graph.vertices.filter(_.vertexType == StreetVertex)) { v =>
          (v.location.lat,v.location.long)
        }
      }

    Logger.timed("Creating edges between stations.", "Transfer edges created.") { () =>
      val stationVertices = 
        Logger.timedCreate(" Finding all station vertices..."," Done.") { () =>
          mergedResult.graph.vertices.filter(_.vertexType == StationVertex).toSeq
        }

      Logger.timed(s" Iterating through ${stationVertices.length} " + 
                   "stations to connect to street vertices...",
                   s" Done.") { () =>
        var transferEdgeCount = 0
        for(v <- stationVertices) {
          val extent =
            Distance.getBoundingBox(v.location.lat, v.location.long, 100)

          index.nearestInExtent(extent,v.location) match {
            case Some(nearest) =>
              val duration = Walking.walkDuration(v.location,nearest.location)
              mergedResult.graph.addEdge(v,WalkEdge(nearest,duration))
              mergedResult.graph.addEdge(nearest,WalkEdge(v,duration))
              transferEdgeCount += 2
            case _ => //pass
          }
        }
        Logger.log(s"   $transferEdgeCount tranfer edges created")
      }
    }

    val graph = mergedResult.graph

    val we = graph.edgeCount(WalkEdge)
    val be = graph.edgeCount(BikeEdge)
    val te = graph.edgeCount(TransitEdge)

    Logger.log(s"Graph Info:")
    Logger.log(s"  Walk Edge Count: ${we}")
    Logger.log(s"  Bike Edge Count: ${be}")
    Logger.log(s"  Transit Edge Count: ${te}")
    Logger.log(s"  Total Edge Count: ${we+be+te}")
    Logger.log(s"  Vertex Count: ${graph.vertexCount}")

    val packed =
      Logger.timedCreate("Packing graph...",
        "Packed graph created.") { () =>
        graph.pack
      }

    (packed,mergedResult.namedLocations,mergedResult.namedWays)
  }
}
