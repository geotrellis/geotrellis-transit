package geotrellis.transit.services.travelshed

import javax.ws.rs._
import javax.ws.rs.core.Response

import geotrellis.transit._
import geotrellis.transit.services._

import geotrellis._
import geotrellis.source._
import geotrellis.jetty._
import geotrellis.feature._
import geotrellis.feature.op._
import geotrellis.data.geojson._

import com.vividsolutions.jts.{geom => jts}
import com.vividsolutions.jts.simplify.TopologyPreservingSimplifier

trait VectorResource extends ServiceUtil{
  @GET
  @Path("/json")
  @Produces(Array("application/json"))
  def getVector(
    @DefaultValue("39.957572")
    @QueryParam("latitude") 
    latitude: Double,
    
    @DefaultValue("-75.161782")
    @QueryParam("longitude") 
    longitude: Double,
    
    @DefaultValue("0")
    @QueryParam("time") 
    time: Int,
    
    @DefaultValue("1800")
    @QueryParam("durations")
    durationsString: String,

    @DefaultValue("walking")
    @QueryParam("modes")  
    modes:String,

    @DefaultValue("weekday")
    @QueryParam("schedule")
    schedule:String,
 
    @DefaultValue("departing")
    @QueryParam("direction")
    direction:String,

    @DefaultValue("500")
    @QueryParam("cols") 
    cols: Int,

    @DefaultValue("500")
    @QueryParam("rows") 
    rows: Int,

    @DefaultValue("0.0001")
    @QueryParam("tolerance") 
    tolerance:Double): Response = {

    val durations = durationsString.split(",").map(_.toInt)
    val maxDuration = durations.foldLeft(0)(math.max(_,_))

    val request = 
      try{
        SptInfoRequest.fromParams(
          latitude,
          longitude,
          time,
          maxDuration,
          modes,
          schedule,
          direction)
      } catch {
        case e:Exception =>
          return ERROR(e.getMessage)
      }

    val sptInfo = SptInfoCache.get(request)

    val multiPolygons:ValueSource[Seq[MultiPolygon[Int]]] =
      sptInfo.vertices match {
        case Some(ReachableVertices(subindex, extent)) =>
          val re = RasterExtent(expandByLDelta(extent), cols, rows)
          val r = TravelTimeRaster(re, re, sptInfo,ldelta)
          (for(duration <- durations) yield {
            RasterSource(r)
              // Set all relevant times to 1, everything else to NODATA
              .localMapIfSet { z => if(z <= duration) { 1 } else { NODATA } }
              .toVector
              // Simplify
              .map { vectors =>
                vectors.map { g =>
                  g.mapGeom( TopologyPreservingSimplifier.simplify(_, tolerance))
                }
               }
              // Map the individual Vectors into one MultiPolygon
              .map { vectors =>
                val geoms =
                  vectors.map(_.geom.asInstanceOf[jts.Polygon])

                val multiPolygonGeom =
                  Feature.factory.createMultiPolygon(geoms.toArray)

                MultiPolygon(multiPolygonGeom, duration)
              }
           })
          .toSeq
          .collectSources
          .converge
        case None => ValueSource(Seq(MultiPolygon.empty(0)))
      }

    val geoJson = 
      multiPolygons.map { seq => GeoJsonWriter.createFeatureCollectionString(seq) }

    geoJson.run match {
      case process.Complete(json, h) =>
        OK.json(json)
      case process.Error(message, failure) =>
        ERROR(message, failure)
    }
  }
}
