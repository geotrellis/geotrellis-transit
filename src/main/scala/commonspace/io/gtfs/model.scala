package commonspace.io.gtfs

import commonspace.Logger
import commonspace.Location
import commonspace.{Time,Duration}
import commonspace.graph.{Vertex,StationVertex}

import scala.collection.mutable

case class Stop(id:String,name:String,location:Location) {
  def createVertex = StationVertex(location)
}

case class StopTime(stop:Stop,arriveTime:Time,departTime:Time)

class Stops() extends Serializable {
  val locationToStop = mutable.Map[Location,Stop]()
  val idToStop = mutable.Map[String,Stop]()

  def add(stop:Stop) = {
    if(idToStop.contains(stop.id)) {
      if(stop != idToStop(stop.id)) {
        sys.error("Trying to add different stops with the same id.")
      }
    } else {
      idToStop(stop.id) = stop
      if(!locationToStop.contains(stop.location)) {
        // This stop is the representative stop (in case of duplicates)
        locationToStop(stop.location) = stop
      } else {
        // We want to check that this stop name is the same
        // as the representative stop.
        if(stop.name != locationToStop(stop.location).name) {
          Logger.warn("Stops with same location do not have the same name: " + 
                     s"'${stop.name}' and '${locationToStop(stop.location).name}'")
        }
      }
    }
  }

  def contains(id:String) = idToStop.contains(id)

  def count = locationToStop.keys.size

  def get(id:String) = locationToStop(idToStop(id).location)

  def mergeIn(other:Stops) = {
    for(location <- other.locationToStop.keys) {
      val thatStop = other.locationToStop(location)
      if(locationToStop.contains(location)) {
        val thisStop = locationToStop(location)
        Logger.warn(s"Merging in Stops that has a station at location ${location}, " +
                    s"which is the location of a stop in this Stops set.")
        Logger.warn(s"This stop: ${thisStop.name}  That stop: ${thatStop.name}")
        Logger.warn(s"Replacing this stop with that stop...")
        idToStop(thisStop.id) = thatStop
      }
      locationToStop(location) = thatStop
      if(idToStop.contains(thatStop.id)) {
        sys.error("Do we need to handle stop ids being the same?")
      }
    }
    this
  }
}

class Trip(val id:String) {
  val stopTimes = mutable.Map[Int,StopTime]()

  def getVertex(stop:Stop,stopsToVertices:mutable.Map[Stop,Vertex]) = 
    if(stopsToVertices.contains(stop)) {
      stopsToVertices(stop)
    } else {
      val v = stop.createVertex
      stopsToVertices(stop) = v
      v
    }

  def setEdges(stopsToVertices:mutable.Map[Stop,Vertex]):Int = {
    scrubData()
    ensureTimes()

    var count = 0
    stopTimes.keys
             .toSeq
             .sorted
             .reduce { (i1,i2) =>
               val departing = stopTimes(i1)
               val arriving = stopTimes(i2)

               val departingVertex = getVertex(departing.stop,stopsToVertices)
               val arrivingVertex = getVertex(arriving.stop,stopsToVertices)

               departingVertex.addEdge(arrivingVertex,
                                       departing.departTime,
                                       arriving.arriveTime - departing.departTime)
               count += 1
               i2
              }
    count
  }


  /**
   * Performs any necessary cleanup of the data.
   * e.g. Removing duplicates
   */
  def scrubData() = {
    
  }

  /**
   * Makes sure each StopTime has a known arrival and departure time;
   * will interpolate times if necessary.
   */
  def ensureTimes() = {

  }
}
