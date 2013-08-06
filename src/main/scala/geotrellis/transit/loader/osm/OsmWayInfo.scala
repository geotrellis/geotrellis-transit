package geotrellis.transit.loader.osm

import geotrellis.network._

object OsmWayInfo {
  def fromTags(tags:Map[String,String]):OsmWayInfo = {
    if(tags.contains("highway")) {
      return highwayTypes.getOrElse(tags("highway"), Impassable)
    }

    if(tags.contains("public_transport")) {
      if(tags("public_transport") == "platform") {
        return WalkOnly
      }
    }

    if(tags.contains("railway")) {
      if(tags("railway") == "platform") {
        return WalkOnly
      }
    }

    return Impassable
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
    "track" -> WalkOrBike,
    "bus_guideway" -> Impassable,
    "raceway" -> Impassable,
    "road" -> WalkOrBike,
    "path" -> WalkOrBike,
    "footway" -> WalkOrBike,
    "cycleway" -> WalkOrBike,
    "bridleway" -> WalkOrBike,
    "steps" -> WalkOnly,
    "proposed" -> Impassable,
    "construction" -> Impassable,
    "bus_stop" -> WalkOnly,
    "crossing" -> WalkOrBike,
    "emergency_access_point" -> Impassable,
    "escape" -> Impassable,
    "give_way" -> Impassable,
    "mini_roundabout" -> WalkOrBike,
    "motorway_junction" -> Impassable
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

  def walkTime(l1:Location,l2:Location):Duration = Duration.UNREACHABLE
  def bikeTime(l1:Location,l2:Location):Duration = Duration.UNREACHABLE
}

trait Walkable {
  val isWalkable = true

  def walkTime(l1:Location,l2:Location):Duration = Walking.walkDuration(l1,l2)
}

trait Bikable {
  val BIKE_SPEED = 6.0  // MOVE THIS 

  val isBikable = true
  def bikeTime(l1:Location,l2:Location):Duration = {
    val d = Distance.distance(l1,l2)
    Duration(math.ceil(d/BIKE_SPEED).toInt)
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
