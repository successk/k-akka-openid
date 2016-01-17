package k.akka.openid

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.concurrent.Future

case class OpenidProviderMockSettings(path: String = "mock") extends OpenidProviderSettings

object OpenidProviderMock extends OpenidProviderBuilder[OpenidProviderMockSettings] {
  override def apply(settings: OpenidProviderMockSettings)(implicit actorSystem: ActorSystem, materializer: ActorMaterializer): OpenidProvider =
    new OpenidProviderMock(settings)
}

class OpenidProviderMock(settings: OpenidProviderMockSettings)(implicit actorSystem: ActorSystem, materializer: ActorMaterializer) extends OpenidProvider {
  override def path: String = settings.path

  override def buildRedirectURI(token: String): String = s"//host/path?token=$token"

  override def requestData(code: String): Future[ProviderIdentification] =
    if (code == "valid")
      Future.successful(ProviderIdentification("provider", "user-pid"))
    else
      Future.failed(new Exception("invalid code"))

  override def extractTokenFromParameters(parameters: Map[String, String]): Option[String] = parameters.get("token")

  override def extractCodeFromParameters(parameters: Map[String, String]): Option[String] = parameters.get("code")
}

