package commonspace.services

import commonspace._
import commonspace.graph._
import commonspace.index.SpatialIndex

import javax.servlet.http.HttpServletRequest
import javax.ws.rs._
import javax.ws.rs.core.{Response, Context, MediaType, MultivaluedMap}
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
import geotrellis.feature.rasterize.{Rasterizer, Callback}
import geotrellis.data.ColorRamps._
import commonspace.Logger

import scala.collection.JavaConversions._

import spire.syntax._

object QueryParser {
  def parseTime(s:String) = {
    val ints = s.split(':').map(_.toInt * 60)
    if(ints.length != 2) {
      sys.error("Invalid time")
    }
    Time(ints(0)*60 + ints(1))
  }

  def parseDuration(s:String) = {
    val ints = s.split(':').map(_.toInt * 60)
    if(ints.length != 2) {
      sys.error("Invalid duration")
    }
    Duration(ints(0)*60 + ints(1))
  }
}

@Path("/travel")
class TripPlanner {
  def getClosestVertex(lat:Double,long:Double):Int = 
    Main.context.index.nearest(lat,long)

  @GET
  @Path("/route")
  def route(
    @DefaultValue("") @QueryParam("startLat") aStartLat:String,
    @DefaultValue("") @QueryParam("startLong") aStartLong:String,
    @DefaultValue("") @QueryParam("endLat") aEndLat:String,
    @DefaultValue("") @QueryParam("endLong") aEndLong:String,
    @DefaultValue("12:00") @QueryParam("startTime") startTime:String,
    @DefaultValue("800") @QueryParam("distance") distance:String,
    @Context req:HttpServletRequest
  ):Response = {
    try {
      val startLat = aStartLat.toDouble
      val startLong = aStartLong.toDouble
      val endLat = aEndLat.toDouble
      val endLong = aEndLong.toDouble
      val time = QueryParser.parseTime(startTime)

      val dist = distance.toInt //meters

      val startVertex =
        getClosestVertex(startLat,startLong)

      val targetVertex =
        getClosestVertex(endLat,endLong)

      val spt =
        commonspace.Logger.timedCreate("Creating shortest path tree...",
          "Shortest Path Tree created.") { () =>
          ShortestPathTree(startVertex,time,Main.context.graph)
        }

      val totalDuration =spt.travelTimeTo(targetVertex).toInt


      val path = spt.travelPathTo(targetVertex)
                    .map(Main.context.graph.location(_))
                    .map(Main.context.namedLocations(_))
                    .map(_.name)

      val pathStr = path.mkString(",")
      val data = s""" {
                        "duration" : "${totalDuration}",
                        "path" : "${pathStr}"
                      } """
      OK.json(data).allowCORS
    } catch {
      case e:Exception =>
        println("ERROR")
        e.printStackTrace
        ERROR(e.getMessage)
    }
  }

  def reproject(wmX:Double,wmY:Double) = {
    val rp = Reproject(Point(wmX,wmY,0), Projections.WebMercator, Projections.LatLong)
      .asInstanceOf[Point[Int]]
      .geom
    (rp.getX,rp.getY)
  }

  val extent = Extent(-8376428.180493358, 4847676.906022543,-8355331.560689615,4867017.75944691)
  val dim = 256
  val (cw,ch) = ((extent.xmax-extent.xmin)/dim, (extent.ymax-extent.ymin)/dim)
  val rasterExtent = RasterExtent(extent,cw,ch,dim,dim)

  val llExtent = {
    val (ymin,xmin) = reproject(extent.xmin,extent.ymin)
    val (ymax,xmax) = reproject(extent.xmax,extent.ymax)
    Extent(xmin,ymin,xmax,ymax)
  }

  val llRasterExtent = {
    val cw = (llExtent.xmax - llExtent.xmin) / dim.toDouble
    val ch = (llExtent.ymax - llExtent.ymin) / dim.toDouble

    RasterExtent(llExtent,cw,ch,dim,dim)
  }

