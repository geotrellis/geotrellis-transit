package geotrellis.transit.services

import geotrellis.transit._

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._

class SptInfoCache(val cacheActor:ActorRef) extends Actor {
  implicit val timeout = Timeout(5 seconds)

  def receive = {
    case request:TravelShedRequest => 
      val future = cacheActor ? CacheLookup(request)
      val sptInfo = Await.result(future, timeout.duration).asInstanceOf[Option[SptInfo]] match {
        case Some(sptInfo) => sptInfo
        case None =>
          val sptInfo = SptInfo(request)
          cacheActor ! CacheSet(request,sptInfo)
          sptInfo
      }
      sender ! sptInfo
  }
}

object SptInfoCache {
  implicit val timeout = Timeout(5 seconds)

  // Create cache actor
  private val system = geotrellis.process.Server.actorSystem
  private val innerCacheActor = 
    system.actorOf(Props(classOf[CacheActor[TravelShedRequest,SptInfo]],10000L,1000L))

  private val cacheActor = 
    system.actorOf(Props(classOf[SptInfoCache],innerCacheActor))

  def get(request:TravelShedRequest) =
    Await.result(cacheActor ? request, timeout.duration).asInstanceOf[SptInfo]
}
