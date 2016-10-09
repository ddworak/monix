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

package monix.reactive.internal.builders

import monix.execution.schedulers.ExecutionModel
import monix.execution.{Ack, Cancelable}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import scala.concurrent.Future
import scala.util.control.NonFatal

private[reactive]
final class ExecuteWithModelObservable[A](
  source: Observable[A],
  f: ExecutionModel => ExecutionModel)
  extends Observable[A] {

  override def unsafeSubscribeFn(out: Subscriber[A]): Cancelable = {
    var streamErrors = true
    try {
      val em = f(out.scheduler.executionModel)
      val newS = out.scheduler.withExecutionModel(em)
      streamErrors = false

      source.unsafeSubscribeFn(new Subscriber[A] {
        implicit val scheduler = newS
        def onError(ex: Throwable): Unit = out.onError(ex)
        def onComplete(): Unit = out.onComplete()
        def onNext(elem: A): Future[Ack] = out.onNext(elem)
      })
    }
    catch {
      case NonFatal(ex) =>
        if (streamErrors) out.onError(ex)
        else out.scheduler.reportFailure(ex)
        Cancelable.empty
    }
  }
}