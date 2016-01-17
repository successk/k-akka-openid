package k.akka.openid

import java.net.URI

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class OpenidGoogleSpec(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with BeforeAndAfterAll {
  def this() = this(ActorSystem("OpenidGoogleSpec"))

  implicit val _actorSystem = _system
  implicit val materializer = ActorMaterializer()

  val defaultOpenidSettings = OpenidGoogleSettings(
    client = "client", secret = "secret", redirect = "redirect", external = "//host/external", path = "path"
  )

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "OpenidGoogleSpec" must {
    "returns the given path" in {
      val settings = defaultOpenidSettings.copy(path = "my-path")
      val openidGoogle = OpenidGoogle(settings)
      assert(openidGoogle.path === "my-path")
    }

    "returns a default path when none given" in {
      val settings = OpenidGoogleSettings(null, null, null)
      val openidGoogle = OpenidGoogle(settings)
      assert(openidGoogle.path !== null)
    }

    "returns a null path when null explicitly given" in {
      val settings = defaultOpenidSettings.copy(path = null)
      val openidGoogle = OpenidGoogle(settings)
      assert(openidGoogle.path === null)
    }

    "builds the uri with valid url and required parameters" in {
      val settings = defaultOpenidSettings.copy()
      val openidGoogle = OpenidGoogle(settings)
      val uri = URI.create(openidGoogle.buildRedirectURI("token"))
      assert(uri.getAuthority === "host")
      assert(uri.getPath === "/external")
      assert(extractParameters(uri) === Map(
        "response_type" -> "code",
        "scope" -> "openid",
        "client_id" -> settings.client,
        "redirect_uri" -> settings.redirect,
        "state" -> "token"
      ))
    }

    "extract the right token parameter from the given map" in {
      val settings = defaultOpenidSettings.copy()
      val openidGoogle = OpenidGoogle(settings)
      val param = openidGoogle.extractTokenFromParameters(Map("state" -> "state", "code" -> "code"))
      assert(param === Some("state"))
    }

    "do not extract any token parameter if it does not exist" in {
      val settings = defaultOpenidSettings.copy()
      val openidGoogle = OpenidGoogle(settings)
      val param = openidGoogle.extractTokenFromParameters(Map("code" -> "code"))
      assert(param === None)
    }

    "extract the right code parameter from the given map" in {
      val settings = defaultOpenidSettings.copy()
      val openidGoogle = OpenidGoogle(settings)
      val param = openidGoogle.extractCodeFromParameters(Map("state" -> "state", "code" -> "code"))
      assert(param === Some("code"))
    }

    "do not extract any code parameter if it does not exist" in {
      val settings = defaultOpenidSettings.copy()
      val openidGoogle = OpenidGoogle(settings)
      val param = openidGoogle.extractCodeFromParameters(Map("state" -> "state"))
      assert(param === None)
    }

    // TODO: How to test [[requestData]] method?
  }

  private def extractParameters(uri: URI) = {
    Map(
      uri.getQuery.split("&").map { p =>
        val split = p.split("=")
        split(0) -> split(1)
      }: _*
    )
  }
}
