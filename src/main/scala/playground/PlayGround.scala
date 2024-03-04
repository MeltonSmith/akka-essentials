package playground

import akka.actor.{Actor, ActorRef, ActorSystem, PoisonPill, Props}

case object Ping
case object Pong

class Pinger extends Actor {
  var countDown = 100

  def receive = {
    case Pong =>
      println(s"${self.path} received pong, count down $countDown")

      if (countDown > 0) {
        countDown -= 1
        sender() ! Ping
      } else {
        sender() ! PoisonPill
        self ! PoisonPill
      }
  }
}

class Ponger(pinger: ActorRef) extends Actor {
  def receive = {
    case Ping => sender()
      println(s"${self.path} received ping")
      pinger ! Pong
  }
}

object PlayGround extends App{

  val system: ActorSystem = ActorSystem("HelloAkka")

  val pinger = system.actorOf(Props[Pinger])
//  val pinger = system.actorOf(Props[Pinger](), "pinger")

  system.actorOf(Props(classOf[Ponger], pinger), "ponger")
}
