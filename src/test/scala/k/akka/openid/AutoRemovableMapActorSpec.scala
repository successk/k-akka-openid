package k.akka.openid

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, TypedActor}
import akka.testkit.TestKit
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.concurrent.duration.FiniteDuration


class AutoRemovableMapActorSpec(_system: ActorSystem) extends TestKit(_system) with WordSpecLike with BeforeAndAfterAll {
  def this() = this(ActorSystem("AutoRemovableMapActorSpec"))

  val longDuration = new FiniteDuration(300, TimeUnit.SECONDS)
  val shortDuration = new FiniteDuration(500, TimeUnit.MILLISECONDS)

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "AutoRemovableMapActor" must {
    "Add an element and get it" in {
      val autoRemovableMapActor: AutoRemovableMapActor[String, String] =
        TypedActor(system).typedActorOf(AutoRemovableMapActor.props[String, String](longDuration))
      autoRemovableMapActor.add("key", "value")
      assert(autoRemovableMapActor.get("key") === Some("value"))
    }

    "Do not return an undefined element" in {
      val autoRemovableMapActor: AutoRemovableMapActor[String, String] =
        TypedActor(system).typedActorOf(AutoRemovableMapActor.props[String, String](longDuration))
      assert(autoRemovableMapActor.get("key") === None)
    }

    "Do not return twice an element" in {
      val autoRemovableMapActor: AutoRemovableMapActor[String, String] =
        TypedActor(system).typedActorOf(AutoRemovableMapActor.props[String, String](longDuration))
      autoRemovableMapActor.add("key", "value")
      assert(autoRemovableMapActor.get("key") === Some("value"))
      assert(autoRemovableMapActor.get("key") === None)
    }

    "Do not return an outdated element" in {
      val autoRemovableMapActor: AutoRemovableMapActor[String, String] =
        TypedActor(system).typedActorOf(AutoRemovableMapActor.props[String, String](shortDuration))
      autoRemovableMapActor.add("key", "value")
      Thread.sleep(shortDuration.toMillis + 100) // margin to be sure the element is removed
      assert(autoRemovableMapActor.get("key") === None)
    }
  }
}
