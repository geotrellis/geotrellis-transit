package geotrellis.transit.services.scenicroute

import geotrellis._
import geotrellis.transit._

object ScenicRoute {
  def getRaster(re:RasterExtent, 
                llRe:RasterExtent,
                sptInfo:SptInfo,
                revSptInfo:SptInfo,
                ldelta:Double,
                minStayTime:Int,
                duration:Int):Raster = {
    val rOut = TravelTimeRaster(re, llRe, sptInfo,ldelta)
    val rIn = TravelTimeRaster(re,llRe, revSptInfo,ldelta)
    rOut.combine(rIn)({ (o,i) =>
      if(o != NODATA && i != NODATA) {
        val stayTime = duration - o - i
        if(stayTime >= minStayTime) {
          stayTime
        } else {
          NODATA
        }
      } else {
        NODATA
      }
    })
  }
}
