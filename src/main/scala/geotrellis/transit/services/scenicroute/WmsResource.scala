package geotrellis.transit.services.scenicroute

import geotrellis._
import geotrellis.rest.op._
import geotrellis.rest._
import geotrellis.data.ColorRamps
import geotrellis.raster.op._
import geotrellis.network._

import geotrellis.transit._
import geotrellis.transit.services._

import javax.ws.rs._
import javax.ws.rs.core.Response

import com.wordnik.swagger.annotations._

trait WmsResource extends ServiceUtil {
  @GET
  @Path("/wms")
  @Produces(Array("image/png"))
  @ApiOperation(
    value = "WMS service that gives a raster showing the time one can spend between travelling from one point to another." , 
    notes = """

""")
  def getWms(
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

    @ApiParam(value="Bounding box for the WMS tile request.",
              required=true)
    @QueryParam("bbox") 
    bbox: String,

    @ApiParam(value="Number of columns for the WMS tile request.",
              required=true,
              defaultValue="256")
    @DefaultValue("256")
    @QueryParam("cols") 
    cols: Int,

    @ApiParam(value="Number of rows for the WMS tile request.",
              required=true,
              defaultValue="256")
    @DefaultValue("256")
    @QueryParam("rows") 
    rows: Int,

    @ApiParam(value="Palette of colors to use to create the travelshed raster.",
              required=false,
              defaultValue="")
    @DefaultValue("")
    @QueryParam("palette") 
    palette: String,

    @ApiParam(value="Break values to use to color the travelshed raster.",
              required=false,
              defaultValue="")
    @DefaultValue("")
    @QueryParam("breaks")
    breaks: String,

    @ApiParam(value="Resolution factor for creating travelshed raster (adjust for performance).",
              required=false,
              defaultValue="3")
    @DefaultValue("3")
    @QueryParam("resolutionFactor")
    resolutionFactor: Int): Response = {

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

    // Reproject to latitude\longitude for querying spatial index.
    val llExtentOp = extentOp.map { ext =>
      val (ymin, xmin) = reproject(ext.xmin, ext.ymin)
      val (ymax, xmax) = reproject(ext.xmax, ext.ymax)
      Extent(xmin, ymin, xmax, ymax)
    }

    val reOp = geotrellis.raster.op.extent.GetRasterExtent(extentOp, cols, rows)
    val llReOp = geotrellis.raster.op.extent.GetRasterExtent(llExtentOp, cols, rows)

    val png = 
      sptInfo match {
        case SptInfo(spt, Some(ReachableVertices(subindex, extent))) =>
          reverseSptInfo match {
            case SptInfo(revSpt, Some(ReachableVertices(revSubindex, revExtent))) =>
              val rOp:Op[Raster] =
                for(re <- reOp;
                    llRe <- llReOp) yield {
                  val newRe =
                    re.withResolution(re.cellwidth * resolutionFactor,
                      re.cellheight * resolutionFactor)
                  val newllRe =
                    llRe.withResolution(llRe.cellwidth * resolutionFactor,
                      llRe.cellheight * resolutionFactor)

                  val cols = newRe.cols
                  val rows = newRe.rows

                  llRe.extent.intersect(expandByLDelta(extent)) match {
                    case Some(_) =>
                      llRe.extent.intersect(expandByLDelta(revExtent)) match {
                        case Some(_) =>
                          ScenicRoute.getRaster(newRe,
                                                newllRe,
                                                sptInfo,
                                                reverseSptInfo,
                                                ldelta,
                                                minStayTime,
                                                duration)
                        case None => Raster.empty(newRe)
                      }
                    case None => Raster.empty(newRe)
                  }
                }


              val colorMap = 
                try {
                  getColorMap(palette,breaks)
                } catch {
                  case e:Exception =>
                    return ERROR(e.getMessage)
                }

              val colorRasterOp =
                rOp.map(_.mapIfSet(colorMap))

              val resampled =
                geotrellis.raster.op.transform.Resize(colorRasterOp, cols, rows)

              io.RenderPngRgba(resampled)
            case _ =>
              io.SimpleRenderPng(reOp.map { re => Raster.empty(re) })
          }
        case _ =>
          io.SimpleRenderPng(reOp.map { re => Raster.empty(re) })
      }

    GeoTrellis.run(png) match {
      case process.Complete(img, h) =>
        OK.png(img)
      case process.Error(message, failure) =>
        ERROR(message, failure)
    }
  }
}

