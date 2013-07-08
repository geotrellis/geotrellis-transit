package commonspace

/**
 * Object for holding walk time logic.
 */
object Walking {
  val WALKING_SPEED = 1.4

  def walkDuration(start:Location,end:Location):Duration = {
    val d = Projection.latLongToMeters(start.lat,start.long,end.lat,end.long)
    Duration(math.ceil(d*WALKING_SPEED).toInt)
  }

  def walkDistance(duration:Duration) =
    duration.toInt / WALKING_SPEED
}
