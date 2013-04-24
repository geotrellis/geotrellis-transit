package commonspace

object Logger {
  def log(msg:String) = {
    println(s"[COMMONSPACE]  $msg")
  }
  
  def warn(msg:String) = {
    println(s"[COMMMONSPACE WARNING]  $msg")
  }
  
  def timed(startMsg:String,endMsg:String)(f:() => Unit) = {
    log(startMsg)
    val s = System.currentTimeMillis
    f()
    val e = System.currentTimeMillis
    val t = "%,d".format(e-s)
    log(s"$endMsg (took $t ms)")
  }

  def timedCreate[T](startMsg:String,endMsg:String)(f:() => T) = {
    log(startMsg)
    val s = System.currentTimeMillis
    val result = f()
    val e = System.currentTimeMillis
    val t = "%,d".format(e-s)
    log(s"\t$endMsg (in $t ms)")
    result
  }
}
