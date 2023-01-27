package com.github.austek.plugin.avro

import java.net.ServerSocket
import scala.util.{Failure, Success, Using}

object PortFinder {

  def findFreePort: Option[Int] = {
    Using(new ServerSocket(0)) { socket =>
      socket.setReuseAddress(true)
      val port = socket.getLocalPort
      if (port == -1) None
      else Some(port)
    } match {
      case Success(value) => value
      case Failure(exception) =>
        exception.printStackTrace()
        None
    }
  }
}
