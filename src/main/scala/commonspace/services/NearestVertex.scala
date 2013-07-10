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

import com.wordnik.swagger.annotations._
import com.wordnik.swagger.jaxrs._

import com.wordnik.swagger.sample.model.User
import com.wordnik.swagger.sample.data.UserData
import com.wordnik.swagger.sample.exception.NotFoundException

import javax.ws.rs.core.Response
import javax.ws.rs._
import com.wordnik.swagger.core.util.RestResourceUtil
import scala.collection.JavaConverters._

trait NearestVertex extends RestResourceUtil {
  @GET
  @Path("/{latitude}/{longitude}")
  @ApiOperation(value = "Get nearest OpenStreetMap node", notes = "Retrieve the closest OpenStreetMap node for use in later requests.")
  def createUser(
     @ApiParam(value = "Latitude of origin point", required = true) @DefaultValue("39.957572") @PathParam("latitude") latitude: Double,
     @ApiParam(value = "Longitude of origin point", required = true) @DefaultValue("-75.161782") @PathParam("longitude") longitude: Double
   ) = {
    Response.ok.entity( Main.context.index.nearest(latitude,longitude) ).build
  }
}

@Path("/nearest_vertex.json/")
@Api(value = "/nearest_vertex", description = "Operations about vertices")
@Produces(Array("application/json"))
class NearestVertexJSON extends NearestVertex
