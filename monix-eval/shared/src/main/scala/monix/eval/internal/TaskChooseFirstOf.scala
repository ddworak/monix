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

package monix.eval.internal

import monix.eval.{Callback, Task}
import monix.execution.CancelableFuture
import monix.execution.atomic.Atomic
import monix.execution.cancelables._
import scala.concurrent.Promise

private[monix] object TaskChooseFirstOf {
  /**
    * Implementation for `Task.chooseFirstOf`.
    */
  def apply[A,B](fa: Task[A], fb: Task[B]): Task[Either[(A, CancelableFuture[B]), (CancelableFuture[A], B)]] =
    Task.unsafeCreate { (scheduler, conn, frameRef, cb) =>
      implicit val s = scheduler
      val pa = Promise[A]()
      val pb = Promise[B]()

      val isActive = Atomic(true)
      val connA = StackedCancelable()
      val connB = StackedCancelable()
      conn push CompositeCancelable(connA, connB)

      // First task: A
      Task.unsafeStartAsync(fa, scheduler, connA, frameRef, new Callback[A] {
        def onSuccess(valueA: A): Unit =
          if (isActive.getAndSet(false)) {
            val futureB = CancelableFuture(pb.future, connB)
            conn.pop()
            cb.asyncOnSuccess(Left((valueA, futureB)))
          } else {
            pa.success(valueA)
          }

        def onError(ex: Throwable): Unit =
          if (isActive.getAndSet(false)) {
            conn.pop()
            connB.cancel()
            cb.asyncOnError(ex)
          } else {
            pa.failure(ex)
          }
      })

      // Second task: B
      Task.unsafeStartAsync(fb, scheduler, connB, frameRef, new Callback[B] {
        def onSuccess(valueB: B): Unit =
          if (isActive.getAndSet(false)) {
            val futureA = CancelableFuture(pa.future, connA)
            conn.pop()
            cb.asyncOnSuccess(Right((futureA, valueB)))
          } else {
            pb.success(valueB)
          }

        def onError(ex: Throwable): Unit =
          if (isActive.getAndSet(false)) {
            conn.pop()
            connA.cancel()
            cb.asyncOnError(ex)
          } else {
            pb.failure(ex)
          }
      })
    }
}