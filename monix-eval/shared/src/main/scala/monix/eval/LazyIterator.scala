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

package monix.eval

import monix.eval.internal.{IteratorLikeBuilders, IteratorLike}

import scala.util.control.NonFatal

/** An `LazyIterator` represents a [[Coeval]] based synchronous
  * iterator.
  *
  * The implementation is practically wrapping
  * a [[ConsStream]] of [[Coeval]], provided for convenience.
  */
final case class LazyIterator[+A](stream: ConsStream[A,Coeval])
  extends IteratorLike[A, Coeval, LazyIterator] {

  def transform[B](f: (ConsStream[A, Coeval]) => ConsStream[B, Coeval]): LazyIterator[B] = {
    val next = try f(stream) catch { case NonFatal(ex) => ConsStream.Error[Coeval](ex) }
    LazyIterator(next)
  }
}

object LazyIterator extends IteratorLikeBuilders[Coeval, LazyIterator] {
  def fromStream[A](stream: ConsStream[A, Coeval]): LazyIterator[A] =
    LazyIterator(stream)
}