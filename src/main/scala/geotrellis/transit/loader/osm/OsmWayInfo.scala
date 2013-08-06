package geotrellis.transit.loader.osm

import geotrellis.network._

trait WayInfo {
  def isWalkable:Boolean
  def walkSpeed:Double

  val isBikable:Boolean
  val bikeSpeed:Double

  private var _direction:WayDirection = BothWays
  def direction = _direction
}

abstract sealed class WayDirection
case object OneWay extends WayDirection
case object BothWays extends WayDirection
case object OneWayReverse extends WayDirection

case object Impassable extends WayInfo {
  val isWalkable = false
  val isBikable = false

  val walkSpeed = 0.0
  val bikeSpeed = 0.0
}

trait Walkable {
  val isWalkable = true

  val walkSpeed = Walking.WALKING_SPEED
}

trait Bikable {
  val isBikable = true
  val bikeSpeed = 6.0  // MOVE THIS 
}

case class WalkOrBike() extends WayInfo 
                          with Walkable
                          with Bikable

case class WalkOnly() extends WayInfo 
                        with Walkable {
  val isBikable = false
  val bikeSpeed = 0.0
}

case class BikeOnly() extends WayInfo 
                        with Bikable {
  val isWalkable = false
  val walkSpeed = 0.0
}

object WayInfo {
  // http://wiki.openstreetmap.org/wiki/Key:oneway
  private val oneWayTrueValues = Set("yes","true","1")
  private val oneWayReverseValues = Set("-1","reverse")

  def fromTags(tags:Map[String,String]):WayInfo = {
    var info:WayInfo = null

    if(tags.contains("highway")) {
      info = highwayTypes.getOrElse(tags("highway"), Impassable)
    }

    if(info == null) {
      if(tags.contains("public_transport")) {
        if(tags("public_transport") == "platform") {
          info = WalkOnly()
        }
      }
    }

    if(info == null) {
      if(tags.contains("railway")) {
        if(tags("railway") == "platform") {
          info = WalkOnly()
        }
      }
    }

    info match {
      case null => Impassable
      case Impassable => Impassable
      case _ =>
        // Check for one-way
        if(tags.contains("oneway")) {
          val oneway = tags("oneway")
          info._direction = 
            if(oneWayTrueValues.contains(oneway)) {
              OneWay
            } else if (oneWayReverseValues.contains(oneway)) {
              OneWayReverse
            } else {
              BothWays
            }
        }
        info
    }
  }

  // http://wiki.openstreetmap.org/wiki/Map_Features#Highway
  val highwayTypes = Map(
    ( "motorway" , Impassable ),
    ( "motorway_link" , Impassable ),
    ( "trunk" , Impassable ),
    ( "trunk_link" , Impassable ),
    ( "primary" , WalkOrBike() ),
    ( "primary_link" , WalkOrBike() ),
    ( "secondary" , WalkOrBike() ),
    ( "secondary_link" , WalkOrBike() ),
    ( "tertiary" , WalkOrBike() ),
    ( "tertiary_link" , WalkOrBike() ),
    ( "living_street" , WalkOrBike() ),
    ( "pedestrian" , WalkOrBike() ),
    ( "residential" , WalkOrBike() ),
    ( "unclassified" , WalkOrBike() ),
    ( "service" , WalkOrBike() ),
    ( "track" , WalkOrBike() ),
    ( "bus_guideway" , Impassable ),
    ( "raceway" , Impassable ),
    ( "road" , WalkOrBike() ),
    ( "path" , WalkOrBike() ),
    ( "footway" , WalkOrBike() ),
    ( "cycleway" , WalkOrBike() ),
    ( "bridleway" , WalkOrBike() ),
    ( "steps" , WalkOnly() ),
    ( "proposed" , Impassable ),
    ( "construction" , Impassable ),
    ( "bus_stop" , WalkOnly() ),
    ( "crossing" , WalkOrBike() ),
    ( "emergency_access_point" , Impassable ),
    ( "escape" , Impassable ),
    ( "give_way" , Impassable ),
    ( "mini_roundabout" , WalkOrBike() ),
    ( "motorway_junction" , Impassable ),
    ( "parking" , WalkOnly() )
  )
}
