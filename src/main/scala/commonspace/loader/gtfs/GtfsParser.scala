package commonspace.loader.gtfs

import commonspace.Logger
import commonspace.{Time,Duration}
import commonspace.Location
import commonspace.graph.{Vertex, MutableGraph}

import scala.collection.mutable

object GtfsParser {
  val gtfsTimeRegex = """(\d?\d):(\d\d):(\d\d)""".r

  def parse(files:GtfsFiles):(Stops,MutableGraph) = {
    val g = MutableGraph()

    val weekdayServiceId = parseWeekdayServiceId(files.calendarPath)

    val stops = parseStops(files.stopsPath)
    val trips = parseTrips(files.tripsPath,weekdayServiceId)
    parseStopTimes(stops, trips, files.stopTimesPath, weekdayServiceId)

    val stopsToVertices = mutable.Map[Stop,Vertex]()

    val edges = Logger.timedCreate("Creating edges for trips...","Done creating edges.") { () => 
      trips.values.map(_.setEdges(stopsToVertices,g)).foldLeft(0)(_+_)
    }
    Logger.log(s"$edges edges set.")
    val vertices = stopsToVertices.values.toSeq
    (stops,g)
  }

  def parseWeekdayServiceId(calendarPath:String):String = {
    val weekdayServiceIds = 
      (for(row <- Csv.fromPath(calendarPath)) yield {
        if(row("monday") != "0") Some(row("service_id")) else None
      }).flatten.toList

    if(weekdayServiceIds.length != 1) {
      sys.error(s"There were ${weekdayServiceIds.length} services that have Monday service, " +
                 "need exactly 1")
    }
    weekdayServiceIds(0)
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

  def parseTrips(tripsPath:String,weekdayServiceId:String) = {
    val trips = mutable.Map[String,Trip]()
    Logger.timed("Parsing trips file...","Finished parsing trips.") { () =>
      for(row <- Csv.fromPath(tripsPath)) {
        if(row("service_id") == weekdayServiceId) {
          val tripId = row("trip_id")
          trips(tripId) = new Trip(tripId)
        }
      }
    }
    trips.toMap
  }

  def parseStopTimes(stops:Stops, trips:Map[String,Trip], stopTimesPath:String,weekdayServiceId:String) = {
    var count = 0
    Logger.timed("Parsing stop times file...","Finished parsing stop times.") { () =>
      for(row <- Csv.fromPath(stopTimesPath)) {
        val tripId = row("trip_id")
        if(trips.contains(tripId)) {
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
    }
    Logger.log(s"$count stop times parsed for ${trips.size} trips.")
  }

  def parseTime(s:String):Time = {
    val gtfsTimeRegex(hour,minute,second) = s
    Time(second.toInt + (minute.toInt*60) + (hour.toInt*3600))
  }
}
