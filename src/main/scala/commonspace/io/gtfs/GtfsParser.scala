package commonspace.io.gtfs

import commonspace.Logger
import commonspace.{Time,Duration}
import commonspace.Location
import commonspace.graph.{Vertex, UnpackedGraph}

import scala.collection.mutable

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
