package k.akka.openid

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{RequestContext, RouteResult}
import akka.stream.ActorMaterializer

import scala.concurrent.Future

object OpenidProvider {

  type ResultProcessor = Function[OpenidResult, Future[RouteResult]]

  /**
   * Result of openid process
   */
  sealed trait OpenidResult {
    /**
     * Returns the request context, from akka http route structure
     */
    def requestContext: RequestContext

    /**
     * Indicates if the result is a success or a failure
     */
    def success: Boolean
  }

  /**
   * The result is a success.
   *
   * @param requestContext The request context
   * @param provider       The openid provider
   * @param pid            The openid user id for given provider
   */
  case class OpenidResultSuccess(requestContext: RequestContext, provider: String, pid: String) extends OpenidResult {
    override def success: Boolean = true
  }

  /**
   * The openid provider did not returned a code.
   * This error should happen only when the request is forged.
   *
   * @param requestContext The request context
   */
  case class OpenidResultUndefinedCode(requestContext: RequestContext) extends OpenidResult {
    override def success: Boolean = false
  }

  /**
   * The request contains any token in its parameters.
   * This error should happen only when request is forged.
   *
   * @param requestContext The request context
   */
  case class OpenidResultUndefinedToken(requestContext: RequestContext) extends OpenidResult {
    override def success: Boolean = false
  }

  /**
   * The client does not have any token defined for it.
   * This error should happen when the user is too long to connect in the given provider or when the url was forged.
   *
   * @param requestContext The request context
   */
  case class OpenidResultInvalidToken(requestContext: RequestContext) extends OpenidResult {
    override def success: Boolean = false
  }

  /**
   * The token from request and the one defined for given client does not match.
   *
   * @param requestContext The request context
   */
  case class OpenidResultInvalidState(requestContext: RequestContext) extends OpenidResult {
    override def success: Boolean = false
  }

  /**
   * There is an error during processing
   *
   * @param requestContext The request context
   * @param error          The error thrown
   */
  case class OpenidResultErrorThrown(requestContext: RequestContext, error: Throwable) extends OpenidResult {
    override def success: Boolean = false
  }

}

/**
 * Defines the interface all openid providers should validate.
 * These methods are used in [[OpenidRouter]] to process requests.
 */
trait OpenidProvider {
  /**
   * The prefix in routes.
   */
  def path: String

  /**
   * Builds the uri used to redirect the client to the provider sso page.
   *
   * @param token The request token, used to avoid request forgery
   * @return The built uri
   */
  def buildRedirectURI(token: String): String

  /**
   * Fetchs user information from provider with given authorization code.
   *
   * @param code The authorization code obtained from user acceptation on target provider
   * @return The identification of the user for this provider
   */
  def requestData(code: String): Future[ProviderIdentification]

  /**
   * Extracts the token from parameters.
   * This token was sent to and returned from provider to avoid request forgery.
   *
   * @param parameters The map of parameters from the request
   * @return The extracted token if defined
   */
  def extractTokenFromParameters(parameters: Map[String, String]): Option[String]

  /**
   * Extracts the authorization code from parameters.
   * This code will be used to fetch user information from provider.
   *
   * @param parameters The map of parameters from the request
   * @return The extracted code if defined
   */
  def extractCodeFromParameters(parameters: Map[String, String]): Option[String]
}

/**
 * All objects extending from this trait will be considered as an openid provider builder.
 *
 * @tparam A The type used for settings for given openid provider
 */
trait OpenidProviderBuilder[A <: OpenidProviderSettings] {
  /**
   * Builds the openid provider, with given settings.
   * See openid provider settings type for more information
   *
   * @return The built openid provider
   */
  def apply(settings: A)(implicit actorSystem: ActorSystem, materializer: ActorMaterializer): OpenidProvider
}

trait OpenidProviderSettings {
  /**
   * The path to use for given provider
   */
  def path: String
}

/**
 * The identification information returned by the provider
 *
 * @param provider The provider url
 * @param pid      The user id for given provider
 */
case class ProviderIdentification(provider: String, pid: String)
