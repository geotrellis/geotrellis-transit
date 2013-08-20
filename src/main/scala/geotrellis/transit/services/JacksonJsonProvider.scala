package geotrellis.transit.services

import javax.ws.rs.Produces

import javax.ws.rs.core.MediaType
import javax.ws.rs.ext.Provider

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider

import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.annotation._

@Provider
@Produces(Array(MediaType.APPLICATION_JSON))
class JacksonJsonProvider extends JacksonJaxbJsonProvider {
  val mapper = new ObjectMapper()
  mapper.registerModule(new DefaultScalaModule())
  mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL)
  mapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT)
  mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  super.setMapper(mapper)

  /** Need to do this so we can return raw JSON strings from the API */
  override
  def isWriteable(typ:Class[_], 
                  genericType:java.lang.reflect.Type, 
                  annotations:Array[java.lang.annotation.Annotation],
                  mediaType:MediaType) =
    !"".getClass.equals(typ)
}
