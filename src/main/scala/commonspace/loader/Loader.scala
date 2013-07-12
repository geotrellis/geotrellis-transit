package commonspace.loader

import commonspace._
import commonspace.graph._
import commonspace.index._

import commonspace.loader.gtfs.GtfsFiles
import commonspace.loader.osm.OsmFileSet

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
    Logger.log("BUILDING WALKING GRAPH")
    val (walkingGraph,walkingVertices,walkingEdges) = 
      build(fileSets.filter(_.isInstanceOf[OsmFileSet]).toSeq)

    Logger.log("BUILDING TRANSIT+WALKING GRAPH")
    val (transitGraph,transitVertices,transitEdges) = 
      build(fileSets.toSeq)

    write(new File(config.dataDirectory,"walking.graph").getPath, walkingGraph)
    write(new File(config.dataDirectory,"walking.vertices").getPath, walkingVertices)
    write(new File(config.dataDirectory,"walking.edges").getPath, walkingEdges)

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
        SpatialIndex(mergedResult.graph.locations) { l =>
          (l.lat,l.long)
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
            Projection.getBoundingBox(v.location.lat, v.location.long, 100)

          val points = index.pointsInExtent(extent).toSeq

          for(location <- points) {
            val t = mergedResult.graph.vertexAtLocation(location)
            if(t.vertexType == StreetVertex) {
              val duration = Walking.walkDuration(v.location,t.location)
              mergedResult.graph.addEdge(v,t,Time.ANY,duration)
              mergedResult.graph.addEdge(t,v,Time.ANY,duration)
              transferEdgeCount += 2
            }
          }
        }
        Logger.log(s"   $transferEdgeCount tranfer edges created")
      }
    }

    val graph = mergedResult.graph

    Logger.log(s"Graph Info:")
    Logger.log(s"  Edge Count: ${graph.edgeCount}")
    Logger.log(s"  Vertex Count: ${graph.vertexCount}")

    val packed =
      Logger.timedCreate("Packing graph...",
        "Packed graph created.") { () =>
        graph.pack
      }

    (packed,mergedResult.namedLocations,mergedResult.namedWays)
  }
}
