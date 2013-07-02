package commonspace

import commonspace.io._
import commonspace.graph._
import commonspace.index._

import java.io._

case class GraphContext(graph:PackedGraph,
                        index:SpatialIndex[Int],
                        namedLocations:NamedLocations,
                        namedWays:NamedWays)

object GraphContext {
  def getContext(path:String,fileSets:List[GraphFileSet]) =
    if(!new File(path).exists) {
      val context = buildContext(fileSets)
      val file = new FileOutputStream(path)
      val buffer = new BufferedOutputStream(file)
      val output = new ObjectOutputStream(buffer)
      try {
        output.writeObject((context.graph,context.namedLocations,context.namedWays))
      } catch {
        case e:Exception =>
          val f = new File(path)
          if(f.exists) { f.delete }
          throw e
      } finally{
        output.close()
      }
      Logger.log(s"Wrote graph to $path")
      context
    } else {
      Logger.timedCreate("Reading graph file object...","Read graph object") { () =>

        val input = new ObjectInputStream(new FileInputStream(path)) {
          override def resolveClass(desc: java.io.ObjectStreamClass): Class[_] = {
            try { Class.forName(desc.getName, false, getClass.getClassLoader) }
            catch { case ex: ClassNotFoundException => super.resolveClass(desc) }
          }
        }

        val (graph,namedLocations,namedWays) =
          try {
            input.readObject().asInstanceOf[(PackedGraph,NamedLocations,NamedWays)]
          }
          finally{
            input.close()
          }

        val index = createSpatialIndex(graph)

        GraphContext(graph,index,namedLocations,namedWays)
      }
    }

  def createSpatialIndex(graph:PackedGraph) = 
    Logger.timedCreate("Creating spatial index...", "Spatial index created.") { () =>
      SpatialIndex(0 until graph.vertexCount) { v => 
        val l = graph.locations.getLocation(v)
        (l.lat,l.long)
      }
    }
  

  def loadFileSet(fileSet:GraphFileSet):ParseResult = {
    Logger.timedCreate(s"Loading ${fileSet.name} data into unpacked graph...",
                        "Upacked graph created.") { () =>
      fileSet.parse
    }
  }

  def buildContext(fileSets:List[GraphFileSet]):GraphContext = {
    if(fileSets.length < 1) { sys.error("Argument error: Empty list of file sets.") }

    // Merge the graphs from all the File Sets into eachother.
    val mergedResult = 
      fileSets.drop(1)
              .foldLeft(loadFileSet(fileSets(0))) { (result,fileSet) =>
                 result.merge(loadFileSet(fileSet))
               }

    val index =     
      Logger.timedCreate("Creating location spatial index...", "Spatial index created.") { () =>
        SpatialIndex(mergedResult.graph.getLocations) { l =>
          (l.lat,l.long)
        }
    }

    // Now we need to make edges between stations
    Logger.timed("Creating edges between stations.", "Transfer edges created.") { () =>
      val stationVertices = 
        Logger.timedCreate(" Finding all station vertices..."," Done.") { () =>
          mergedResult.graph.getVertices.filter(_.vertexType == StationVertex)
        }

      Logger.timed(" Iterating through stations to connect to street vertices..."," Done.") { () =>
        for(v <- stationVertices) {
          val extent =
            Projection.getBoundingBox(v.location.lat, v.location.long, 100)

          for(location <- index.pointsInExtent(extent)) {
            val t = mergedResult.graph.getVertexAtLocation(location)
            val duration = Walking.walkDuration(v.location,t.location)
            mergedResult.graph.addEdge(v,t,Time.ANY,duration)
          }
        }
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

    val packedIndex = createSpatialIndex(packed)

    GraphContext(packed,packedIndex,mergedResult.namedLocations,mergedResult.namedWays)
  }
}
