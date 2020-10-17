package io.kensu.redis_streams_zio.specs.zio

import zio.test.Assertion
import zio.test.AssertionM.Render.param

trait CustomAssertions {

  def hasThrowableMassage(expectedMessage: String): Assertion[Throwable] =
    Assertion.assertion("throwableHasMassage")(param(expectedMessage)) { th =>
      Option(th.getMessage) match {
        case Some(value) => value.equals(expectedMessage)
        case None        => false
      }
    }
}
