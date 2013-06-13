package commonspace.io

import commonspace._
import commonspace.graph._

import scala.collection.mutable

case class GtfsFiles(name:String,stopsPath:String,stopTimesPath:String) {
  if(!new java.io.File(stopsPath).exists) { 
    sys.error(s"Stops file $stopsPath does not exist.")
  }
  if(!new java.io.File(stopTimesPath).exists) { 
    sys.error(s"Stop Times file $stopTimesPath does not exist.")
  }
}

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

case class Stop(id:String,name:String,location:Location) {
  def createVertex = new Vertex(location)
}

case class StopTime(stop:Stop,arriveTime:Time,departTime:Time)

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

object GtfsParser {
  val gtfsTimeRegex = """(\d?\d):(\d\d):(\d\d)""".r

  def parse(files:GtfsFiles):(Stops,UnpackedGraph) = {
    val stops = parseStops(files.stopsPath)

    val trips = parseStopTimes(stops, files.stopTimesPath)

    val stopsToVertices = mutable.Map[Stop,Vertex]()

    val edges = Logger.timedCreate("Creating edges for trips...","Done creating edges.") { () => 
      trips.map(_.setEdges(stopsToVertices)).foldLeft(0)(_+_) 
    }
    Logger.log(s"$edges edges set.")
    val vertices = stopsToVertices.values.toSeq
    (stops,UnpackedGraph(vertices))
  }

  def parseStops(stopsPath:String):Stops = {
    val stops = new Stops()
    Logger.timed("Parsing stops file...","Finished parsing stops.") { () =>
      for(row <- Csv.fromPath(stopsPath)) {
        val id = row("stop_id")
        val name = row("stop_name")
        val lat = row("stop_lat").toDouble
        val long = row("stop_lon").toDouble
        stops.add(Stop(id,name,Location(lat,long)))
      }
    }
    Logger.log(s"${stops.count} stops parsed.")
    stops
  }

  def parseStopTimes(stops:Stops, stopTimesPath:String) = {
    val trips = mutable.Map[String,Trip]()
    var count = 0
    Logger.timed("Parsing stop times file...","Finished parsing stop times.") { () =>
      for(row <- Csv.fromPath(stopTimesPath)) {
        val tripId = row("trip_id")
        if(!trips.contains(tripId)) { trips(tripId) = new Trip(tripId) }

        val trip = trips(tripId)
        val stopId = row("stop_id")

        if(!stops.contains(stopId)) { 
          sys.error(s"Stop Times file at $stopTimesPath contains stop $stopId " +
                     "that is not included in the stops file.") 
        }

        val stop = stops.get(stopId)
        
        val seq = row("stop_sequence").toInt
        val arriveTime = parseTime(row("arrival_time"))
        val departTime = parseTime(row("departure_time"))
        
        trip.stopTimes(seq) = StopTime(stop,arriveTime,departTime)
        count += 1
      }
    }
    Logger.log(s"$count stop times parsed for ${trips.size} trips.")
    trips.values
  }

  def parseTime(s:String):Time = {
    val gtfsTimeRegex(hour,minute,second) = s
    Time(second.toInt + (minute.toInt*60) + (hour.toInt*3600))
  }
}
