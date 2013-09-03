package geotrellis.transit.services

import geotrellis.transit.Main

import geotrellis.network._
import geotrellis.network.graph._

case class TravelShedRequest(lat: Double, 
                             lng:Double, 
                             time:Time, 
                             duration:Duration,
                             modes:Seq[TransitMode],
                             departing:Boolean)
object TravelShedRequest {
  val availableModes = Main.context.graph.transitEdgeModes
                                         .map(_.service)
  val modesStr = availableModes.map(_.toLowerCase).mkString(", ")

  def fromParams(latitude:Double,
                 longitude:Double,
                 timeString:Int,
                 durationString:Int,
                 modesString:String,
                 schedule:String,
                 direction:String):TravelShedRequest = {
      val lat = latitude.toDouble
      val long = longitude.toDouble
      val time = Time(timeString.toInt)
      val duration = Duration(durationString.toInt)

      val modes = 
        (for(mode <- modesString.split(",")) yield {
          mode.toLowerCase match {
            case "walking" => Walking
            case "biking" => Biking
            case s =>
              availableModes.find(_.toLowerCase == s) match {
                case Some(service) =>
                  ScheduledTransit(
                    service,
                    schedule match {
                      case "weekday" => WeekDaySchedule
                      case "saturday" => DaySchedule(Saturday)
                      case "sunday" => DaySchedule(Sunday)
                      case _ =>
                        throw new Exception("Unknown schedule. Choose from $modesStr")
                    })
                case None =>
                    throw new Exception("Unknown mode. Choose from $modesStr")
              }
          }
        })

    val departing = direction != "arriving"
      
    TravelShedRequest(lat,long,time,duration,modes,departing)
  }
}
