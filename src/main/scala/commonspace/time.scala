package commonspace

class Time(private val secondsFromMidnight:Int) extends Serializable {
  def toInt = secondsFromMidnight

  def >(other:Time) = {
    this.toInt > other.toInt
  }

  def <(other:Time) = {
    this.toInt < other.toInt
  }

  def -(other:Time) = {
    new Duration(this.toInt - other.toInt)
  }

  override
  def toString() = {
    s"Time($secondsFromMidnight)"
  }

  override 
  def hashCode = secondsFromMidnight.hashCode

  override 
  def equals(other: Any) = 
    other match { 
      case that: Time => this.secondsFromMidnight == that.secondsFromMidnight
      case _ => false 
    }
}

object Time {
  val ANY = new Time(-2)

  def apply(secondsFromMidnight:Int) = new Time(secondsFromMidnight)
}

class Duration(private val seconds:Int) extends Serializable {
  def toInt = seconds

  override
  def toString() = {
    if(seconds < 0) { s"Duration(UNREACHABLE)" }
    else { s"Duration($seconds seconds)" }
  }

  override 
  def hashCode = seconds.hashCode

  override 
  def equals(other: Any) = 
    other match { 
      case that: Duration => this.seconds == that.seconds
      case _ => false 
    }

  def isReachable():Boolean = 
    seconds >= 0
}

object Duration {
  val UNREACHABLE = new Duration(-1)

  def apply(seconds:Int) = 
    if(seconds < 0) {
      Duration.UNREACHABLE
    } else {
      new Duration(seconds)
    }
}
