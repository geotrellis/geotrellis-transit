package geotrellis.transit.services

import geotrellis.network._
import geotrellis.network.graph._

case class TravelShedRequest(lat: Double, 
                             lng:Double, 
                             time:Time, 
                             duration:Duration,
                             pathType:PathType,
                             departing:Boolean)
object TravelShedRequest {
  def fromParams(latitude:Double,
                 longitude:Double,
                 timeString:Int,
                 durationString:Int,
                 mode:String,
                 schedule:String,
                 direction:String):TravelShedRequest = {
      val lat = latitude.toDouble
      val long = longitude.toDouble
      val time = Time(timeString.toInt)
      val duration = Duration(durationString.toInt)
      val pathType:PathType =
        mode match {
          case "walk" => WalkPath
          case "bike" => BikePath
          case "transit" =>
            TransitPath(
              schedule match {
                case "weekday" => WeekDaySchedule
                case "saturday" => DaySchedule(Saturday)
                case "sunday" => DaySchedule(Sunday)
                case _ =>
                  throw new Exception("Unknown schedule. Choose from weekday, saturday, or sunday")
              }
            )
          case _ =>
            throw new Exception("Unknown mode. Choose from walk, bike, or transit")
        }

    val departing = direction != "arriving"
      
    TravelShedRequest(lat,long,time,duration,pathType,departing)
  }
}
