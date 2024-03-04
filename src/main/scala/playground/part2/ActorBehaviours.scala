package playground.part2

import akka.actor.AbstractActor.Receive
import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object ActorBehaviours {

  val system: ActorSystem = ActorSystem("Some")

  object Counter {
    case object Increment
    case object Decrement
    case object Print
  }

  class Counter extends Actor {
    import Counter._

    override def receive: Receive = {
      case Increment => context.become(incr(1), discardOld = false)
      case Decrement => context.become(decr(1), discardOld = false)
      case Print => println(0)
    }

    def incr(value: Int): Receive = {
      case Increment => context.become(incr(value + 1), discardOld = false)
      case Decrement => context.unbecome()
      case Print => println(value)
    }

    def decr(value: Int): Receive = {
      case Increment => context.unbecome()
      case Decrement => context.become(decr(value - 1), discardOld = false)
      case Print => println(value)
    }
  }

  import Counter._
  private val myCounter: ActorRef = system.actorOf(Props[Counter], "myCounter")

  (1 to 5).foreach(_ => myCounter ! Increment)
  (1 to 3).foreach(_ => myCounter ! Decrement)
  myCounter ! Print

  case class Vote(candidate: String)
  case object VoteStatusRequest
  case class VoteStatusReply(candidate: Option[String])
  class Citizen extends Actor {
    override def receive: Receive = {
      case Vote(name) =>  context.become(voted(name))
      case VoteStatusRequest => sender() ! VoteStatusReply(None)
    }

    def voted(candidate: String): Receive = {
      case VoteStatusRequest => sender() ! VoteStatusReply(Some(candidate))
    }
  }

  case class AggregateVotes(citizen: Set[ActorRef])

  class VoteAggregator extends Actor {
    //    var stillWaiting: Set[ActorRef] = Set()
    //    var currentStats: Map[String, Int] = Map()

    override def receive: Receive = awaitingCommand

    def awaitingCommand: Receive = {
      case AggregateVotes(citizens) =>
        citizens.foreach(citizenRef => citizenRef ! VoteStatusRequest)
        context.become(awaitingStatuses(citizens, Map()))
    }

    def awaitingStatuses(stillWaiting: Set[ActorRef], currentStats: Map[String, Int]): Receive = {
      case VoteStatusReply(None) =>
        //a citizen hasn't voted yet
        sender() ! VoteStatusRequest //this might end up in an infinite loop
      case VoteStatusReply(Some(candidate)) =>
        val newStillWaiting = stillWaiting - sender()
        val currentVotesOfCandidate = currentStats.getOrElse(candidate, 0)
        val newStats = currentStats + (candidate -> (currentVotesOfCandidate + 1))
        if (newStillWaiting.isEmpty)
          println(s"[aggregator] poll stats: $newStats")
        else { //still need to process some statuses
          context.become(awaitingStatuses(newStillWaiting, newStats))
        }
    }
  }



  val alice = system.actorOf(Props[Citizen])
  val bob = system.actorOf(Props[Citizen])
  val charlie = system.actorOf(Props[Citizen])
  val daniel = system.actorOf(Props[Citizen])

  alice ! Vote("Martin")
  bob ! Vote("Jonas")
  charlie ! Vote("Roland")
  daniel ! Vote("Roland")

  val voteAggregator = system.actorOf(Props[VoteAggregator])
  voteAggregator ! AggregateVotes(Set(alice, bob, charlie, daniel))

  def main(args: Array[String]): Unit = {

  }
}
