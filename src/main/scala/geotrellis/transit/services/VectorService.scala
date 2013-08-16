package geotrellis.transit.services

import javax.ws.rs._
import javax.ws.rs.core.Response
import com.wordnik.swagger.annotations._

import geotrellis._
import geotrellis.raster.op.ToVector
import geotrellis.rest._
import geotrellis.feature._
import geotrellis.feature.op._

import com.vividsolutions.jts.{geom => jts}

@Produces(Array("application/json"))
@Path("/vector")
@Api(value = "/vector", 
   description = "Returns a set of MultiPolygons in GeoJson " +
                 "that defines the ability.")
class VectorService extends ServiceUtil{
  @GET
  def get(
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
    
    @ApiParam(value="Comma seperated list of durations, in seconds, to get polygons for.", 
              required=true, 
              defaultValue="1800")
    @DefaultValue("1800")
    @QueryParam("durations")
    durationsString: String,

    @ApiParam(value="Mode of transportation. One of: walk, bike, transit", 
              required=true, 
              defaultValue="transit")
    @DefaultValue("transit")
    @QueryParam("mode")  
    mode:String,

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

    @ApiParam(value="Number of columns for the traveshed raster to be vectorized.",
              required=true,
              defaultValue="500")
    @DefaultValue("500")
    @QueryParam("cols") 
    cols: Int,

    @ApiParam(value="Number of rows for the traveshed raster to be vectorized.",
              required=true,
              defaultValue="256")
    @DefaultValue("256")
    @QueryParam("rows") 
    rows: Int,

    @ApiParam(value="Tolerance value for vector simplification.",
              required=false,
              defaultValue="0.0001")
    @DefaultValue("0.0001")
    @QueryParam("tolerance") 
    tolerance:Double): Response = {

    val durations = durationsString.split(",").map(_.toInt)
    val maxDuration = durations.foldLeft(0)(math.max(_,_))

    val request = 
      try{
        TravelShedRequest.fromParams(
          latitude,
          longitude,
          time,
          maxDuration,
          mode,
          schedule,
          direction)
      } catch {
        case e:Exception =>
          return ERROR(e.getMessage)
      }

    val sptInfo = SptInfoCache.get(request)

    sptInfo.vertices match {
      case Some(ReachableVertices(subindex, extent)) =>
        val re = RasterExtent(expandByLDelta(extent), cols, rows)
        val r = TravelTimeRaster(re, re, sptInfo,ldelta)
        val geoJsonOps =
          (for(duration <- durations) yield {
            Literal(r)
            // Set all relevant times to 1, everything else to NODATA
              .into(logic.RasterMapIfSet(_)(z => if(z <= duration) { 1 } else { NODATA }))
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

                MultiPolygon(multiPolygonGeom, duration)
               }
          }).toSeq

        val geoJsonOp = io.ToGeoJson(geoJsonOps)

        GeoTrellis.run(geoJsonOp) match {
          case process.Complete(json, h) =>
            println(s"$json")
            OK.json(json)
          case process.Error(message, failure) =>
            ERROR(message, failure)
        }

      case None => ERROR("""There were no reachable vertices for the given point.""")
    }
  }
}
