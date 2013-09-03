package geotrellis.transit.services.between

import javax.ws.rs._

import com.wordnik.swagger.annotations._

@Path("/between")
@Api(value = "/between", 
   description = "Travel Time information between two points.")
class TravelShedService extends WmsResource
