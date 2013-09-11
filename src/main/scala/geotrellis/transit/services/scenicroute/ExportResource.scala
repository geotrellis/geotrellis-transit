package geotrellis.transit.services.scenicroute

import geotrellis.transit._
import geotrellis.transit.services._

import geotrellis._
import geotrellis.network._
import geotrellis.rest.op._
import geotrellis.rest._
import geotrellis.data.arg.ArgWriter
import geotrellis.data.geotiff

import javax.ws.rs._
import javax.ws.rs.core

import java.io.{File,FileInputStream}
import com.google.common.io.Files

import com.wordnik.swagger.annotations._

trait ExportResource extends ServiceUtil {
  @GET
  @Path("/export")
  @Produces(Array("application/octet-stream"))
  @ApiOperation(
    value = "Exports a travelshed raster." , 
    notes = """

This service exports a scenic route raster with the given request parameters as
a GeoTIFF or GeoTrellis ARG format.

""")
  def getExport(
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

    @ApiParam(value="Minimum duration of time staying at a destination along the way, in seconds", 
              required=false, 
              defaultValue="0")
    @DefaultValue("0")
    @QueryParam("minStayTime") 
    minStayTime: Int,

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

    @ApiParam(value="Bounding box for request in latitude and longitude coordinates. In longmin,latmin,longmax,latmax order.",
              required=true)
    @QueryParam("bbox") 
    bbox: String,

    @ApiParam(value="Number of columns for the output raster.",
              required=true,
              defaultValue="256")
    @DefaultValue("256")
    @QueryParam("cols") 
    cols: Int,

    @ApiParam(value="Number of rows for the output raster.",
              required=true,
              defaultValue="256")
    @DefaultValue("256")
    @QueryParam("rows") 
    rows: Int,

    @ApiParam(value="Format of the exported raster. One of arg, tiff.",
              required=true,
              defaultValue="tiff")
    @DefaultValue("tiff")
    @QueryParam("format")
    format:String): core.Response = {

    val request =
      try {
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

    val extentOp = string.ParseExtent(bbox)
    val reOp = geotrellis.raster.op.extent.GetRasterExtent(extentOp, cols, rows)

    val (spt, subindex, extent) = sptInfo match {
      case SptInfo(spt, Some(ReachableVertices(subindex, extent))) => (spt, subindex, extent)
      case _ => return ERROR("Invalid SptInfo in cache.")
    }

    val (revSpt, revSubindex, revExtent) = reverseSptInfo match {
      case SptInfo(revSpt, Some(ReachableVertices(revSubindex, revExtent))) => (revSpt, revSubindex, revExtent)
      case _ => return ERROR("Invalid SptInfo in cache.")
    }

    val d = Files.createTempDir()

    try {
      val rasterWriteOp =
        reOp.map { re =>
          val r =
            re.extent.intersect(expandByLDelta(extent)) match {
              case Some(_) =>
                re.extent.intersect(expandByLDelta(revExtent)) match {
                  case Some(_) =>
                    ScenicRoute.getRaster(re,
                                          re,
                                          sptInfo,
                                          reverseSptInfo,
                                          ldelta,
                                          minStayTime,
                                          duration)
                  case None => Raster.empty(re)
                }
              case None => Raster.empty(re)
            }

          val name = s"scenicroute"

          if(format == "arg") {
            ArgWriter(TypeInt).write(new File(d,s"$name.arg").getAbsolutePath,r,name)
          } else {
            geotiff.Encoder.writePath(new File(d,s"$name.tif").getAbsolutePath,r,geotiff.Settings.int32)
          }

          compressDirectory(d)
        }

      GeoTrellis.run(rasterWriteOp) match {
        case process.Complete(zipFile, h) =>
          val in = new FileInputStream(zipFile)
          try {
            val bytes = new Array[Byte](zipFile.length.toInt)
            in.read(bytes)
            in.close()
            Response.ok("application/octet-stream").data(bytes)
          } finally {
            in.close()
          }
        case process.Error(message, failure) =>
          ERROR(message, failure)
      }
    } finally {
      deleteRecursively(d)
    }
  }
}
