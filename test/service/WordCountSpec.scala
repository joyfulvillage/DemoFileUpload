package service

import play.api.test._
import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import akka.pattern.ask
import scala.concurrent.{Await, ExecutionContext, Future}
import java.util.concurrent.Executors
import scala.concurrent.duration._
import scala.util.{Success, Failure}


class WordCountSpec extends PlaySpecification {

  "The mapAdding" should {
    "adds up 2 maps" in {
      val map1 = Map("a" -> 1, "b" -> 2)
      val map2 = Map("a" -> 3, "c" -> 5)

      val map3 = Map("a" -> 4, "b" -> 2, "c" -> 5)

      val result = WordCountNode.mapAdding(map1, map2)

      result === map3
    }
  }

  "The wordCountNode" should {
    "analysis the file" in new WithApplication {

      import java.io.{File => JFile}

      val testFile = new JFile("./test/service/testMaterial.txt")

      val testConfig = ConfigFactory.parseString(
        """
         akka.log-dead-letters = 0
         akka.log-dead-letters-during-shutdown = off
        """.stripMargin)

      implicit val testSystem = ActorSystem("testSystem", testConfig)

      implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

      val wordCountNode = testSystem.actorOf(Props[WordCountNode],
        name = "wordCountNode")

      val resultFuture: Future[Any] = wordCountNode ? MSG_WORD_COUNT(testFile)

      val map = Map[String, Int]("e"->1, "f"->1, "bc"->2,"a"->1, "i"->1, "b"->1, "g"->1, "c"->1, "h"->1, "d"->1)

      val result: Future[(Int, Map[String, Int])] = resultFuture.map {
        case MSG_RESULT(count, countMap) => (count, countMap)
      }

      Await.result(result, 5 seconds)

      result.value.get match {
        case Success(x) =>
          val (count, countMap) = x
          count === 11 and countMap.mustEqual(map)
        case Failure(_) => false
      }
    }
  }

}
