/*
 * Copyright (c) 2014-2016 by its authors. Some rights reserved.
 * See the project homepage at: https://monix.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package monix.execution.schedulers

import monix.execution.Scheduler
import scala.concurrent.{BlockContext, CanAwait}
import scala.util.control.NonFatal

/** Adds trampoline execution capabilities to
  * [[monix.execution.Scheduler schedulers]], when
  * inherited.
  *
  * When it receives [[LocalRunnable]] instances, it
  * switches to a trampolined mode where all incoming
  * [[LocalRunnable LocalRunnables]] are executed on the
  * current thread.
  *
  * This is useful for light-weight callbacks. The idea is
  * borrowed from the implementation of `scala.concurrent.Future`.
  * Currently used as an optimization by `Task` in processing
  * its internal callbacks.
  */
trait LocalBatchingExecutor extends Scheduler {
  private[this] val localTasks = new ThreadLocal[List[Runnable]]()

  protected def executeAsync(r: Runnable): Unit

  final def execute(runnable: Runnable): Unit =
    runnable match {
      case _: LocalRunnable =>
        localTasks.get() match {
          case null =>
            executeAsync(new Batch(runnable, Nil))
          case some =>
            // If we are already in batching mode, add to stack
            localTasks.set(runnable :: some)
        }
      case _ =>
        // No local execution, forwards to underlying context
        executeAsync(runnable)
    }

  private final class Batch(runHead: Runnable, runTail: List[Runnable])
    extends Runnable with BlockContext {

    private[this] var cachedBatch: List[Runnable] = runTail
    private[this] var parentBlockContext: BlockContext = null

    def run(): Unit = {
      parentBlockContext = BlockContext.current
      BlockContext.withBlockContext(this) {
        try {
          runLoop(runHead)
        }
        finally {
          parentBlockContext = null
        }
      }
    }

    def blockOn[T](thunk: => T)(implicit permission: CanAwait): T = {
      forkTheRest(Nil)
      parentBlockContext.blockOn(thunk)
    }

    def runLoop(r: Runnable): Unit = {
      try r.run() catch {
        case ex: Throwable =>
          forkTheRest(null)
          if (NonFatal(ex)) reportFailure(ex) else throw ex
      }

      cachedBatch match {
        case head :: tail =>
          cachedBatch = tail
          runLoop(head)
        case Nil =>
          localTasks.get() match {
            case head :: tail =>
              localTasks.set(Nil)
              cachedBatch = tail
              runLoop(head)
            case Nil =>
              localTasks.set(null)
            case null =>
              () // do nothing else
          }
      }
    }

    def forkTheRest(newLocalTasks: Nil.type): Unit = {
      val remaining = cachedBatch ::: localTasks.get()
      localTasks.set(newLocalTasks)
      cachedBatch = Nil

      remaining match {
        case null | Nil => ()
        case head :: tail =>
          executeAsync(new Batch(head, tail))
      }
    }
  }
}
