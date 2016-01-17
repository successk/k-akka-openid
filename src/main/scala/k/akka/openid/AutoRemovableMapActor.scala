package k.akka.openid

import akka.actor.{Cancellable, TypedActor, TypedProps}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
 * This interface describes an actor which works as a simplified Map.
 * The developer can add values inside the map and retrieve it.
 * Once the value is retrieved, it is removed from the Map.
 *
 * To construct the [[AutoRemovableMapActor]], you need to call [[AutoRemovableMapActor.props]].
 * This function accepts one argument which is the [[FiniteDuration]] each element can stay in the map.
 * Passed this duration, the element is removed from the map and cannot be get furthermore.
 *
 * @tparam A The type of map keys
 * @tparam B The type of map values
 */
trait AutoRemovableMapActor[A, B] {
  /**
   * Adds an element in the map.
   *
   * @param key   The key of the element
   * @param value The value
   */
  def add(key: A, value: B): Unit

  /**
   * Gets an element in terms of its key.
   *
   * @param key The key of the element to retrieve
   * @return The value of the map in a [[Option]] if existing, otherwise [[None]]
   */
  def get(key: A): Option[B]

  /**
   * Private, should be used inside the implementation of this map only.
   */
  def _removeScheduled(key: A): Unit
}

object AutoRemovableMapActor {
  /**
   * Returns the props to constructs a new [[AutoRemovableMapActor]] with an explicit finite duration.
   *
   * @param duration The duration to use
   * @tparam A The key type
   * @tparam B The value type
   * @return The props to create the actor
   * @see AutoRemovableMapActor
   */
  def props[A, B](duration: FiniteDuration): TypedProps[AutoRemovableMapActor[A, B]] =
    TypedProps(classOf[AutoRemovableMapActor[A, B]], new AutoRemovableMapActorImpl[A, B](duration))

  /**
   * Returns the props to constructs a new [[AutoRemovableMapActor]] with a finite duration defined as a length and a time unit.
   *
   * @param duration The duration to use
   * @tparam A The key type
   * @tparam B The value type
   * @return The props to create the actor
   * @see AutoRemovableMapActor
   */
  def props[A, B](duration: Int, timeUnit: TimeUnit): TypedProps[AutoRemovableMapActor[A, B]] =
    props[A, B](new FiniteDuration(duration, timeUnit))

  /**
   * Returns the props to constructs a new [[AutoRemovableMapActor]] with a finite duration defined as a number of seconds.
   *
   * @param duration The duration to use
   * @tparam A The key type
   * @tparam B The value type
   * @return The props to create the actor
   * @see AutoRemovableMapActor
   */
  def props[A, B](duration: Int): TypedProps[AutoRemovableMapActor[A, B]] =
    props[A, B](duration, SECONDS)
}

class AutoRemovableMapActorImpl[A, B](duration: FiniteDuration) extends AutoRemovableMapActor[A, B] {
  private val internalMap = mutable.Map[A, B]()
  private val timerMap = mutable.Map[A, Cancellable]()

  override def add(key: A, value: B) = {
    val self = TypedActor.self[AutoRemovableMapActor[A, B]]
    remove(key)
    internalMap(key) = value
    timerMap(key) = TypedActor.context.system.scheduler.scheduleOnce(duration) {
      self._removeScheduled(key)
    }
  }

  override def get(key: A): Option[B] = remove(key)

  override def _removeScheduled(key: A): Unit = remove(key)

  def remove(key: A): Option[B] = {
    timerMap.remove(key).foreach(_.cancel())
    internalMap.remove(key)
  }
}
