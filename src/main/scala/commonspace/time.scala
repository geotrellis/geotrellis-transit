package commonspace

class Time(private val secondsFromMidnight:Int) {
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
  val UNKNOWN = new Time(-1)
  val ANY = new Time(-2)

  def apply(secondsFromMidnight:Int) = new Time(secondsFromMidnight)
}

class Duration(private val seconds:Int) {
  def toInt = seconds

  override
  def toString() = {
    s"Duration($seconds)"
  }

  override 
  def hashCode = seconds.hashCode

  override 
  def equals(other: Any) = 
    other match { 
      case that: Duration => this.seconds == that.seconds
      case _ => false 
    }
}

object Duration {
  def apply(seconds:Int) = new Duration(seconds)
}
