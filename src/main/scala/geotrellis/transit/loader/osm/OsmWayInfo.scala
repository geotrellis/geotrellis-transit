package geotrellis.transit.loader.osm

import geotrellis.network._

object OsmWayInfo {
  def fromTags(tags:Map[String,String]) = {
    if(tags.contains("highway")) {
      tags("highway") match {
        case _ => true
      }
    } else { false } ||
    if(tags.contains("public_transport")) { 
      tags("public_transport") == "platform"
    } else { false } ||
    if(tags.contains("railway")) {
      tags("railway") == "platform"
    } else { false }
  }

  // http://wiki.openstreetmap.org/wiki/Map_Features#Highway
  val highwayTypes = Map(
    "motorway" -> Impassable,
    "motorway_link" -> Impassable,
    "trunk" -> Impassable,
    "trunk_link" -> Impassable,
    "primary" -> Impassable,
    "primary_link" -> Impassable,
    "secondary" -> WalkOrBike,
    "secondary_link" -> WalkOrBike,
    "tertiary" -> WalkOrBike,
    "tertiary_link" -> WalkOrBike,
    "living_street" -> WalkOrBike,
    "pedestrian" -> WalkOrBike,
    "residential" -> WalkOrBike,
    "unclassified" -> WalkOrBike,
    "service" -> WalkOrBike,
    "track" -> 

  )
}

sealed abstract class OsmWayInfo {
  val isWalkable:Boolean
  val isBikable:Boolean

  def walkTime(l1:Location,l2:Location):Duration // in seconds
  def bikeTime(l1:Location,l2:Location):Duration // in seconds
}

case object Impassable extends OsmWayInfo { 
  val isWalkable = false
  val isBikable = false

  def walkTime(l1:Location,l2:Location):Duration = -1
  def bikeTime(l1:Location,l2:Location):Duration = -1
}

trait Walkable {
  val isWalkable = true

  def walkTime(l1:Location,l2:Location):Duration = Walking.walkDuration(v1.location,v2.location)
}

trait Bikable {
  val BIKE_SPEED = 6.0  // MOVE THIS 

  val isBikable = true
  def bikeTime(l1:Location,l2:Location):Duration = {
    val d = Distance.distance(start,end)
    Duration(math.ceil(d/OsmWayInfo.BIKE_SPEED).toDuration)
  }
}

case object WalkOrBike extends OsmWayInfo 
                          with Walkable
                          with Bikable

case object WalkOnly extends OsmWayInfo 
                        with Walkable {
  val isBikable = false
  def bikeTime(l1:Location,l2:Location):Duration = Duration.UNREACHABLE
}

case object BikeOnly extends OsmWayInfo 
                        with Bikable {
  val isWalkable = false
  def walkTime(l1:Location,l2:Location):Duration = Duration.UNREACHABLE
}
