package commonspace.services

import commonspace._
import commonspace.graph._
import commonspace.index.SpatialIndex
import javax.servlet.http.HttpServletRequest
import javax.ws.rs._
import javax.ws.rs.core.{ Response, Context, MediaType, MultivaluedMap }
import geotrellis._
import geotrellis.admin._
import geotrellis.admin.Json._
import geotrellis.raster.op._
import geotrellis.statistics.op._
import geotrellis.rest._
import geotrellis.rest.op._
import geotrellis.raster._
import geotrellis.feature._
import geotrellis.feature.op.geometry.AsPolygonSet
import geotrellis.feature.rasterize.{ Rasterizer, Callback }
import geotrellis.data.ColorRamps._
import geotrellis.data.ColorRamp
import commonspace.Logger
import scala.collection.JavaConversions._
import com.wordnik.swagger.annotations._
import com.wordnik.swagger.jaxrs._
import com.wordnik.swagger.sample.model.User
import com.wordnik.swagger.sample.data.UserData
import com.wordnik.swagger.sample.exception.NotFoundException
import javax.ws.rs._
import com.wordnik.swagger.core.util.RestResourceUtil
import scala.collection.JavaConverters._
import spire.syntax._
import geotrellis.data.ColorRamps

object TravelShed { var cachedPng: Array[Byte] = Array[Byte]() }

@Path("/travelshed")
class TravelShed {
  def reproject(wmX: Double, wmY: Double) = {
    val rp = Reproject(Point(wmX, wmY, 0), Projections.WebMercator, Projections.LatLong)
      .asInstanceOf[Point[Int]]
      .geom
    (rp.getX, rp.getY)
  }

  def stringToColor(s: String) = (Integer.parseInt(s, 16) << 8) | 0xff

  // val extent = Extent(-8377192.5507762, 4844390.708567381,-8353955.694180742,4869113.618507661)
  // val (cw,ch) = (75,75)
  // val cols = ((extent.xmax - extent.xmin)/cw).toInt
  // val rows = ((extent.ymax-extent.ymin)/ch).toInt
  // val rasterExtent = RasterExtent(extent,cw,ch,cols,rows)

  // val llExtent = {
  //   val (ymin,xmin) = reproject(extent.xmin,extent.ymin)
  //   val (ymax,xmax) = reproject(extent.xmax,extent.ymax)
  //   Extent(xmin,ymin,xmax,ymax)
  // }n

  // val llRasterExtent = {
  //   val cw = (llExtent.xmax - llExtent.xmin) / cols.toDouble
  //   val ch = (llExtent.ymax - llExtent.ymin) / rows.toDouble

  //   RasterExtent(llExtent,cw,ch,cols,rows)
  // }

