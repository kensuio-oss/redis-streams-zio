package io.kensu.redis_streams_zio.specs.adapters

import java.{ lang, util }
import java.util.Date
import java.util.concurrent.TimeUnit

import org.redisson.api._
import org.redisson.client.codec.Codec

class RStreamByteArrayAdapter[K, V]() extends RStream[K, V] {

  override def createGroup(groupName: String): Unit = ???

  override def createGroup(groupName: String, id: StreamMessageId): Unit = ???

  override def removeGroup(groupName: String): Unit = ???

  override def removeConsumer(groupName: String, consumerName: String): Long = ???

  override def updateGroupMessageId(groupName: String, id: StreamMessageId): Unit = ???

  override def ack(groupName: String, ids: StreamMessageId*): Long = ???

  override def getPendingInfo(groupName: String): PendingResult = ???

  override def listPending(groupName: String): PendingResult = ???

  override def listPending(
    groupName: String,
    startId: StreamMessageId,
    endId: StreamMessageId,
    count: Int
  ): util.List[PendingEntry] = ???

  override def listPending(
    groupName: String,
    consumerName: String,
    startId: StreamMessageId,
    endId: StreamMessageId,
    count: Int
  ): util.List[PendingEntry] = ???

  override def pendingRange(
    groupName: String,
    startId: StreamMessageId,
    endId: StreamMessageId,
    count: Int
  ): util.Map[StreamMessageId, util.Map[K, V]] = ???

  override def pendingRange(
    groupName: String,
    consumerName: String,
    startId: StreamMessageId,
    endId: StreamMessageId,
    count: Int
  ): util.Map[StreamMessageId, util.Map[K, V]] = ???

  override def claim(
    groupName: String,
    consumerName: String,
    idleTime: Long,
    idleTimeUnit: TimeUnit,
    ids: StreamMessageId*
  ): util.Map[StreamMessageId, util.Map[K, V]] = ???

  override def fastClaim(
    groupName: String,
    consumerName: String,
    idleTime: Long,
    idleTimeUnit: TimeUnit,
    ids: StreamMessageId*
  ): util.List[StreamMessageId] = ???

  override def readGroup(
    groupName: String,
    consumerName: String,
    ids: StreamMessageId*
  ): util.Map[StreamMessageId, util.Map[K, V]] = ???

  override def readGroup(
    groupName: String,
    consumerName: String,
    count: Int,
    ids: StreamMessageId*
  ): util.Map[StreamMessageId, util.Map[K, V]] = ???

  override def readGroup(
    groupName: String,
    consumerName: String,
    timeout: Long,
    unit: TimeUnit,
    ids: StreamMessageId*
  ): util.Map[StreamMessageId, util.Map[K, V]] = ???

  override def readGroup(
    groupName: String,
    consumerName: String,
    count: Int,
    timeout: Long,
    unit: TimeUnit,
    ids: StreamMessageId*
  ): util.Map[StreamMessageId, util.Map[K, V]] = ???

