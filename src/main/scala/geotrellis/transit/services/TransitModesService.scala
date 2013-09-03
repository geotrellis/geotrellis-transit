package geotrellis.transit.services

import geotrellis.transit._
import geotrellis.rest._

import javax.ws.rs._
import javax.ws.rs.core.Response

import com.wordnik.swagger.annotations._

@Path("/transitmodes")
@Api(value = "/transitmodes", 
   description = "Query transit modes.")
class TransitModesService extends ServiceUtil {
  @GET
  @Produces(Array("application/json"))
  @ApiOperation(
    value = "Returns the transit modes available for queries against this API." , 
    notes = """

Hit this endpoint before queries to other services, and use the resulting names
anywhere there is a 'modes' query parameter.

""")
  def get():Response = {
    val sb = new StringBuilder()
    sb append """
       { "name" : "Walking", "scheduled" : "false" },
       { "name" : "Biking",  "scheduled" : "false" },
"""

    for(mode:String <- Main.context.graph.transitEdgeModes.map(_.service).toSet) {
      sb append s"""
         { "name" : "$mode", "scheduled" : "true" },
"""
    }

    val modesJsonAll = sb.toString
    val modesJson = modesJsonAll.substring(0,modesJsonAll.lastIndexOf(","))

    OK.json(stripJson(s"""
   {
     "modes": [
       $modesJson
      ]
   }
"""))
  }
}
