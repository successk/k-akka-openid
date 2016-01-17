# Openid implementation for Akka Http

**First of all, be aware that this library is not a part of akka framework.**

This library provides an easy-to-use implementation of openid for [Akka HTTP][akka-http].

## How to use this library?

### Import this into your project

```
libraryDependencies += "net.successk" %% "k-akka-openid" % "0.1.0"
```

It will also import akka http dependency.

### Prepares your project

In this example, we start from nothing. So we create the server.

```scala
object OpenidBoot extends App {
  // System implicit
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  
  val route = pathEndOrSingleSlash {
    // We provide a sample welcome page listing all available openid providers.
    complete(HttpResponse(entity = HttpEntity(ContentTypes.`text/html(UTF-8)`, ByteString(<html>
      <head>
        <title>Manual test</title>
        <meta charset="utf-8"/>
      </head>
      <body>
        <ul>
          <!-- Our routes will be /session/{provider}/redirection -->
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
```

This class creates a server with a welcome page containing a link to Google openid (configured below).
You can go to `http://localhost:9000` and see the result.

### Adds Openid providers

Now, we can add openid providers 

```scala
// ...
implicit val materializer = ActorMaterializer()
implicit val ec = system.dispatcher
// ===== Added code: start =====
// The duration the token is kept in memory.
// This token is generated when the user is redirected to openid provider page and requested on openid provider response.
implicit val sessionDuration = new FiniteDuration(5, TimeUnit.MINUTES)

// Configuration in terms of google credentials (see https://developers.google.com/identity/protocols/OpenIDConnect).
val googleSettings = OpenidGoogleSettings(
  client = "client-id",
  secret = "client-secret",
  redirect = "http://my.site/session/google/processing",
  external = "https://accounts.google.com/o/oauth2/v2/auth", // optional, this is already the default value
  path = "google" // optional, this is already the default value
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
  // You will need to add your own logic for each result.
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
} ~
// ===== Added code: stop =====
pathEndOrSingleSlash {
// We provide a sample welcome page listing all available openid providers.
// ...
```

### That's all!

Good job, your openid configuration is done!
Then, you only need to implement your own logic on openid responses.

## Be involved

### New providers

This library needs you to implement new providers as Twitter, Facebook, Github...
Clone this repository and follow `OpenidGoogle` example to implement a new provider.
In case of incompatibility, please open a ticket or provide a correction.

Currently, available providers are:

* Google: OpenidGoogle

### Improve documentation

If you think there is a lack of documentation, please open an issue and explain your need.

### Report issues

Please report any bug or issue to improve this library.
Help us and we will help you. ;)

## Troubleshooting

### I found a bug, what can I do

If you find a bug, please open a ticket in Github repository with enough information so we can correct it.

### My version of akka is not compatible

We will try to use the last version of [Akka HTTP][akka-http] each time we do an update.

If you use a newer version of [Akka HTTP][akka-http], please refer in Github issue which one you use.
Then we will see if we can migrate ou implementation.

In the case you use an older version of [Akka HTTP][akka-http] and you cannot update it,
please consider cloning this repository and change the version.
If there is no compatibility, you will need to correct the code yourself.
No work will be done for older versions.

### Compatibility with older versions

This library is still new and could end up with major changes.
Furthermore, akka http is still experimental and could end up with major changes.
So the semantic of versions for this library will be:

* x.y.z (x = 0):
  * x = always 0 while akka http and this library are still experimental and not stable
  * y = Major change, no guaranty of compatibility with previous version
  * z = Minor change, compatibility with previous version (usually bug correction)
* x.y.z (x > 0):
  * x = Major change, no guaranty of compatibility with previous version
  * y = Minor change (new functionality, new option), compatibility with previous version
  * z = bug correction

[akka-http]: http://doc.akka.io/docs/akka-stream-and-http-experimental/current/scala/http/