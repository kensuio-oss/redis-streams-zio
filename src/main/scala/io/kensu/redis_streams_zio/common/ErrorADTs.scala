package io.kensu.redis_streams_zio.common

import scala.util.control.NoStackTrace

sealed trait StreamError extends Throwable with Product with NoStackTrace
case object RetryableStreamError extends StreamError
case object NonRetryableStreamError extends StreamError
