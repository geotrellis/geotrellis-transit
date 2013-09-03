package geotrellis.transit.services

import geotrellis.transit._

import geotrellis._
import geotrellis.network._
import geotrellis.rest.op._
import geotrellis.rest._
import geotrellis.data.ColorRamps

import javax.ws.rs._
import javax.ws.rs.core.Response

import com.wordnik.swagger.annotations._

@Path("/reachable")
@Api(value = "/reachable", 
     description = "Queries reachability.")
class ReachableService extends ServiceUtil {
  val latLongRegex = """\[(-?\d+\.?\d*),(-?\d+\.?\d*)\]""".r

  case class LocationInfo(lat:Double,lng:Double,vertex:Int)
  case class ReachableInfo(location:LocationInfo,duration:Duration) {
    val reachable = duration != Duration.UNREACHABLE
    println(duration)
    def toJson() = {
      s"""{ "location": [${location.lat},${location.lng}], 
            "reachable": "$reachable", 
            "duration": ${duration.toInt} }"""
    }
  }

  @GET
  @Produces(Array("application/json"))
  @ApiOperation(
    value = "Information about the reachability of given locations." , 
    notes = """

Given a starting\ending location, and a list of destinations\source locations,
which ones are reachable and in how long?

""")
  def getReachable(
    @ApiParam(value = "Latitude of origin point", 
              required = true, 
              defaultValue = "39.957572")
    @DefaultValue("39.957572")
    @QueryParam("latitude") 
    latitude: Double,
    
    @ApiParam(value = "Longitude of origin point", 
              required = true, 
              defaultValue = "-75.161782")
    @DefaultValue("-75.161782")
    @QueryParam("longitude") 
    longitude: Double,
    
    @ApiParam(value = "Starting time of trip, in seconds from midnight", 
              required = true, 
              defaultValue = "0")
    @DefaultValue("0")
    @QueryParam("time") 
    time: Int,
    
    @ApiParam(value="Maximum duration of trip, in seconds", 
              required=true, 
              defaultValue="1800")
    @DefaultValue("1800")
    @QueryParam("duration") 
    duration: Int,

    @ApiParam(value="""
Modes of transportation. Must be one of the modes returned from /transitmodes, case insensitive.
""",
              required=true, 
              defaultValue="walking")
    @DefaultValue("walking")
    @QueryParam("modes")  
    modes:String,

    @ApiParam(value="Schedule for public transportation. One of: weekday, saturday, sunday", 
              required=false, 
              defaultValue="weekday")
    @DefaultValue("weekday")
    @QueryParam("schedule")
    schedule:String,
 
    @ApiParam(value="Direction of travel. One of: departing,arriving", 
              required=true, 
              defaultValue="departing")
    @DefaultValue("departing")
    @QueryParam("direction")
    direction:String,

    @ApiParam(value="""Destinations\Sources in lat long format.""", 
              required=true,
              defaultValue="[39.96,-75.17],[39.85,-75.15]")
    @DefaultValue("[39.96,-75.17],[39.85,-75.15]")
    @QueryParam("points")
    points:String

): Response = {
    // Parse points
    val errorMsg = "Invalid 'points' parameter format. e.g. [39.96,-75.17],[39.85,-75.15]"
    val locations = 
      try {
        (for(latLongRegex(latString,lngString) <- latLongRegex findAllIn points) yield {
          val lat = latString.toDouble
          val lng = lngString.toDouble
          val v = Main.context.index.nearest(lat, lng)
          LocationInfo(lat,lng,v)
        }).toList
      } catch {
        case _:Exception => return ERROR(errorMsg)
      }

    val request = 
      try {
        SptInfoRequest.fromParams(
          latitude,
          longitude,
          time,
          duration,
          modes,
          schedule,
          direction)
      } catch {
        case e:Exception => 
          return ERROR(e.getMessage)
      }

    val sptInfo = SptInfoCache.get(request)

    val results = 
      (for(l <- locations) yield {
        ReachableInfo(l,sptInfo.spt.travelTimeTo(l.vertex))
      }).toSeq
        .map(_.toJson)
        .reduce(_+","+_)


    OK.json(s"""
{
  "result" : [ $results ]
}""")
  }
}