  @GET
  @Path("/request")
  def getRequest(
    @DefaultValue("39.957572")@QueryParam("latitude") latitude: String,
    @DefaultValue("-75.161782")@QueryParam("longitude") longitude: String,
    @QueryParam("time") timeString: String,
    @QueryParam("duration") durationString: String,
    @DefaultValue("blue-to-red")@QueryParam("colorRamp") colorRampKey: String): Response = {
    println(s" LAT $latitude LONG $longitude TIME $timeString DURATION $durationString")
    val lat = latitude.toDouble
    val long = longitude.toDouble
    val time = Time(timeString.toInt)
    val duration = Duration(durationString.toInt)

    val startVertex = Main.context.index.nearest(lat, long)

    val spt =
      commonspace.Logger.timedCreate("Creating shortest path tree...",
        "Shortest Path Tree created.") { () =>
          ShortestPathTree(startVertex, time, Main.context.graph, duration)
        }

    var xmin = Double.MaxValue
    var ymin = Double.MaxValue
    var xmax = Double.MinValue
    var ymax = Double.MinValue
    val subindex =
      commonspace.Logger.timedCreate("Creating subindex of reachable vertices...",
        "Subindex created.") { () =>
          val reachable = spt.reachableVertices.toList
          SpatialIndex(reachable) { v =>
            val l = Main.context.graph.location(v)
            if (xmin > l.long) {
              xmin = l.long
            }
            if (xmax < l.long) {
              xmax = l.long
            }
            if (ymin > l.lat) {
              ymin = l.lat
            }
            if (ymax < l.lat) {
              ymax = l.lat
            }
            (l.lat, l.long)
          }
        }

    if (xmin == Double.MaxValue) {
      OK.json(s"""{ "extent": "", "url": "" } """)
    } else {
      val ldelta:Float = 0.0022f
      val ldelta2:Float = ldelta * ldelta
      val extent = Extent(xmin - ldelta, ymin - ldelta, xmax + ldelta, ymax + ldelta)
      val (cw, ch) = reproject(55, 55)
      val cols = ((extent.xmax - extent.xmin) / cw).toInt
      val rows = ((extent.ymax - extent.ymin) / ch).toInt
      println(s"$extent  $cols   $rows")
      val rasterExtent = RasterExtent(extent, cw, ch, cols, rows)

      commonspace.Logger.log(s"CREATING PNG FOR $time $duration ($lat,$long)")

      val r =
        commonspace.Logger.timedCreate(s"Creating travel time raster ($cols x $rows)...",
          "Travel time raster created.") { () =>
            val data = RasterData.emptyByType(TypeInt, cols, rows)

            cfor(0)(_ < cols, _ + 1) { col =>
              cfor(0)(_ < rows, _ + 1) { row =>
                val destLong = rasterExtent.gridColToMap(col)
                val destLat = rasterExtent.gridRowToMap(row)

                val e = Extent(destLong - ldelta, destLat - ldelta, destLong + ldelta, destLat + ldelta)
                val l = subindex.pointsInExtentAsJavaList(e)

                if (l.isEmpty) {
                  data.set(col, row, NODATA)
                } else {
                  var s = 0.0
                  var c = 0
                  var ws = 0.0
                  val length = l.length
                  cfor(0)(_ < length, _ + 1) { i =>
                    val target = l(i).asInstanceOf[Int]
                    val t = spt.travelTimeTo(target).toInt
                    val loc = Main.context.graph.location(target)
                    val dlat = (destLat - loc.lat)
                    val dlong = (destLong - loc.long)
                    val d = dlat * dlat + dlong * dlong
                    if (d < ldelta2) {
                      val w = 1 / d
                      s += t * w
                      ws += w
                      c += 1
                    }
                  }
                  val mean = s / ws
                  data.set(col, row, if (c == 0) NODATA else mean.toInt)
                }
              }
            }

            Raster(data, rasterExtent)
          }

      println(s"Min Max ${r.findMinMax}")
      // val (rmin,rmax) = r.findMinMax
      // val spacing = rmax / 30

      /* val palette1 = 
        List(0x4698D3FF, 0x39C6F0FF,
          0x76C9B3FF, 0xA8D050FF, 0xF6EB14FF, 0xFCB017FF,
          0xF16022FF, 0xEE2C24FF, 0xFFFFFFFF)
       */
      // val colorRamp = geotrellis.data.ColorRamp(palette1.toSeq)
      val colorRamp = ColorRamps.HeatmapBlueToYellowToRedSpectrum
      val palette = colorRamp.interpolate(13).colors
      val rOp =
        r.mapIfSet { z =>
          val minutes = z / 40
          minutes match {
            case a if a > duration.toInt => 0xFF000000
            case a if a < 3              => palette(0)
            case a if a < 5              => palette(1)
            case a if a < 8              => palette(3)
            case a if a < 10             => palette(4)
            case a if a < 15             => palette(5)
            case a if a < 20             => palette(6)
            case a if a < 25             => palette(7)
            case a if a < 30             => palette(8)
            case a if a < 40             => palette(9)
            case a if a < 50             => palette(10)
            case a if a < 60             => palette(11)
            case _                       => palette(12)
          }
        }

      // val png = 

      //      val cr = Colors.rampMap.getOrElse(colorRampKey,BlueToRed)
      // val cr = 
      //   ColorRamp.createWithRGBColors(
      //     List(0x4698D3, 0x39C6F0,
      //     0x76C9B3, 0xA8D050, 0xF6EB14, 0xFCB017,
      //     0xF16022, 0xEE2C24).reverse:_*)
      // val ramp = 
      //    if(cr.toArray.length < breaks.length) { cr.interpolate(breaks.length) }
      //    else { cr }

      //val rOp = geotrellis.raster.op.transform.ResampleRaster(r,cols*5,rows*5)
      //      val rOp = r

      val png1 = geotrellis.raster.op.transform.ResampleRaster(Force(rOp), cols * 2, rows * 2)
      val png = io.RenderPngRgba(png1)
      //val png = io.RenderPngRgba(rOp)

      //      val png = Render(rOp,ramp,Literal(breaks.toArray))

      // 20 seconds
      GeoTrellis.run(png) match {
        case process.Complete(img, h) =>
          TravelShed.cachedPng = img.asInstanceOf[Array[Byte]].clone
          println(s"  Cached png is ${TravelShed.cachedPng.length} bytes long.")
          val re = rasterExtent
          OK.json(s"""{ "extent": [["${re.extent.ymin}","${re.extent.xmin}"],["${re.extent.ymax}","${re.extent.xmax}"]], "url": "gt/travelshed/png?latitude=$lat,longitude=$long" } """)
        case process.Error(message, failure) =>
          ERROR(message, failure)
      }
    }
  }

  @GET
  @Path("/png")
  def getPng(
    @DefaultValue("39.957572")@QueryParam("latitude") latitude: String,
    @DefaultValue("-75.161782")@QueryParam("longitude") longitude: String): Response = {
    println("ANYTHING")
    println(s"  Cached png is ${TravelShed.cachedPng.length} bytes long.")
    OK.png(TravelShed.cachedPng)
  }
}