  @GET
  @Path("/raster")
  def raster(
    @DefaultValue("") @QueryParam("lat") aLat:String,
    @DefaultValue("") @QueryParam("long") aLong:String,
    @DefaultValue("12:00") @QueryParam("startTime") startTime:String,
    @DefaultValue("00:30") @QueryParam("duration") durationString:String,
    @Context req:HttpServletRequest
  ):Response = {
    try {
      val lat = aLat.toDouble
      val long = aLong.toDouble
      val time = QueryParser.parseTime(startTime)
      val duration = QueryParser.parseDuration(durationString)

      commonspace.Logger.log(s"Creating travelshed raster for " +
                             s"point ($lat,$long) starting at $time " +
                             s"with a max duration of $duration")

      val startVertex = Main.context.index.nearest(lat,long)

      val spt =
        commonspace.Logger.timedCreate("Creating shortest path tree...",
          "Shortest Path Tree created.") { () =>
          ShortestPathTree(startVertex,time,Main.context.graph,duration)
        }

      val subindex =
        commonspace.Logger.timedCreate("Creating subindex of reachable vertices...",
          "Subindex created.") { () =>
          val reachable = spt.reachableVertices.toList
          commonspace.Logger.log(s" ---- NUMBER OF REACHABLE NODES: ${reachable.length}")
          SpatialIndex(reachable) { v =>
            val l = Main.context.graph.location(v)
            (l.lat,l.long)
          }
        }

      var gridToTotal = 0L
      var gridToCount = 0

      var destPathsTotal = 0L
      var destPathsCount = 0

      var sptTotal = 0L
      var sptCount = 0

      var missingCount = 0

      var t = 0L

      val r =
        commonspace.Logger.timedCreate(s"Creating travel time raster ($dim x $dim)...",
                                       "Travel time raster created.") { () =>
          val data = RasterData.emptyByType(TypeInt,dim,dim)

          cfor(0)(_<dim,_+1) { col =>
            cfor(0)(_<dim,_+1) { row =>
              val destLong = llRasterExtent.gridColToMap(col)
              val destLat = llRasterExtent.gridRowToMap(row)

              val e = Extent(destLong - 0.0018,destLat - 0.0018, destLong + 0.0018,destLat + 0.0018)
              val l = subindex.pointsInExtent(e).toList
              if(l.isEmpty) {
                data.set(col,row,NODATA)
              } else {
                var s = 0.0
                var c = 0
                var ws = 0.0
                cfor(0)(_ < l.length, _ + 1) { i =>
                  val target = l(i)
                  val t = spt.travelTimeTo(target).toInt
                  val loc = Main.context.graph.location(target)
                  val d = Projection.distance(destLat,destLong,loc.lat,loc.long)
                  val w = 1/d
                  s += t * w
                  ws += w
                  c += 1
                }
                val mean = s / (c * ws)
                data.set(col,row,mean.toInt)
              }
            }
          }

          Raster(data,rasterExtent)
        }

      val op = r//geotrellis.raster.op.focal.Max(r, geotrellis.raster.op.focal.Circle(20))

      if(missingCount > 0) {
        Logger.warn(s"There were $missingCount locations that were in index but not in graph.")
      }

      GeoTrellis.run(geotrellis.io.SimpleRenderPng(op,
                            geotrellis.data.ColorRamps.HeatmapBlueToYellowToRedSpectrum)) match {
        case process.Complete(img,h) =>
          OK.png(img)
        case process.Error(message,failure) =>
          ERROR(message,failure)
      }
    } catch {
      case e:Exception =>
        println("ERROR")
        e.printStackTrace
        ERROR(s"Server error: $e")
    }
  }

