package geotrellis.transit.services.scenicroute

import javax.ws.rs._
import javax.ws.rs.core.Response
import com.wordnik.swagger.annotations._

import geotrellis.transit._
import geotrellis.transit.services._

import geotrellis._
import geotrellis.raster.op.ToVector
import geotrellis.rest._
import geotrellis.feature._
import geotrellis.feature.op._
import geotrellis.network._

import com.vividsolutions.jts.{geom => jts}

trait VectorResource extends ServiceUtil{
  @GET
  @Path("/json")
  @Produces(Array("application/json"))
  @ApiOperation(
    value = "Returns GeoJSON describing the limits of where you could stay for some minimum while travelling between two points.", 
    notes = """
Given a start location, an end location, transit modes, optionally a start time and schedule if
there are scheduled transit modes, a maximum trip duration, and a list of one or more minimum
stay times (the time at any given point that you would be able to stay while still making it
from the start location to the destination location within the time limit) returns
a polygon for each minimum stay time that contains points such that any point inside the polygon
can be reached and stayed at for the minimum stay time while still making it from the source to the 
destination within the maximum duration using the transit modes.
""")
  def getVector(
    @ApiParam(value = "Latitude of origin point", 
              required = true, 
              defaultValue = "39.957572")
    @DefaultValue("39.957572")
    @QueryParam("latitude") 
    latitude: Double,
        
    @ApiParam(value = "Longitude of destionation point", 
              required = true, 
              defaultValue = "-75.161782")
    @DefaultValue("-75.161782")
    @QueryParam("longitude") 
    longitude: Double,

    @ApiParam(value = "Latitude of destination point", 
              required = true, 
              defaultValue = "39.957572")
    @DefaultValue("39.987572")
    @QueryParam("destlatitude") 
    destlatitude: Double,

    @ApiParam(value = "Longitude of destination point", 
              required = true, 
              defaultValue = "-75.161782")
    @DefaultValue("-75.261782")
    @QueryParam("destlongitude") 
    destlongitude: Double,
    
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

    @ApiParam(value="Comma seperated list of minimum durations of time staying at a destination along the way, in seconds.", 
              required=false, 
              defaultValue="0")
    @DefaultValue("0")
    @QueryParam("minStayTimes") 
    minStayTimesString: String,

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
    
    @ApiParam(value="Number of columns for the traveshed raster to be vectorized.",
              required=true,
              defaultValue="500")
    @DefaultValue("500")
    @QueryParam("cols") 
    cols: Int,

    @ApiParam(value="Number of rows for the traveshed raster to be vectorized.",
              required=true,
              defaultValue="500")
    @DefaultValue("500")
    @QueryParam("rows")
    rows: Int,

    @ApiParam(value="Tolerance value for vector simplification.",
              required=false,
              defaultValue="0.0001")
    @DefaultValue("0.0001")
    @QueryParam("tolerance")
    tolerance:Double): Response = {

    val minStayTimes = minStayTimesString.split(",").map(_.toInt)

    val request = 
      try{
        SptInfoRequest.fromParams(
          latitude,
          longitude,
          time,
          duration,
          modes,
          schedule,
          "departing")
      } catch {
        case e:Exception =>
          return ERROR(e.getMessage)
      }

    val sptInfo = SptInfoCache.get(request)

    // Get arrival request
    val reverseSptInfoRequest = 
      SptInfoRequest(destlatitude,
                     destlongitude,
                     Time(request.time.toInt + request.duration.toInt),
                     request.duration,
                     request.modes,
                     !request.departing)
    val reverseSptInfo = SptInfoCache.get(reverseSptInfoRequest)

    val multiPolygonOps:Seq[Op[MultiPolygon[Int]]] =
      sptInfo.vertices match {
        case Some(ReachableVertices(subindex, extent)) =>
          reverseSptInfo.vertices match {
            case Some(ReachableVertices(revSubindex, revExtent)) =>
              val re = RasterExtent(expandByLDelta(extent), cols, rows)
                (for(minStayTime <- minStayTimes) yield {
                  val r =
                    ScenicRoute.getRaster(re,
                                          re,
                                          sptInfo,
                                          reverseSptInfo,
                                          ldelta,
                                          minStayTime,
                                          duration)
                  Literal(r)
                  // Set all relevant times to 1, everything else to NODATA
                    .into(logic.RasterMapIfSet(_)(z => if(z != NODATA) { 1 } else { NODATA }))
                  // Vectorize
                    .into(ToVector(_))
                  // Simplify
                    .map { vectors =>
                      vectors.map(geometry.Simplify(_, tolerance))
                     }
                  // Collect the operations
                    .into(logic.Collect(_))
                  // Map the individual Vectors into one MultiPolygon
                    .map { vectors =>
                      val geoms =
                        vectors.map(_.geom.asInstanceOf[jts.Polygon])

                      val multiPolygonGeom =
                        Feature.factory.createMultiPolygon(geoms.toArray)

                      MultiPolygon(multiPolygonGeom, minStayTime)
                     }
                }).toSeq
            case None => 
              Seq(Literal(MultiPolygon.empty(0)))
          }
        case None => 
          Seq(Literal(MultiPolygon.empty(0)))
      }

    val geoJsonOp = io.ToGeoJson(multiPolygonOps)

    GeoTrellis.run(geoJsonOp) match {
      case process.Complete(json, h) =>
        OK.json(json)
      case process.Error(message, failure) =>
        ERROR(message, failure)
    }
  }
}
