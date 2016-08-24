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

package monix.types

/** A type-class for `F[A]` suspendable applicatives
  * whose evaluation can be memoized, along with a guarantee
  * that the side-effects only happen once.
  *
  * The `memoize` operation takes an `F[_]` instance and
  * returns a new `F` that guarantees that its evaluation and
  * all related side-effects only happen once, with the results
  * to be reused on subsequent evaluations.
  */
trait Memoizable[F[_]] extends Deferrable[F] {
  def memoize[A](fa: F[A]): F[A]

  def evalOnce[A](a: => A): F[A] =
    memoize(eval(a))
}

object Memoizable {
  @inline def apply[F[_]](implicit F: Memoizable[F]): Memoizable[F] = F
}

