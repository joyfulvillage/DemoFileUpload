package service

import akka.actor.{Stash, ActorRef, Actor, Props}
import play.api.libs.concurrent.Akka
import play.api.Play.current
import java.io.File
import akka.routing.SmallestMailboxRouter


class WordCountNode extends Actor with Stash {

  val WORKER_SIZE = Runtime.getRuntime.availableProcessors()

  val router: ActorRef =
    context.actorOf(Props[Counter].withRouter(SmallestMailboxRouter(WORKER_SIZE)),
      name = "counterService")

  def receive = defaultMode

  def defaultMode: Receive = {
    case MSG_WORD_COUNT(file) =>

      val fileInLines = scala.io.Source.fromFile(file).getLines()

      var lineCount = 0
      for (line <- fileInLines) {
        router ! MSG_COUNT_LINE(line)
        lineCount += 1
      }

      context.become(counting(sender, lineCount, 0, Map[String, Int]()))
  }

  def counting(origSender: ActorRef, numLeft: Int, count: Int, countMap: Map[String, Int]): Receive = {

    case MSG_LOCAL_RESULT(localCount, localCountMap) =>

      val newCount = count + localCount
      val newCountMap = WordCountNode.mapAdding(countMap, localCountMap)

      if (numLeft == 1) {
        origSender ! MSG_RESULT(newCount, newCountMap)
        unstashAll()
        context.become(defaultMode, discardOld = false)

      } else
        context.become(counting(origSender, numLeft - 1, newCount, newCountMap))

    case _ => stash()
  }


}

object WordCountNode {

  lazy val node: ActorRef = {
    val wordCountNode = Akka.system.actorOf(Props[WordCountNode],
      name = "wordCountNode")
    wordCountNode
  }

  private[service] def mapAdding(map1: Map[String, Int], map2: Map[String, Int]): Map[String, Int] = {

    var result: Map[String, Int] = map1
    map2.foreach { x =>
      val (k, v) = x
      result.get(k) match {
        case None => result = result.updated(k, v)
        case Some(value) => result = result.updated(k, value + v)
      }
    }
    result
  }
}

class Counter extends Actor {
  def receive: Receive = {
    case MSG_COUNT_LINE(str) =>

      val words = str.split(" ")

      val countMap: Map[String, Int] = words.groupBy(x => x).map(y => (y._1, y._2.size))

      sender ! MSG_LOCAL_RESULT(words.size, countMap)
  }
}

sealed trait Message

case class MSG_WORD_COUNT(file: File) extends Message

case class MSG_RESULT(count: Int, countMap: Map[String, Int])

case class MSG_COUNT_LINE(str: String) extends Message

case class MSG_LOCAL_RESULT(count: Int, countMap: Map[String, Int])