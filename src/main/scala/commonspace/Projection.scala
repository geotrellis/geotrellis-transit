package commonspace

import geotrellis.Extent

object Projection {
  val radiusOfEarth = 6371000 // Meters
  val globalMinLat = math.toRadians(-90.0)
  val globalMaxLat = math.toRadians(90.0)
  val globalMinLong = math.toRadians(-180.0)
  val globalMaxLong = math.toRadians(180.0)


  def degToRad(deg:Double) = { deg * (math.Pi / 180.0) }

  def latLongToMeters(lat1:Double,long1:Double,lat2:Double,long2:Double) = {
    val dLat = math.toRadians(lat2-lat1)
    val dLon = math.toRadians(long2-long1)
    val rlat1 = math.toRadians(lat1)
    val rlat2 = math.toRadians(lat2)

    val x = math.sin(dLat/2)
    val y = math.sin(dLon/2)
    val a = x * x + y * y * math.cos(rlat1) * math.cos(rlat2)
    radiusOfEarth * 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))
  }

  def getBoundingBox(lat:Double,long:Double,distance:Double):Extent = {
    val radDist = distance / radiusOfEarth;
    val radLat = math.toRadians(lat)
    val radLong = math.toRadians(long)

    var minLat = radLat - radDist
    var maxLat = radLat + radDist

    var minLong = 0.0
    var maxLong = 0.0

    if (minLat > globalMinLat && maxLat < globalMaxLat) {
      val deltaLon = math.asin(math.sin(radDist) / math.cos(radLat))
      minLong = radLong - deltaLon
      if (minLong < globalMinLong) minLong += 2d * math.Pi
      maxLong = radLong + deltaLon
      if (maxLong > globalMaxLong) maxLong -= 2d * math.Pi
    } else {
      // a pole is within the distance
      minLat = math.max(minLat, globalMinLat)
      maxLat = math.min(maxLat, globalMaxLat)
      minLong = globalMinLong
      maxLong = globalMaxLong
    }

    Extent(math.toDegrees(minLong),
           math.toDegrees(minLat),
           math.toDegrees(maxLong),
           math.toDegrees(maxLat))
  }
}
