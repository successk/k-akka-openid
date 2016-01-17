package k.akka.openid

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.nimbusds.jwt.SignedJWT
import spray.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Configure the google version of openid.
 * See [[https://developers.google.com/identity/protocols/OpenIDConnect Google Openid Connect]].
 *
 * @param client   Your client code given by Google
 * @param secret   Your client secret given by Google
 * @param redirect The redirection url Google should send the user after connection
 * @param external The external url used to redirect the user so he can connect itself
 * @param path     The path used in your url to access google version of openid
 */
case class OpenidGoogleSettings(
  client: String, secret: String, redirect: String,
  external: String = "https://accounts.google.com/o/oauth2/v2/auth", path: String = "google"
) extends OpenidProviderSettings

/**
 * Openid with Google as provider
 *
 * @see OpenIdProviderBuilder
 */
object OpenidGoogle extends OpenidProviderBuilder[OpenidGoogleSettings] with DefaultJsonProtocol {
  override def apply(settings: OpenidGoogleSettings)(implicit actorSystem: ActorSystem, materializer: ActorMaterializer): OpenidProvider =
    new OpenidGoogle(settings)

  /**
   * Converts result obtained from Google request fetching identity information.
   */
  implicit object GoogleProviderResultJson extends RootJsonReader[ProviderIdentification] {
    override def read(value: JsValue): ProviderIdentification = value match {
      case obj: JsObject =>
        obj.fields("id_token") match {
          case JsString(idToken) =>
            val claimsSet = SignedJWT.parse(idToken).getJWTClaimsSet
            ProviderIdentification(claimsSet.getIssuer, claimsSet.getSubject)
        }
    }
  }

}

/**
 * Openid with Google as provider
 *
 * @param settings The settings for google provider
 */
class OpenidGoogle(settings: OpenidGoogleSettings)(implicit actorSystem: ActorSystem, materializer: ActorMaterializer) extends OpenidProvider {
  override def path: String = settings.path

  override def buildRedirectURI(token: String): String = {
    val prefix = settings.external
    val parameters = Map(
      "client_id" -> settings.client,
      "response_type" -> "code",
      "scope" -> "openid",
      "redirect_uri" -> settings.redirect,
      "state" -> token
    ) map { case (key, value) => key + "=" + value } mkString "&"

    s"$prefix?$parameters"
  }

  override def requestData(code: String): Future[ProviderIdentification] = {
    import OpenidGoogle._

    val parametersEntity = Map(
      "code" -> code,
      "client_id" -> settings.client,
      "client_secret" -> settings.secret,
      "redirect_uri" -> settings.redirect,
      "grant_type" -> "authorization_code"
    ) map { case (key, value) => key + "=" + value } mkString "&"

    val providerResult = for {
      response <- Http().singleRequest(HttpRequest(
        method = HttpMethods.POST,
        uri = s"https://www.googleapis.com/oauth2/v4/token",
        entity = HttpEntity(ContentType(MediaTypes.`application/x-www-form-urlencoded`, HttpCharsets.`UTF-8`), parametersEntity)
      ))
      strResult <- Unmarshal(response.entity).to[String]
    } yield strResult
    providerResult.map(_.parseJson.convertTo[ProviderIdentification])
  }

  override def extractTokenFromParameters(parameters: Map[String, String]): Option[String] = parameters.get("state")

  override def extractCodeFromParameters(parameters: Map[String, String]): Option[String] = parameters.get("code")
}
