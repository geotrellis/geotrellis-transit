package geotrellis.transit.services.travelshed

import geotrellis._
import geotrellis.rest.op._
import geotrellis.rest._
import geotrellis.data.ColorRamps

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
    value = "WMS service exposing the travelshed raster for placement on a webmap." ,
    notes = """

This is a WMS endpoint for a transitshed raster layer that can be placed on a web map. 

""")
  def getWms(
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
    resolutionFactor: Int,

    @DefaultValue("false")
    @QueryParam("datapng") 
    dataPng: Boolean): Response = {

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
          val rOp =
            for (
              re <- reOp;
              llRe <- llReOp
            ) yield {
              val newRe =
                re.withResolution(re.cellwidth * resolutionFactor, re.cellheight * resolutionFactor)
              val newllRe =
                llRe.withResolution(llRe.cellwidth * resolutionFactor, llRe.cellheight * resolutionFactor)

              val cols = newRe.cols
              val rows = newRe.rows

              llRe.extent.intersect(expandByLDelta(extent)) match {
                case Some(ie) => TravelTimeRaster(newRe, newllRe, sptInfo,ldelta)
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

          val dataRasterOp = rOp.map(r => r.map { z =>
            if (z == NODATA) 0 else {
              // encode seconds in RGBA color values: 0xRRGGBBAA.

              // If you disregard the alpha channel,
              // you can think of this as encoding the value in base 255:
              // B = x * 1
              // G = x * 255
              // R = x * 255 * 255
              val b = (z % 255) << 8
              val g = (z / 255).toInt << 16
              val r = (z / (255 * 255)).toInt << 24

              // Alpha channel is always set to 255 to avoid the values getting garbled
              // by browser optimizations.
              r | g | b | 0xff
            }
          })

          val outputOp = if (dataPng) dataRasterOp else colorRasterOp

          val resampled =
            geotrellis.raster.op.transform.Resize(outputOp, cols, rows)

          io.RenderPngRgba(resampled)
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

