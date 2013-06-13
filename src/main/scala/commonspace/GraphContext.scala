package commonspace

import commonspace.io._
import commonspace.graph._
import commonspace.index._

import java.io._

object GraphContext {
  def getContext(path:String,fileSets:List[GtfsFiles]) = {
    if(!new File(path).exists) {
      val (result,index) = getResults(fileSets)
      val graph = result.graph

      Logger.log(s"Graph Info:")
      Logger.log(s"  Edge Count: ${graph.edgeCount}")
      Logger.log(s"  Vertex Count: ${graph.vertexCount}")

      val packed =
        Logger.timedCreate("Packing graph...",
          "Packed graph created.") { () =>
          graph.pack
        }

      val context = GraphContext(packed,index,result.stops)
      val file = new FileOutputStream(path)
      val buffer = new BufferedOutputStream(file)
      val output = new ObjectOutputStream(buffer)
      try {
        output.writeObject((packed,result.stops))
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

        val (graph,stops) =
          try {
            input.readObject().asInstanceOf[(PackedGraph,Stops)]
          }
          finally{
            input.close()
          }

        val index =
          Logger.timedCreate("Creating spatial index...", "Spatial index created.") { () =>
            SpatialIndex(graph.locations.getLocations) { l => (l.lat,l.long) }
          }

        GraphContext(graph,index,stops)
      }
    }
  }

  def loadFileSet(fileSet:GtfsFiles):FileSetResult = {
    val (stops, graph) =
      Logger.timedCreate(s"Loading GTFS ${fileSet.name} data into unpacked graph...",
                          "Upacked graph created.") { () =>
      GtfsParser.parse(fileSet)
    }

    FileSetResult(stops,graph)
  }

  def getResults(fileSets:List[GtfsFiles]):(FileSetResult,SpatialIndex[Location]) = {
    if(fileSets.length < 1) { sys.error("Argument error: Empty list of file sets.") }

    // Merge the graphs from all the File Sets into eachother.
    val mergedResult = 
      fileSets.drop(1)
              .foldLeft(loadFileSet(fileSets(0))) { (result,fileSet) =>
                 result.merge(loadFileSet(fileSet))
               }

    val index = 
      Logger.timedCreate("Creating spatial index...", "Spatial index created.") { () => 
        SpatialIndex(mergedResult.graph.getVertices.map(_.location)) { l => (l.lat,l.long) }
      }

    // Now we need to make edges between stations
    Logger.timed("Creating edges between stations.", "Transfer edges created.") { () =>
      val locationsToVertices = mergedResult.graph.getVertices
                                                  .map(v => (v.location,v))
                                                  .toMap
      for(v <- mergedResult.graph.getVertices) {
        val extent =
          Projection.getBoundingBox(v.location.lat, v.location.long, 800)

        for(location <- index.pointsInExtent(extent)) {
          if(!locationsToVertices.contains(location)) {
            sys.error(s"Spatial index has location of $location but the graph " +
                       "has no vertex at that location.")
          }          
          val t = locationsToVertices(location)
          val distance =
            Projection.latLongToMeters(v.location.lat,
              v.location.long,
              location.lat,
              location.long)
          mergedResult.graph.addEdge(v,t,Time.ANY,Duration((2*distance).toInt))
        }
      }
    }
    (mergedResult,index)
  }
}

case class GraphContext(graph:PackedGraph,index:SpatialIndex[Location],stops:Stops) {
  val WALKING_SPEED = 1.4
}
