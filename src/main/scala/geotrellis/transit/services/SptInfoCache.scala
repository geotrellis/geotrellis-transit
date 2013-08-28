package geotrellis.transit.services

import geotrellis.transit._

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._

object SptInfoCache {
  implicit val timeout = Timeout(500 seconds)

  // Create cache actor
  private val system = geotrellis.process.Server.actorSystem
  private val cacheActor = 
    system.actorOf(Props(
      classOf[CacheActor[TravelShedRequest,SptInfo]],
      10000L,
      1000L,
      { request:TravelShedRequest => SptInfo(request) }))

  def get(request:TravelShedRequest) =
    Await.result(cacheActor ? CacheLookup(request), timeout.duration).asInstanceOf[SptInfo]
}