  override def readGroup(
    groupName: String,
    consumerName: String,
    id: StreamMessageId,
    nameToId: util.Map[String, StreamMessageId]
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readGroup(
    groupName: String,
    consumerName: String,
    count: Int,
    id: StreamMessageId,
    nameToId: util.Map[String, StreamMessageId]
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readGroup(
    groupName: String,
    consumerName: String,
    count: Int,
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    nameToId: util.Map[String, StreamMessageId]
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readGroup(
    groupName: String,
    consumerName: String,
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    nameToId: util.Map[String, StreamMessageId]
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readGroup(
    groupName: String,
    consumerName: String,
    id: StreamMessageId,
    key2: String,
    id2: StreamMessageId
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readGroup(
    groupName: String,
    consumerName: String,
    id: StreamMessageId,
    key2: String,
    id2: StreamMessageId,
    key3: String,
    id3: StreamMessageId
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readGroup(
    groupName: String,
    consumerName: String,
    count: Int,
    id: StreamMessageId,
    key2: String,
    id2: StreamMessageId
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readGroup(
    groupName: String,
    consumerName: String,
    count: Int,
    id: StreamMessageId,
    key2: String,
    id2: StreamMessageId,
    key3: String,
    id3: StreamMessageId
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readGroup(
    groupName: String,
    consumerName: String,
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    key2: String,
    id2: StreamMessageId
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readGroup(
    groupName: String,
    consumerName: String,
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    key2: String,
    id2: StreamMessageId,
    key3: String,
    id3: StreamMessageId
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readGroup(
    groupName: String,
    consumerName: String,
    count: Int,
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    key2: String,
    id2: StreamMessageId
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readGroup(
    groupName: String,
    consumerName: String,
    count: Int,
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    key2: String,
    id2: StreamMessageId,
    key3: String,
    id3: StreamMessageId
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def size(): Long = ???

  override def add(key: K, value: V): StreamMessageId = ???

  override def add(id: StreamMessageId, key: K, value: V): Unit = ???

  override def add(key: K, value: V, trimLen: Int, trimStrict: Boolean): StreamMessageId = ???

  override def add(id: StreamMessageId, key: K, value: V, trimLen: Int, trimStrict: Boolean): Unit = ???

  override def addAll(entries: util.Map[K, V]): StreamMessageId = ???

  override def addAll(id: StreamMessageId, entries: util.Map[K, V]): Unit = ???

  override def addAll(entries: util.Map[K, V], trimLen: Int, trimStrict: Boolean): StreamMessageId = ???

  override def addAll(id: StreamMessageId, entries: util.Map[K, V], trimLen: Int, trimStrict: Boolean): Unit = ???

  override def read(ids: StreamMessageId*): util.Map[StreamMessageId, util.Map[K, V]] = ???

  override def read(count: Int, ids: StreamMessageId*): util.Map[StreamMessageId, util.Map[K, V]] = ???

  override def read(timeout: Long, unit: TimeUnit, ids: StreamMessageId*): util.Map[StreamMessageId, util.Map[K, V]] =
    ???

  override def read(
    count: Int,
    timeout: Long,
    unit: TimeUnit,
    ids: StreamMessageId*
  ): util.Map[StreamMessageId, util.Map[K, V]] = ???

  override def read(
    id: StreamMessageId,
    name2: String,
    id2: StreamMessageId
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def read(
    id: StreamMessageId,
    name2: String,
    id2: StreamMessageId,
    name3: String,
    id3: StreamMessageId
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def read(
    id: StreamMessageId,
    nameToId: util.Map[String, StreamMessageId]
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def read(
    count: Int,
    id: StreamMessageId,
    name2: String,
    id2: StreamMessageId
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def read(
    count: Int,
    id: StreamMessageId,
    name2: String,
    id2: StreamMessageId,
    name3: String,
    id3: StreamMessageId
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def read(
    count: Int,
    id: StreamMessageId,
    nameToId: util.Map[String, StreamMessageId]
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def read(
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    name2: String,
    id2: StreamMessageId
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def read(
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    name2: String,
    id2: StreamMessageId,
    name3: String,
    id3: StreamMessageId
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def read(
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    nameToId: util.Map[String, StreamMessageId]
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def read(
    count: Int,
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    name2: String,
    id2: StreamMessageId
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def read(
    count: Int,
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    name2: String,
    id2: StreamMessageId,
    name3: String,
    id3: StreamMessageId
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def read(
    count: Int,
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    nameToId: util.Map[String, StreamMessageId]
  ): util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def range(startId: StreamMessageId, endId: StreamMessageId): util.Map[StreamMessageId, util.Map[K, V]] = ???

  override def range(
    count: Int,
    startId: StreamMessageId,
    endId: StreamMessageId
  ): util.Map[StreamMessageId, util.Map[K, V]] = ???

  override def rangeReversed(
    startId: StreamMessageId,
    endId: StreamMessageId
  ): util.Map[StreamMessageId, util.Map[K, V]] = ???

  override def rangeReversed(
    count: Int,
    startId: StreamMessageId,
    endId: StreamMessageId
  ): util.Map[StreamMessageId, util.Map[K, V]] = ???

  override def remove(ids: StreamMessageId*): Long = ???

  override def trim(size: Int): Long = ???

  override def trimNonStrict(size: Int): Long = ???

  override def getInfo: StreamInfo[K, V] = ???

  override def listGroups(): util.List[StreamGroup] = ???

  override def listConsumers(groupName: String): util.List[StreamConsumer] = ???

  override def expire(timeToLive: Long, timeUnit: TimeUnit): Boolean = ???

  override def expireAt(timestamp: Long): Boolean = ???

  override def expireAt(timestamp: Date): Boolean = ???

  override def clearExpire(): Boolean = ???

  override def remainTimeToLive(): Long = ???

  override def sizeInMemory(): Long = ???

  override def restore(state: Array[Byte]): Unit = ???

  override def restore(state: Array[Byte], timeToLive: Long, timeUnit: TimeUnit): Unit = ???

  override def restoreAndReplace(state: Array[Byte]): Unit = ???

  override def restoreAndReplace(state: Array[Byte], timeToLive: Long, timeUnit: TimeUnit): Unit = ???

  override def dump(): Array[Byte] = ???

  override def touch(): Boolean = ???

  override def migrate(host: String, port: Int, database: Int, timeout: Long): Unit = ???

  override def copy(host: String, port: Int, database: Int, timeout: Long): Unit = ???

  override def move(database: Int): Boolean = ???

  override def getName: String = ???

  override def delete(): Boolean = ???

  override def unlink(): Boolean = ???

  override def rename(newName: String): Unit = ???

  override def renamenx(newName: String): Boolean = ???

  override def isExists: Boolean = ???

  override def getCodec: Codec = ???

  override def addListener(listener: ObjectListener): Int = ???

  override def removeListener(listenerId: Int): Unit = ???

  override def createGroupAsync(groupName: String): RFuture[Void] = ???

  override def createGroupAsync(groupName: String, id: StreamMessageId): RFuture[Void] = ???

  override def removeGroupAsync(groupName: String): RFuture[Void] = ???

  override def removeConsumerAsync(groupName: String, consumerName: String): RFuture[lang.Long] = ???

  override def updateGroupMessageIdAsync(groupName: String, id: StreamMessageId): RFuture[Void] = ???

  override def ackAsync(groupName: String, ids: StreamMessageId*): RFuture[lang.Long] = ???

  override def getPendingInfoAsync(groupName: String): RFuture[PendingResult] = ???

  override def listPendingAsync(groupName: String): RFuture[PendingResult] = ???

  override def listPendingAsync(
    groupName: String,
    startId: StreamMessageId,
    endId: StreamMessageId,
    count: Int
  ): RFuture[util.List[PendingEntry]] = ???

  override def listPendingAsync(
    groupName: String,
    consumerName: String,
    startId: StreamMessageId,
    endId: StreamMessageId,
    count: Int
  ): RFuture[util.List[PendingEntry]] = ???

  override def pendingRangeAsync(
    groupName: String,
    startId: StreamMessageId,
    endId: StreamMessageId,
    count: Int
  ): RFuture[util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def pendingRangeAsync(
    groupName: String,
    consumerName: String,
    startId: StreamMessageId,
    endId: StreamMessageId,
    count: Int
  ): RFuture[util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def claimAsync(
    groupName: String,
    consumerName: String,
    idleTime: Long,
    idleTimeUnit: TimeUnit,
    ids: StreamMessageId*
  ): RFuture[util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def fastClaimAsync(
    groupName: String,
    consumerName: String,
    idleTime: Long,
    idleTimeUnit: TimeUnit,
    ids: StreamMessageId*
  ): RFuture[util.List[StreamMessageId]] = ???

  override def readGroupAsync(
    groupName: String,
    consumerName: String,
    ids: StreamMessageId*
  ): RFuture[util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readGroupAsync(
    groupName: String,
    consumerName: String,
    count: Int,
    ids: StreamMessageId*
  ): RFuture[util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readGroupAsync(
    groupName: String,
    consumerName: String,
    timeout: Long,
    unit: TimeUnit,
    ids: StreamMessageId*
  ): RFuture[util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readGroupAsync(
    groupName: String,
    consumerName: String,
    count: Int,
    timeout: Long,
    unit: TimeUnit,
    ids: StreamMessageId*
  ): RFuture[util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readGroupAsync(
    groupName: String,
    consumerName: String,
    id: StreamMessageId,
    nameToId: util.Map[String, StreamMessageId]
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readGroupAsync(
    groupName: String,
    consumerName: String,
    count: Int,
    id: StreamMessageId,
    nameToId: util.Map[String, StreamMessageId]
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readGroupAsync(
    groupName: String,
    consumerName: String,
    count: Int,
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    key2: String,
    id2: StreamMessageId
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readGroupAsync(
    groupName: String,
    consumerName: String,
    count: Int,
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    key2: String,
    id2: StreamMessageId,
    key3: String,
    id3: StreamMessageId
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readGroupAsync(
    groupName: String,
    consumerName: String,
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    nameToId: util.Map[String, StreamMessageId]
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readGroupAsync(
    groupName: String,
    consumerName: String,
    id: StreamMessageId,
    key2: String,
    id2: StreamMessageId
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readGroupAsync(
    groupName: String,
    consumerName: String,
    id: StreamMessageId,
    key2: String,
    id2: StreamMessageId,
    key3: String,
    id3: StreamMessageId
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readGroupAsync(
    groupName: String,
    consumerName: String,
    count: Int,
    id: StreamMessageId,
    key2: String,
    id2: StreamMessageId
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readGroupAsync(
    groupName: String,
    consumerName: String,
    count: Int,
    id: StreamMessageId,
    key2: String,
    id2: StreamMessageId,
    key3: String,
    id3: StreamMessageId
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readGroupAsync(
    groupName: String,
    consumerName: String,
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    key2: String,
    id2: StreamMessageId
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readGroupAsync(
    groupName: String,
    consumerName: String,
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    key2: String,
    id2: StreamMessageId,
    key3: String,
    id3: StreamMessageId
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def sizeAsync(): RFuture[lang.Long] = ???

  override def addAsync(key: K, value: V): RFuture[StreamMessageId] = ???

  override def addAsync(id: StreamMessageId, key: K, value: V): RFuture[Void] = ???

  override def addAsync(key: K, value: V, trimLen: Int, trimStrict: Boolean): RFuture[StreamMessageId] = ???

  override def addAsync(id: StreamMessageId, key: K, value: V, trimLen: Int, trimStrict: Boolean): RFuture[Void] = ???

  override def addAllAsync(entries: util.Map[K, V]): RFuture[StreamMessageId] = ???

  override def addAllAsync(id: StreamMessageId, entries: util.Map[K, V]): RFuture[Void] = ???

  override def addAllAsync(entries: util.Map[K, V], trimLen: Int, trimStrict: Boolean): RFuture[StreamMessageId] = ???

  override def addAllAsync(
    id: StreamMessageId,
    entries: util.Map[K, V],
    trimLen: Int,
    trimStrict: Boolean
  ): RFuture[Void] = ???

  override def readAsync(ids: StreamMessageId*): RFuture[util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readAsync(count: Int, ids: StreamMessageId*): RFuture[util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readAsync(
    timeout: Long,
    unit: TimeUnit,
    ids: StreamMessageId*
  ): RFuture[util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readAsync(
    count: Int,
    timeout: Long,
    unit: TimeUnit,
    ids: StreamMessageId*
  ): RFuture[util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def readAsync(
    id: StreamMessageId,
    name2: String,
    id2: StreamMessageId
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readAsync(
    id: StreamMessageId,
    name2: String,
    id2: StreamMessageId,
    name3: String,
    id3: StreamMessageId
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readAsync(
    id: StreamMessageId,
    nameToId: util.Map[String, StreamMessageId]
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readAsync(
    count: Int,
    id: StreamMessageId,
    name2: String,
    id2: StreamMessageId
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readAsync(
    count: Int,
    id: StreamMessageId,
    name2: String,
    id2: StreamMessageId,
    name3: String,
    id3: StreamMessageId
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readAsync(
    count: Int,
    id: StreamMessageId,
    nameToId: util.Map[String, StreamMessageId]
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readAsync(
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    name2: String,
    id2: StreamMessageId
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readAsync(
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    name2: String,
    id2: StreamMessageId,
    name3: String,
    id3: StreamMessageId
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readAsync(
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    nameToId: util.Map[String, StreamMessageId]
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readAsync(
    count: Int,
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    name2: String,
    id2: StreamMessageId
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readAsync(
    count: Int,
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    name2: String,
    id2: StreamMessageId,
    name3: String,
    id3: StreamMessageId
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def readAsync(
    count: Int,
    timeout: Long,
    unit: TimeUnit,
    id: StreamMessageId,
    nameToId: util.Map[String, StreamMessageId]
  ): RFuture[util.Map[String, util.Map[StreamMessageId, util.Map[K, V]]]] = ???

  override def rangeAsync(
    startId: StreamMessageId,
    endId: StreamMessageId
  ): RFuture[util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def rangeAsync(
    count: Int,
    startId: StreamMessageId,
    endId: StreamMessageId
  ): RFuture[util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def rangeReversedAsync(
    startId: StreamMessageId,
    endId: StreamMessageId
  ): RFuture[util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def rangeReversedAsync(
    count: Int,
    startId: StreamMessageId,
    endId: StreamMessageId
  ): RFuture[util.Map[StreamMessageId, util.Map[K, V]]] = ???

  override def removeAsync(ids: StreamMessageId*): RFuture[lang.Long] = ???

  override def trimAsync(size: Int): RFuture[lang.Long] = ???

  override def trimNonStrictAsync(size: Int): RFuture[lang.Long] = ???

  override def getInfoAsync: RFuture[StreamInfo[K, V]] = ???

  override def listGroupsAsync(): RFuture[util.List[StreamGroup]] = ???

  override def listConsumersAsync(groupName: String): RFuture[util.List[StreamConsumer]] = ???

  override def expireAsync(timeToLive: Long, timeUnit: TimeUnit): RFuture[lang.Boolean] = ???

  override def expireAtAsync(timestamp: Date): RFuture[lang.Boolean] = ???

  override def expireAtAsync(timestamp: Long): RFuture[lang.Boolean] = ???

  override def clearExpireAsync(): RFuture[lang.Boolean] = ???

  override def remainTimeToLiveAsync(): RFuture[lang.Long] = ???

  override def sizeInMemoryAsync(): RFuture[lang.Long] = ???

  override def restoreAsync(state: Array[Byte]): RFuture[Void] = ???

  override def restoreAsync(state: Array[Byte], timeToLive: Long, timeUnit: TimeUnit): RFuture[Void] = ???

  override def restoreAndReplaceAsync(state: Array[Byte]): RFuture[Void] = ???

  override def restoreAndReplaceAsync(state: Array[Byte], timeToLive: Long, timeUnit: TimeUnit): RFuture[Void] = ???

  override def dumpAsync(): RFuture[Array[Byte]] = ???

  override def touchAsync(): RFuture[lang.Boolean] = ???

  override def migrateAsync(host: String, port: Int, database: Int, timeout: Long): RFuture[Void] = ???

  override def copyAsync(host: String, port: Int, database: Int, timeout: Long): RFuture[Void] = ???

  override def moveAsync(database: Int): RFuture[lang.Boolean] = ???

  override def deleteAsync(): RFuture[lang.Boolean] = ???

  override def unlinkAsync(): RFuture[lang.Boolean] = ???

  override def renameAsync(newName: String): RFuture[Void] = ???

  override def renamenxAsync(newName: String): RFuture[lang.Boolean] = ???

  override def isExistsAsync: RFuture[lang.Boolean] = ???

  override def addListenerAsync(listener: ObjectListener): RFuture[Integer] = ???

  override def removeListenerAsync(listenerId: Int): RFuture[Void] = ???

  override def getIdleTime: lang.Long = ???

  override def getIdleTimeAsync: RFuture[lang.Long] = ???
}
