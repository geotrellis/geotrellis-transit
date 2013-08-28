package geotrellis.transit.services

import com.wordnik.swagger.annotations.Api
import com.wordnik.swagger.jaxrs.listing.ApiListing

import javax.ws.rs.{Produces, Path}

@Path("/api-docs")
@Api("/api-docs")
@Produces(Array("application/json"))
class ApiListingResourceJSON extends ApiListing
