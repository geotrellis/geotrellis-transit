package geotrellis.transit

import akka.actor.{Actor,Props,Cancellable}
import scala.concurrent.duration._

import scala.collection.mutable

case object CleanRequest

case class CacheLookup[TK](key:TK)
case class CacheSet[TK,TV](key:TK,value:TV)

class CacheActor[TK,TV](expireTime:Long,cleanInterval:Long = 1000L) extends Actor {
  val cache = mutable.Map[TK,TV]()

  val lastAccess = mutable.Map[TK,Long]()

  var cleanTick:Cancellable = null

  import context.dispatcher

  override
  def preStart() = { 
    cleanTick = context.system.scheduler.schedule(
                                           0 milliseconds,
                                           cleanInterval milliseconds,
                                           self,
                                           CleanRequest)
  }

  override
  def postStop() = if(cleanTick != null) { cleanTick.cancel }

  def receive = {
    case CleanRequest => 
      val rt = Runtime.getRuntime
      val kb =(rt.totalMemory - rt.freeMemory) * 0.000976562
      println(s"Cleaning cache... (Total Memory Usage $kb KB)")
      cleanCache()
    case CacheLookup(key) => sender ! cacheLookup(key.asInstanceOf[TK])
    case CacheSet(key,value) => set(key.asInstanceOf[TK],value.asInstanceOf[TV])
  }

  def cleanCache() = 
    for(key <- lastAccess.keys.toList) {
      if(System.currentTimeMillis - lastAccess(key) > expireTime) {
        println(s"  REMOVING $key")
        cache.remove(key)
        lastAccess.remove(key)
      }
    }

  def cacheLookup(key:TK) =
    if(cache.contains(key)) {
      lastAccess(key) = System.currentTimeMillis
      Some(cache(key))
    }
    else { None }

  def set(key:TK,value:TV) = {
    cache(key) = value
    lastAccess(key) = System.currentTimeMillis
  }
}
