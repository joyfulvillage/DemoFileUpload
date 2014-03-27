package controllers

import play.api._
import play.api.mvc._
import java.io.File
import service._
import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.postfixOps

object Application extends Controller {

  val wordCountNode = WordCountNode.node

  def index = Action.async {
    Future { Ok(views.html.index("Your new application is ready.")) }
  }

  def uploadFile = Action.async(parse.multipartFormData) { request =>
    request.body.file("fileUpload").map { txtFile =>

      implicit val timeout = Timeout(1 second)

      val filename: String = txtFile.filename
      val realFile: File = txtFile.ref.file
      (wordCountNode ? MSG_WORD_COUNT(realFile)).map {
        case MSG_RESULT(count, countMap) =>
          val resultJson = Json.obj(
            "filename" -> filename,
            "wordcount" -> count,
            "countMap" -> Json.toJson(countMap)
          )

          Ok(resultJson)
      }

    }.getOrElse {
      Future {
        Redirect(routes.Application.index).flashing(
          "error" -> "Missing file"
        )
      }
    }
  }

}