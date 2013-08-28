package geotrellis.transit.services

import javax.ws.rs._

import com.wordnik.swagger.annotations._

@Path("/travelshed")
@Api(value = "/travelshed", 
   description = "Travelshed generation.")
class TravelShedService extends VectorResource 
                           with WmsResource
                           with ReachableResource
                           with ExportResource
