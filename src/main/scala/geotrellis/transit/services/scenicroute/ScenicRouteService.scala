package geotrellis.transit.services.scenicroute

import javax.ws.rs._

import com.wordnik.swagger.annotations._

@Path("/scenicroute")
@Api(value = "/scenicroute", 
   description = "Time you can spend along a path based on travel time information between two points.")
class ScenicRouteService extends WmsResource
                            with ExportResource
                            with VectorResource
