package k.akka.openid

import java.security.MessageDigest

import akka.actor.{ActorSystem, TypedActor}
import akka.http.scaladsl.model.headers.{HttpCookie, Location}
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import k.akka.openid.OpenidProvider._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Random

/**
 * Settings of openid router.
 * Whatever the configuration is, it does not change the logic in the rest of the process.
 * This configuration avoids url conflict and beautify seen url.
 * If there is no settings, available url will be `/{provider}/redirect` and `/{provider}/response`
 *
 * {{{
 *   val providers = Seq(ProviderA(path = "a"), ProviderB(path = "b"))
 *
 *   // Example 1:
 *   val settings = OpenidRouterSettings(
 *     prefix = Some("session"),
 *     beforeProviderOnRedirection = Some("before-redirect"),
 *     afterProviderOnRedirection = Some("after-redirect"),
 *     beforeProviderOnResponse = Some("before-response"),
 *     afterProviderOnResponse = Some("after-response")
 *   )
 *
 *   // available routes:
 *   // /session/before-redirect/a/after-redirect => redirect to ProviderA sso
 *   // /session/before-response/a/after-response => ProviderA sso will redirect the user to this url
 *   // /session/before-redirect/b/after-redirect => redirect to ProviderB sso
 *   // /session/before-response/b/after-response => BProvider sso will redirect the user to this url
 *   val route = OpenidRouter(providers, settings)
 *
 *   // Example 2:
 *   val settings = OpenidRouterSettings(
 *     prefix = None, // default
 *     beforeProviderOnRedirection = None, // default
 *     afterProviderOnRedirection = Some("redirect"),
 *     beforeProviderOnResponse = None, // default
 *     afterProviderOnResponse = Some("response")
 *   )
 *
 *   // available routes: session here again because set manually when defining route
 *   // /session/a/redirect => redirect to ProviderA sso
 *   // /session/a/response => ProviderA sso will redirect the user to this url
 *   // /session/b/redirect => redirect to ProviderB sso
 *   // /session/b/response => BProvider sso will redirect the user to this url
 *   val route = pathPrefix("session") { OpenidRouter(providers, settings) }
 * }}}
 *
 * @param prefix                      The prefix of all routes
 * @param beforeProviderOnRedirection The prefix before all provider names to redirect to openid provider sso
 * @param afterProviderOnRedirection  The suffix after all provider names to redirect to openid provider sso
 * @param beforeProviderOnResponse    The prefix before all provider names when getting response of openid provider
 * @param afterProviderOnResponse     The suffix after all provider names when getting response of openid provider
 */
case class OpenidRouterSettings(
  prefix: Option[String] = None,
  beforeProviderOnRedirection: Option[String] = None,
  afterProviderOnRedirection: Option[String] = None,
  beforeProviderOnResponse: Option[String] = None,
  afterProviderOnResponse: Option[String] = None
)

/**
 * Builds a router for openid providers.
 */
object OpenidRouter {
  /**
   * Builds the route including all `providers` and respecting given `settings`.
   * The result of the process must be processed by `resultProcessor`.
   *
   * @param providers        The list of providers to use
   * @param settings         The settings of routing
   * @param resultProcessor  The processor of the result
   * @param actorSystem      The current actor system
   * @param materializer     The current materializer
   * @param sessionsDuration The duration the security tokens will be available between redirection to a provider and its result
   * @return
   */
  def apply(providers: Seq[OpenidProvider], settings: OpenidRouterSettings = OpenidRouterSettings())(resultProcessor: ResultProcessor)(implicit actorSystem: ActorSystem, materializer: ActorMaterializer, sessionsDuration: FiniteDuration): Route =
    new OpenidRouter(providers, settings, resultProcessor, sessionsDuration)(actorSystem, materializer).build()
}

/**
 * Openid router builder.
 * It was split from companion object to simplify the implementation and maintenance.
 *
 * @param providers        The list of providers to consider
 * @param _settings        The openid router settings
 * @param resultProcessor  The processor of results coming from this router
 * @param sessionsDuration The duration of a session a token is available from the redirection of the user and the openid provider result
 * @param actorSystem      The current actor system
 * @param materializer     The current materializer
 */
private class OpenidRouter(providers: Seq[OpenidProvider], _settings: OpenidRouterSettings, resultProcessor: ResultProcessor, sessionsDuration: FiniteDuration)(implicit actorSystem: ActorSystem, materializer: ActorMaterializer) {
  implicit val ec = actorSystem.dispatcher

  // Defines a default setting when none is given
  val settings: OpenidRouterSettings = _settings match {
    case OpenidRouterSettings(_, None, None, None, None) =>
      // It will avoids having url conflict when there is no configuration.
      _settings.copy(afterProviderOnRedirection = Some("redirect"), afterProviderOnResponse = Some("response"))
    case settings: OpenidRouterSettings => settings
  }

  /**
   * List of tokens, which will be removed when outdated (after [[sessionsDuration]]).
   */
  private val tokens: AutoRemovableMapActor[String, String] =
    TypedActor(actorSystem).typedActorOf(AutoRemovableMapActor.props[String, String](sessionsDuration))

