package k.akka.openid

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.testkit.ScalatestRouteTest
import k.akka.openid.OpenidProvider._
import org.scalatest.Matchers._
import org.scalatest.WordSpecLike

import scala.concurrent.duration.FiniteDuration

class OpenidRouterSpec extends WordSpecLike with ScalatestRouteTest {
  implicit val sessionDuration = new FiniteDuration(1, TimeUnit.MINUTES)

  val providers = Seq(OpenidProviderMock(OpenidProviderMockSettings("tst")))
  val routerSettings = OpenidRouterSettings(
    prefix = Some("session"),
    afterProviderOnRedirection = Some("redirection"),
    afterProviderOnResponse = Some("processing")
  )

  val route = OpenidRouter(providers, routerSettings) {
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
  }

  "OpenidRouterSpec" must {
    "redirect to provider url" in {
      Get("/session/tst/redirection") ~> route ~> check {
        assert(response.status === StatusCodes.TemporaryRedirect)
        val location = response.getHeader("Location").getOrElse(null)
        assert(location !== null)
        location shouldBe a[Location]
        location.asInstanceOf[Location].uri.toString() should startWith("//host/path?token=")
      }
    }

    // TODO: How to test chaining url (redirection to Openid provider and redirection to this site)?
  }
}

