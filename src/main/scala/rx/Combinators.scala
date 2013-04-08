// Copied from Scala.Rx (https://github.com/lihaoyi/scala.rx)
// Copyright (c) 2013, Li Haoyi (haoyi.sg at gmail.com)

package rx

import util.{Try, Failure, Success}
import concurrent.{ExecutionContext, Future}
import concurrent.duration._
import akka.actor.ActorSystem
import Flow.Signal
import AsyncSignals._
import concurrent.duration._
import SyncSignals._

/**
 * A collection of methods which allow you to construct Rxs from other
 * Rxs using method chaining
 */
object Combinators{
  trait EmitterMethods[+T]{ source: Flow.Emitter[T] =>
  }

  trait SignalMethods[+T]{ source: Signal[T] =>

    /**
     * Creates a new Rx which ignores Failure conditions of the source Rx; it
     * will not propagate the changes, and simply remain holding on to its last
     * value
     */
    def skipFailures(implicit p: Propagator) = filterSig((oldTry, newTry) => newTry.isSuccess)

    /**
     * Creates a new Rx which filters the updates to the old Rx, giving you
     * access to both the old Try[T] and the new Try[T] in deciding whether
     * or not you want to accept the update
     */
    def filterTry(predicate: (Try[T], Try[T]) => Boolean)
                 (implicit p: Propagator) = filterSig(predicate)

    /**
     * Creates a new Rx which ignores specific Success conditions of the source Rx; it
     * will not propagate the changes, and simply remain holding on to its last
     * value if the new value fails the filter. Optionally takes a failurePred, allowing
     * it to filter the Failure conditions as well.
     */
    def filter(successPred: T => Boolean, failurePred: Throwable => Boolean = x => true)
              (implicit p: Propagator): Signal[T] = {
      new FilterSignal(source)(
        (x, y) => (x, y) match {
          case (_, Success(value)) if successPred(value) => Success(value)
          case (_, Failure(thrown)) if failurePred(thrown) => Failure(thrown)
          case (old, _) => old
        }
      )
    }

    /**
     * Creates a new Rx which filters the updates to the old Rx, giving you
     * access to both the old value and the new value in deciding whether
     * or not you want to accept the update.
     *
     * Optionally takes a `failurePred`, allowing you to filter cases where
     * both the previous and the new value are both Failures.
     */
    def filterDiff(successPred: (T, T) => Boolean = _!=_, failurePred: (Throwable, Throwable) => Boolean = _!=_)
                  (implicit p: Propagator)= {

      filterSig(
        (x, y) => (x, y) match {
          case (Success(a), Success(b)) => successPred(a, b)
          case (Failure(a), Failure(b)) => failurePred(a, b)
          case _ => true
        }
      )
    }

    /**
     * Creates a new Rx which contains the value of the old Rx, except transformed by some
     * function.
     */
    def map[A](f: T => A)(implicit p: Propagator): Signal[A] = new MapSignal[T, A](source)(y => y.map(f))

    /**
     * Creates a new Rx which debounces the old Rx; updates coming in within `interval`
     * of a previous update get ignored. After the `interval` has passed, the last
     * un-applied update (if any) will be applied to update the value of the Rx
     */
    /*def debounce(interval: FiniteDuration, delay: FiniteDuration = 0 seconds)
                (implicit system: ActorSystem, ex: ExecutionContext, p: Propagator): Rx[T] = {

      if (delay == 0.seconds) new ImmediateDebouncedSignal[T](source, interval)
      else new DelayedRebounceSignal[T](source, interval, delay)
    }*/

    def filterSig(predicate: (Try[T], Try[T]) => Boolean)(implicit p: Propagator): Signal[T] = {
      new FilterSignal(source)((x, y) => if (predicate(x, y)) y else x)
    }

  }
  implicit class pimpedFutureSignal[T](source: Signal[Future[T]]){
    /**
     * Flattens out an Rx[Future[T]] into a Rx[T]. If the first
     * Future has not yet arrived, the AsyncSig contains its default value.
     * Afterwards, it updates itself when and with whatever the Futures complete
     * with.
     *
     * `async` can be configured with a variety of Targets, to configure
     * its handling of Futures which complete out of order (RunAlways, DiscardLate)
     */
    def async(default: T,
              target: AsyncSignals.Target[T] = AsyncSignals.RunAlways[T]())
             (implicit executor: ExecutionContext, p: Propagator): Rx[T] = {
      new AsyncSig(default, source, target)
    }
  }



}