  /**
   * Builds the route for all providers with given configuration.
   *
   * @return The built route
   */
  def build(): Route = {
    settings.prefix match {
      case Some(prefix) =>
        pathPrefix(prefix) {
          buildAllProviders
        }
      case None =>
        buildAllProviders
    }
  }

  /**
   * Builds all provider routes and concatenate all route parts.
   */
  private def buildAllProviders: Route =
    providers map {
      p => buildOneProvider(p)
    } reduce {
      (a, b) => a ~ b
    }

  /**
   * Builds one provider for redirection route and result route.
   *
   * @param provider The provider to consider
   * @return The built route
   */
  private def buildOneProvider(provider: OpenidProvider): Route = {
    buildForProcess(provider.path, settings.beforeProviderOnRedirection, settings.afterProviderOnRedirection) {
      redirection(provider)
    } ~ buildForProcess(provider.path, settings.beforeProviderOnResponse, settings.afterProviderOnResponse) {
      response(provider)
    }
  }

  /**
   * Builds a route for given process in terms of optional parts before and after the route.
   * {{{
   *   // Examples:
   *   buildForProcess("a", Some("before"), Some("after")(process) => final url = /before/a/after
   *   buildForProcess("a", None,           Some("after")(process) => final url = /a/after
   * }}}
   *
   * @param providerPath The path of the provider
   * @param beforeOpt    The optional part before the provider path
   * @param afterOpt     The optional part after the provider path
   * @param process      The process to consider
   * @return The built route
   */
  private def buildForProcess(providerPath: String, beforeOpt: Option[String], afterOpt: Option[String])(process: Route) = {
    (beforeOpt, afterOpt) match {
      case (Some(before), Some(after)) =>
        pathPrefix(before) {
          pathPrefix(providerPath) {
            path(after) {
              process
            }
          }
        }
      case (Some(before), None) =>
        pathPrefix(before) {
          path(providerPath) {
            process
          }
        }
      case (None, Some(after)) =>
        pathPrefix(providerPath) {
          path(after) {
            process
          }
        }
      case (None, None) =>
        path(providerPath) {
          process
        }
    }
  }

  /**
   * Builds the redirection response for given provider.
   */
  private def redirection(provider: OpenidProvider): Route = {
    val openidTokenCookie = generateToken()
    setCookie(HttpCookie("openidToken", value = openidTokenCookie)) { ctx =>
      val token = generateToken()
      val hash = crypt(openidTokenCookie, token)
      tokens.add(openidTokenCookie, hash)
      val uri = provider.buildRedirectURI(token)
      ctx.complete(HttpResponse(StatusCodes.TemporaryRedirect, headers = List(Location(uri))))
    }
  }

  /**
   * Builds the process for given provider and sends the result to [[resultProcessor]].
   */
  private def response(provider: OpenidProvider): Route = {
    cookie("openidToken") { openidToken =>
      deleteCookie("openidToken") {
        parameterMap { parameters => ctx =>
          provider.extractTokenFromParameters(parameters) map { tokenParameter =>
            tokens.get(openidToken.value) map { token =>
              if (crypt(openidToken.value, tokenParameter) == token) {
                provider.extractCodeFromParameters(parameters) map { code =>
                  callProvider(provider, code, ctx)
                } getOrElse {
                  resultProcessor(OpenidResultUndefinedCode(ctx))
                }
              } else {
                resultProcessor(OpenidResultInvalidState(ctx))
              }
            } getOrElse {
              resultProcessor(OpenidResultInvalidToken(ctx))
            }
          } getOrElse {
            resultProcessor(OpenidResultUndefinedToken(ctx))
          }
        }
      }
    }
  }

  /**
   * Calls the provider and sends the result to [[resultProcessor]]
   *
   * @param provider The provider to consider
   * @param code     The access code
   * @param ctx      The current request context
   * @return The request result
   */
  private def callProvider(provider: OpenidProvider, code: String, ctx: RequestContext): Future[RouteResult] = {
    val result = provider.requestData(code)

    result flatMap { providerResult =>
      resultProcessor(OpenidResultSuccess(ctx, providerResult.provider, providerResult.pid))
    } recoverWith { case t =>
      resultProcessor(OpenidResultErrorThrown(ctx, t))
    }
  }

  /**
   * Generates a random token.
   */
  private def generateToken(): String = Random.alphanumeric.take(50).mkString

  /**
   * Crypts a string with a salt.
   *
   * @param str  The string to crypt
   * @param salt The salt to use
   * @return The crypted string
   */
  private def crypt(str: String, salt: String) = {
    // Code inspired of [[akka.util.Crypt]] which is now deprecated and could be removed in the future.
    val hex = "0123456789ABCDEF"
    val bytes = (str + salt).getBytes("ASCII")
    MessageDigest.getInstance("SHA1").update(bytes)
    val builder = new java.lang.StringBuilder(bytes.length * 2)
    bytes.foreach { byte ⇒ builder.append(hex.charAt((byte & 0xF0) >> 4)).append(hex.charAt(byte & 0xF)) }
    builder.toString
  }
}
