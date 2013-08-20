package geotrellis.transit.services

import geotrellis.transit._

import geotrellis._
import geotrellis.network._
import geotrellis.rest.op._
import geotrellis.rest._
import geotrellis.data.arg.ArgWriter
import geotrellis.data.geotiff

import javax.ws.rs._
import javax.ws.rs.core

import java.io._
import com.google.common.io.Files

import java.io.{BufferedReader, FileOutputStream, File}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.io.Source

import com.wordnik.swagger.annotations._

trait ExportResource extends ServiceUtil {
  private def deleteRecursively(f:File): Boolean = {
    if (f.isDirectory) f.listFiles match { 
      case null =>
      case xs   => xs foreach deleteRecursively
    }
    f.delete()
  }

  def compressDirectory(directory:File):File = {
    val zipFile = new File(directory,"result.zip")
    val zip = new ZipOutputStream(new FileOutputStream(zipFile))

    for (file <- directory.listFiles) {
      if(zipFile.getName != file.getName) {
        zip.putNextEntry(new ZipEntry(file.getName))
        val in = new BufferedInputStream(new FileInputStream(file))
        var b = in.read()
        while (b > -1) {
          zip.write(b)
          b = in.read()
        }
        in.close()
        zip.closeEntry()
      }
    }
    zip.close()
    zipFile
  }

  @GET
  @Path("/export")
  @Produces(Array("application/octet-stream"))
  @ApiOperation(
    value = "Exports a travelshed raster." , 
    notes = """

This service exports a travel shed raster with the given request parameters as
a GeoTIFF or GeoTrellis ARG format.

""")
  def getExport(
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
        TravelShedRequest.fromParams(
          latitude,
          longitude,
          time,
          duration,
          mode,
          schedule,
          direction)
      } catch {
        case e:Exception =>
          return ERROR(e.getMessage)
      }

    val sptInfo = SptInfoCache.get(request)

    val extentOp = string.ParseExtent(bbox)
    val reOp = geotrellis.raster.op.extent.GetRasterExtent(extentOp, cols, rows)

    val (spt, subindex, extent) = sptInfo match {
      case SptInfo(spt, Some(ReachableVertices(subindex, extent))) => (spt, subindex, extent)
      case _ => throw new Exception("Invalid SptInfo in cache.")
    }

    val d = Files.createTempDir()

    try {
      val rOp =
        reOp.map { re =>
          val r =
            re.extent.intersect(expandByLDelta(extent)) match {
              case Some(ie) => TravelTimeRaster(re, re, sptInfo,ldelta)
              case None => Raster.empty(re)
            }

          val name = s"travelshed"

          if(format == "arg") {
            ArgWriter(TypeInt).write(new File(d,s"$name.arg").getAbsolutePath,r,name)
          } else {
            geotiff.Encoder.writePath(new File(d,s"$name.tif").getAbsolutePath,r,geotiff.Settings.int32)
          }

          compressDirectory(d)
        }

      GeoTrellis.run(rOp) match {
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
