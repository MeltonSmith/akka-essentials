package playground.part2

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

import scala.annotation.tailrec
import scala.collection.immutable

object ChildActorsExercise  extends  App {

  object WordCounterMaster {
    case class Initialize(nChildren: Int)

    case class WordCountTask(text: String, chunkSize: Int = 8)
    case class WordCountTaskV2(taskId: Int, text: String)

    case class WordCountReply(count: Int)
    case class WordCountReplyV2(taskId: Int, count: Int)
  }

  class WordCounterMaster extends Actor {

    import WordCounterMaster._

    override def receive: Receive = {
//      case Initialize(number) =>
//        val refs = (0 to number).map(elem => {
//          println(s"${self.path} creating child")
//          context.actorOf(Props[WordCounterWorker], s"worker_$elem")
//        })
//        context.become(withWorkers(refs))
      case Initialize(number) =>
        val refs = for (i <- 0 to number) yield context.actorOf(Props[WordCounterWorkerV2], s"wcw_$i")
        context.become(withChildren(refs, 0, 0, Map()))
    }

    def withChildren(chilrenRefs: Seq[ActorRef], currentChildIndex: Int, currentTaskId: Int, requestMap: Map[Int, ActorRef]): Receive  = {
        case text: String =>
          println(s"[master] I have received: $text - I will send it to child $currentChildIndex")
          val originalSender = sender()
          val task = WordCountTaskV2(currentTaskId, text)
          val childrenRef = chilrenRefs(currentChildIndex)
          childrenRef ! task
          val nextChildIndex = (currentChildIndex + 1) % chilrenRefs.length
          val newTaskId = currentTaskId + 1
          val newRequestMap = requestMap + (currentTaskId -> originalSender)
          context.become(withChildren(chilrenRefs, nextChildIndex, newTaskId, newRequestMap))
        case WordCountReplyV2(id, count) =>
          val originalSender = requestMap(id)
          originalSender ! count
          context.become(withChildren(chilrenRefs, currentChildIndex, currentTaskId, requestMap - id))
    }

    def withWorkers(workers: Seq[ActorRef]): Receive = {
      case WordCountTask(text, chunkSize) =>
        val iterator = Iterator.continually(workers).flatten
        @tailrec
        def sendText(text: String, seq: Seq[WordCountTask]): Seq[WordCountTask] = {
          if (text.length <= chunkSize || text.indexWhere(_.isWhitespace) == -1) {
            val task = WordCountTask(text)
            return task +: seq
          }
          if (text.isEmpty)
            return seq

          val i = text.lastIndexWhere(_.isWhitespace, chunkSize) match {
            case -1 => text.indexWhere(_.isWhitespace)
            case some: Int => some
          }

          val task = WordCountTask(text.slice(0, i))
          val tasks = task +: seq
          sendText(text.slice(i, text.length).stripLeading(), tasks)
        }

        val tasksSeq = sendText(text, Seq[WordCountTask]())
        context.become(awaitingCounts(tasksSeq, 0))
        for (task <- tasksSeq) {
          iterator.next() ! task
        }


    }

     def awaitingCounts(tasksLeft: Seq[WordCountTask], result: Int): Receive = {
       case WordCountReply(value) =>
         val newAwaitingTasks = tasksLeft.tail
         val newResult = result + value
         if (newAwaitingTasks.isEmpty)
           println(s"[word counter] poll stats: $newResult")
         else { //still need to process some statuses
           context.become(awaitingCounts(newAwaitingTasks, newResult))
         }
     }

  }

  class WordCounterWorker extends Actor {

    import WordCounterMaster._
    override def receive: Receive = {
      case WordCountTask(text, _) => sender() ! WordCountReply(text.split(" ").length)
    }
  }

  class WordCounterWorkerV2 extends Actor {

    import WordCounterMaster._
    override def receive: Receive = {
      case WordCountTaskV2(id, text) =>
        println(s"${self.path} I have received task with $id with $text")
        sender() ! WordCountReplyV2(id, text.split(" ").length)
    }
  }


  class TestActor extends Actor {
    import WordCounterMaster._
    override def receive: Receive = {
      case "go" =>
        val master = context.actorOf(Props[WordCounterMaster], "master")
        master ! Initialize(3)
        val texts = List("I love Akka", "Scala is super dope", "yes", "me too")
        texts.foreach(text => master ! text)
      case count: Int =>
        println(s"[test actor] I received a reply: $count")
    }
  }

  /*
   - create WordCounterMaster
   - send Initialize(10) to wordCounterMaster
   - send "akka is awesome" to wordCounterMaster
    wcm will send a wordCountTask("...") to one of its children
    child replies with a WordCountReply(3) to the master
    master replies with 3 to the sender
    request -> wcm -> wcw
     r <- wcm <-
   */
  //round robin logic
  // 1,2,3,4,5 and 7 tasks
  // 1,2,3,4,5,6,7

  import WordCounterMaster._

  val system = ActorSystem("exercise")
//  val wcm = system.actorOf(Props[WordCounterMaster], "wcm")
//  wcm ! Initialize(10)
//  wcm ! WordCountTask("This is a demo text for a word count task", 1)

  private val testActor: ActorRef = system.actorOf(Props[TestActor], "testActor")
  testActor ! "go"



}
