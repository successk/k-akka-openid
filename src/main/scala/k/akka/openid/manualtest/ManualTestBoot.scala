/**
 * This file is used to show a basic usage of this library and more importantly to manually test it.
 */

package k.akka.openid.manualtest

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import k.akka.openid.OpenidProvider._
import k.akka.openid._

import scala.concurrent.duration.FiniteDuration
import scala.io.StdIn

object Config {
  lazy val config = ConfigFactory.load()

  lazy val googleClient = config.getString("session.openid.google.client")

  lazy val googleSecret = config.getString("session.openid.google.secret")

  lazy val googleRedirect = config.getString("session.openid.google.redirect")

  lazy val googleExternal = config.getString("session.openid.google.external")

  lazy val maxSeconds = config.getInt("session.openid.max-seconds")
}

object ManualTestBoot extends App {

  import Config._

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val sessionDuration = new FiniteDuration(maxSeconds, TimeUnit.SECONDS)

  // Configuration from application.conf, in terms of google credentials.
  val googleSettings = OpenidGoogleSettings(
    client = googleClient,
    secret = googleSecret,
    redirect = googleRedirect,
    external = googleExternal,
    path = "google"
  )
  // There is only google for now
  val providers = Seq(OpenidGoogle(googleSettings))

  // Available url will be:
  // /session/google/redirection => redirect to google sso
  // /session/google/processing => google will redirect the user to this url
  val routerSettings = OpenidRouterSettings(
    prefix = Some("session"),
    afterProviderOnRedirection = Some("redirection"),
    afterProviderOnResponse = Some("processing")
  )

  val route = OpenidRouter(providers, routerSettings) {
    // Each case represents a possible result the openid router provides
    case OpenidResultSuccess(ctx, provider, pid) =>
      ctx.complete(s"(provider, pid) = ($provider, $pid)")
    case OpenidResultUndefinedCode(ctx) =>
      ctx.complete("undefined code")
    case OpenidResultUndefinedToken(ctx) =>
      ctx.complete("undefined token")
    case OpenidResultInvalidToken(ctx) =>
      ctx.complete("invalid token")
    case OpenidResultInvalidState(ctx) =>
      ctx.complete("invalid state")
    case OpenidResultErrorThrown(ctx, error) =>
      ctx.complete("error: " + error.getMessage)
  } ~ pathEndOrSingleSlash {
    // We provide a sample welcome page listing all available openid providers.
    complete(HttpResponse(entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, ByteString(<html>
      <head>
        <title>Manual test</title>
        <meta charset="utf-8"/>
      </head>
      <body>
        <ul>
          <li>
            <a href="/session/google/redirection">Google</a>
          </li>
        </ul>
      </body>
    </html>.toString()))))
  }

  // Starts the server
  val server = Http().bindAndHandle(handler = route, interface = "localhost", port = 9000)
  println(s"Server online at http://localhost:9000")
  println("Press RETURN to stop...")
  StdIn.readLine()

  // Stops the server
  println("Stopping")
  server.flatMap(_.unbind()).onComplete(_ ⇒ system.awaitTermination())
}