  @GET
  @Path("/wms")
  def render(
    @DefaultValue("") @QueryParam("bbox") bbox:String,
    @DefaultValue("256") @QueryParam("cols") cols:String,
    @DefaultValue("256") @QueryParam("rows") rows:String,
    @DefaultValue("") @QueryParam("lat") latString:String,
    @DefaultValue("") @QueryParam("lng") longString:String,
    @DefaultValue("43200") @QueryParam("time") startTime:String,
    @DefaultValue("600") @QueryParam("duration") durationString:String,
    @DefaultValue("") @QueryParam("palette") palette:String,
    @DefaultValue("4") @QueryParam("colors") numColors:String,
    @DefaultValue("image/png") @QueryParam("format") format:String,
    @DefaultValue("") @QueryParam("breaks") breaks:String,
    @DefaultValue("blue-to-red") @QueryParam("colorRamp") colorRampKey:String,
    @Context req:HttpServletRequest
  ):Response = {
    val lat = latString.toDouble
    val long = longString.toDouble
    val time = Time(startTime.toInt)
    val duration = Duration(durationString.toInt)

    val extentOp = string.ParseExtent(bbox)

    val llExtentOp = extentOp.map { ext =>
      val (ymin,xmin) = reproject(ext.xmin,ext.ymin)
      val (ymax,xmax) = reproject(ext.xmax,ext.ymax)
      Extent(xmin,ymin,xmax,ymax)
    }

    val colsOp = string.ParseInt(cols)
    val rowsOp = string.ParseInt(rows)

    val reOp = geotrellis.raster.op.extent.GetRasterExtent(extentOp, colsOp, rowsOp)
    val llReOp = geotrellis.raster.op.extent.GetRasterExtent(llExtentOp, colsOp, rowsOp)

    val rOp = 
      for(re <- reOp;
        llRe <- llReOp) yield {
        val startVertex = Main.context.index.nearest(lat,long)

        val spt =
          commonspace.Logger.timedCreate("Creating shortest path tree...",
            "Shortest Path Tree created.") { () =>
            ShortestPathTree(startVertex,time,Main.context.graph,duration)
          }

        val subindex =
          commonspace.Logger.timedCreate("Creating subindex of reachable vertices...",
            "Subindex created.") { () =>
            val reachable = spt.reachableVertices.toList
            SpatialIndex(reachable) { v =>
              val l = Main.context.graph.location(v)
              (l.lat,l.long)
            }
          }

        commonspace.Logger.log(s"CREATING TILE FOR $time $duration ($lat,$long)")
        commonspace.Logger.timedCreate(s"Creating travel time raster ($cols x $rows)...",
                                       "Travel time raster created.") { () =>
          val data = RasterData.emptyByType(TypeInt,dim,dim)

          cfor(0)(_<dim,_+1) { col =>
            cfor(0)(_<dim,_+1) { row =>
              val destLong = llRe.gridColToMap(col)
              val destLat = llRe.gridRowToMap(row)

              val e = Extent(destLong - 0.0018,destLat - 0.0018, destLong + 0.0018,destLat + 0.0018)
              val l = subindex.pointsInExtent(e).toList
              if(l.isEmpty) {
                data.set(col,row,NODATA)
              } else {
                var s = 0.0
                var c = 0
                var ws = 0.0
                cfor(0)(_ < l.length, _ + 1) { i =>
                  val target = l(i)
                  val t = spt.travelTimeTo(target).toInt
                  val loc = Main.context.graph.location(target)
                  val dlat = (destLat -loc.lat)
                  val dlong = (destLong - loc.long)
                  val d = dlat*dlat+dlong*dlong
                  val w = 1/d
                  s += t * w
                  ws += w
                  c += 1
//      DEBUG
                  // val et = Walking.walkDuration(Location(lat,long),loc).toInt
                  // if(et > t) {
                  //   val diff = et - t
                  //   if(topDiff < diff) { 
                  //     topDiff = diff
                  //     topVertex = target
                  //     topLocation = loc
                  //   }
                  // }
                }
                val mean = s / (c * ws)
                data.set(col,row,mean.toInt)
              }
            }
          }

          // if(topLocation != null) {
          //   val startLoc = Location(lat,long)
          //   commonspace.Logger.log(s"TOP WEIRD VERTEX PATH FROM $startVertex at $startLoc TO $topVertex at $topLocation WITH DIFFERENCE $topDiff")
          // }

          Raster(data,rasterExtent)
        }
      }

    val breaks = for(i <- 1 to 12) yield { i * 10 }

    val cr = Colors.rampMap.getOrElse(colorRampKey,BlueToRed)
    val ramp = if(cr.toArray.length < breaks.length) { cr.interpolate(breaks.length) }
    else { cr }

    val png = Render(rOp,ramp,Literal(breaks.toArray))

    GeoTrellis.run(png) match {
      case process.Complete(img,h) =>
        OK.png(img)
          .cache(1000)
      case process.Error(message,failure) =>
        ERROR(message,failure)
    }
  }
}
