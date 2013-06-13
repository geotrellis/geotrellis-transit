package commonspace.services

import commonspace._
import commonspace.io._
import commonspace.graph._

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
}

@Path("/travel")
class TripPlanner {
  def getStopPathEdges(lat:Double,long:Double,dist:Double):Seq[PathEdge] = {
    val boundingBox = Projection.getBoundingBox(lat,long,dist)
    (for(l <- Main.context.index.pointsInExtent(boundingBox)) yield {
      val v = Main.context.graph.locations.getVertexAt(l.lat, l.long)
      val d = Projection.latLongToMeters(lat,long,l.lat,l.long)
      val t = math.ceil(d*Main.context.WALKING_SPEED).toInt
      PathEdge(v,t)
    }).filter(_.vertex != -1)
  }

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

      val initialPaths =
        getStopPathEdges(startLat,startLong,dist)

      val destPaths =
        getStopPathEdges(endLat,endLong,dist)

      for(path <- initialPaths) {
        println(s"INITIAL PATH: $path")
      }

      val spt =
        commonspace.Logger.timedCreate("Creating shortest path tree...",
          "Shortest Path Tree created.") { () =>
          ShortestPathTree(initialPaths,time,Main.context.graph)
        }

      val f = destPaths(0)

      val (endStop,totalDuration) =
        destPaths.drop(1).foldLeft((f.vertex,f.duration + spt.travelTimeTo(f .vertex).toInt)) { (t,path) =>
          t match { case(v,d) =>
            val newDur = spt.travelTimeTo(path.vertex).toInt + path.duration
            if(d > newDur) {
              (path.vertex,newDur)
            } else {
              t
            }
          }
        }

      val path = spt.travelPathTo(endStop)
                    .map(Main.context.graph.locations.getLocation(_))
                    .map(Main.context.stops.locationToStop(_))
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

  val extent = Extent(-8376428.180493358, 4847676.906022543,-8355331.560689615,4867017.75944691)
  val dim = 256//*6
  val (cw,ch) = ((extent.xmax-extent.xmin)/dim, (extent.ymax-extent.ymin)/dim)
  val rasterExtent = RasterExtent(extent,cw,ch,dim,dim)

  def reproject(wmX:Double,wmY:Double) = {
    val rp = Reproject(Point(wmX,wmY,0), Projections.WebMercator, Projections.LatLong)
      .asInstanceOf[Point[Int]]
      .geom
    (rp.getX,rp.getY)
  }

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
    @DefaultValue("800") @QueryParam("distance") distance:String,
    @Context req:HttpServletRequest
  ):Response = {
    try {
      val lat = aLat.toDouble
      val long = aLong.toDouble
      val time = QueryParser.parseTime(startTime)

      val dist = distance.toInt //meters


      val initialPaths =
        commonspace.Logger.timedCreate(s"Creating initial paths at distance $dist",
                                        "Initial paths created.") { () =>
          getStopPathEdges(lat,long,dist)
        }

      commonspace.Logger.log(s"Initial paths: ${initialPaths.length}")

      val spt =
        commonspace.Logger.timedCreate("Creating shortest path tree...",
          "Shortest Path Tree created.") { () =>
          ShortestPathTree(initialPaths,time,Main.context.graph)
        }

      val s = scala.collection.mutable.Set[Int]()
      var empties = 0

      var gridToTotal = 0L
      var gridToCount = 0

      var destPathsTotal = 0L
      var destPathsCount = 0

      var sptTotal = 0L
      var sptCount = 0

      var t = 0L

      val r =
        commonspace.Logger.timedCreate(s"Creating travel time raster ($dim x $dim)...",
                                       "Travel time caster created.") { () =>
          val data = RasterData.emptyByType(TypeInt,dim,dim)

          cfor(0)(_<dim,_+1) { col =>
            cfor(0)(_<dim,_+1) { row =>
              t = System.currentTimeMillis
              val destLong = llRasterExtent.gridColToMap(col)
              val destLat = llRasterExtent.gridRowToMap(row)
              gridToTotal += System.currentTimeMillis - t
              gridToCount += 1

              t = System.currentTimeMillis
              val destPaths =
                getStopPathEdges(destLat,destLong,dist)
              destPathsTotal += System.currentTimeMillis - t
              destPathsCount += 1

              if(!destPaths.isEmpty) {
                t = System.currentTimeMillis
                val v =
                  destPaths.map(path => spt.travelTimeTo(path.vertex).toInt).max
                data.set(col,row,v)
                s += 1
                sptTotal += System.currentTimeMillis - t
                sptCount += 1
              } else { empties += 1 }
            }
          }

          Raster(data,rasterExtent)
        }

      Logger.log(s"  - Average row grid to map time: ${gridToTotal/gridToCount.toDouble}")
      Logger.log(s"  - Average row destination paths lookup time: ${destPathsTotal/destPathsCount.toDouble}")
      Logger.log(s"  - Average row SPT dest lookup time: ${sptTotal/sptCount.toDouble}")

      GeoTrellis.run(geotrellis.io.SimpleRenderPng(r)) match {
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
}
