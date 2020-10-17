package io.kensu.redis_streams_zio.specs.adapters

import java.util.concurrent.TimeUnit

import org.redisson.api.redisnode.{ BaseRedisNodes, RedisNodes }
import org.redisson.api._
import org.redisson.client.codec.Codec
import org.redisson.config.Config

import scala.annotation.nowarn

class RedissonClientAdapter() extends RedissonClient {

  override def getTimeSeries[V](name: String): RTimeSeries[V] = ???

  override def getTimeSeries[V](name: String, codec: Codec): RTimeSeries[V] = ???

  override def getStream[K, V](name: String): RStream[K, V] = ???

  override def getStream[K, V](name: String, codec: Codec): RStream[K, V] = ???

  override def getRateLimiter(name: String): RRateLimiter = ???

  override def getBinaryStream(name: String): RBinaryStream = ???

  override def getGeo[V](name: String): RGeo[V] = ???

  override def getGeo[V](name: String, codec: Codec): RGeo[V] = ???

  override def getSetCache[V](name: String): RSetCache[V] = ???

  override def getSetCache[V](name: String, codec: Codec): RSetCache[V] = ???

  override def getMapCache[K, V](name: String, codec: Codec): RMapCache[K, V] = ???

  override def getMapCache[K, V](name: String, codec: Codec, options: MapOptions[K, V]): RMapCache[K, V] = ???

  override def getMapCache[K, V](name: String): RMapCache[K, V] = ???

  override def getMapCache[K, V](name: String, options: MapOptions[K, V]): RMapCache[K, V] = ???

  override def getBucket[V](name: String): RBucket[V] = ???

  override def getBucket[V](name: String, codec: Codec): RBucket[V] = ???

  override def getBuckets: RBuckets = ???

  override def getBuckets(codec: Codec): RBuckets = ???

  override def getHyperLogLog[V](name: String): RHyperLogLog[V] = ???

  override def getHyperLogLog[V](name: String, codec: Codec): RHyperLogLog[V] = ???

  override def getList[V](name: String): RList[V] = ???

  override def getList[V](name: String, codec: Codec): RList[V] = ???

  override def getListMultimap[K, V](name: String): RListMultimap[K, V] = ???

  override def getListMultimap[K, V](name: String, codec: Codec): RListMultimap[K, V] = ???

  override def getListMultimapCache[K, V](name: String): RListMultimapCache[K, V] = ???

  override def getListMultimapCache[K, V](name: String, codec: Codec): RListMultimapCache[K, V] = ???

  override def getLocalCachedMap[K, V](name: String, options: LocalCachedMapOptions[K, V]): RLocalCachedMap[K, V] = ???

  override def getLocalCachedMap[K, V](
    name: String,
    codec: Codec,
    options: LocalCachedMapOptions[K, V]
  ): RLocalCachedMap[K, V] = ???

  override def getMap[K, V](name: String): RMap[K, V] = ???

  override def getMap[K, V](name: String, options: MapOptions[K, V]): RMap[K, V] = ???

  override def getMap[K, V](name: String, codec: Codec): RMap[K, V] = ???

  override def getMap[K, V](name: String, codec: Codec, options: MapOptions[K, V]): RMap[K, V] = ???

  override def getSetMultimap[K, V](name: String): RSetMultimap[K, V] = ???

  override def getSetMultimap[K, V](name: String, codec: Codec): RSetMultimap[K, V] = ???

  override def getSetMultimapCache[K, V](name: String): RSetMultimapCache[K, V] = ???

  override def getSetMultimapCache[K, V](name: String, codec: Codec): RSetMultimapCache[K, V] = ???

  override def getSemaphore(name: String): RSemaphore = ???

  override def getPermitExpirableSemaphore(name: String): RPermitExpirableSemaphore = ???

  override def getLock(name: String): RLock = ???

  override def getMultiLock(locks: RLock*): RLock = ???

  override def getRedLock(locks: RLock*): RLock = ???

  override def getFairLock(name: String): RLock = ???

  override def getReadWriteLock(name: String): RReadWriteLock = ???

  override def getSet[V](name: String): RSet[V] = ???

  override def getSet[V](name: String, codec: Codec): RSet[V] = ???

  override def getSortedSet[V](name: String): RSortedSet[V] = ???

  override def getSortedSet[V](name: String, codec: Codec): RSortedSet[V] = ???

  override def getScoredSortedSet[V](name: String): RScoredSortedSet[V] = ???

  override def getScoredSortedSet[V](name: String, codec: Codec): RScoredSortedSet[V] = ???

  override def getLexSortedSet(name: String): RLexSortedSet = ???

  override def getTopic(name: String): RTopic = ???

  override def getTopic(name: String, codec: Codec): RTopic = ???

  override def getPatternTopic(pattern: String): RPatternTopic = ???

  override def getPatternTopic(pattern: String, codec: Codec): RPatternTopic = ???

  override def getQueue[V](name: String): RQueue[V] = ???

  override def getTransferQueue[V](name: String): RTransferQueue[V] = ???

  override def getTransferQueue[V](name: String, codec: Codec): RTransferQueue[V] = ???

  override def getDelayedQueue[V](destinationQueue: RQueue[V]): RDelayedQueue[V] = ???

  override def getQueue[V](name: String, codec: Codec): RQueue[V] = ???

  override def getRingBuffer[V](name: String): RRingBuffer[V] = ???

  override def getRingBuffer[V](name: String, codec: Codec): RRingBuffer[V] = ???

  override def getPriorityQueue[V](name: String): RPriorityQueue[V] = ???

  override def getPriorityQueue[V](name: String, codec: Codec): RPriorityQueue[V] = ???

  override def getPriorityBlockingQueue[V](name: String): RPriorityBlockingQueue[V] = ???

  override def getPriorityBlockingQueue[V](name: String, codec: Codec): RPriorityBlockingQueue[V] = ???

  override def getPriorityBlockingDeque[V](name: String): RPriorityBlockingDeque[V] = ???

  override def getPriorityBlockingDeque[V](name: String, codec: Codec): RPriorityBlockingDeque[V] = ???

  override def getPriorityDeque[V](name: String): RPriorityDeque[V] = ???

  override def getPriorityDeque[V](name: String, codec: Codec): RPriorityDeque[V] = ???

  override def getBlockingQueue[V](name: String): RBlockingQueue[V] = ???

  override def getBlockingQueue[V](name: String, codec: Codec): RBlockingQueue[V] = ???

  override def getBoundedBlockingQueue[V](name: String): RBoundedBlockingQueue[V] = ???

  override def getBoundedBlockingQueue[V](name: String, codec: Codec): RBoundedBlockingQueue[V] = ???

  override def getDeque[V](name: String): RDeque[V] = ???

  override def getDeque[V](name: String, codec: Codec): RDeque[V] = ???

  override def getBlockingDeque[V](name: String): RBlockingDeque[V] = ???

  override def getBlockingDeque[V](name: String, codec: Codec): RBlockingDeque[V] = ???

  override def getAtomicLong(name: String): RAtomicLong = ???

  override def getAtomicDouble(name: String): RAtomicDouble = ???

  override def getLongAdder(name: String): RLongAdder = ???

  override def getDoubleAdder(name: String): RDoubleAdder = ???

  override def getCountDownLatch(name: String): RCountDownLatch = ???

  override def getBitSet(name: String): RBitSet = ???

  override def getBloomFilter[V](name: String): RBloomFilter[V] = ???

  override def getBloomFilter[V](name: String, codec: Codec): RBloomFilter[V] = ???

  override def getScript: RScript = ???

  override def getScript(codec: Codec): RScript = ???

  override def getExecutorService(name: String): RScheduledExecutorService = ???

  override def getExecutorService(name: String, options: ExecutorOptions): RScheduledExecutorService = ???

  override def getExecutorService(name: String, codec: Codec): RScheduledExecutorService = ???

  override def getExecutorService(name: String, codec: Codec, options: ExecutorOptions): RScheduledExecutorService = ???

  override def getRemoteService: RRemoteService = ???

  override def getRemoteService(codec: Codec): RRemoteService = ???

  override def getRemoteService(name: String): RRemoteService = ???

  override def getRemoteService(name: String, codec: Codec): RRemoteService = ???

  override def createTransaction(options: TransactionOptions): RTransaction = ???

  override def createBatch(options: BatchOptions): RBatch = ???

  override def createBatch(): RBatch = ???

  override def getKeys: RKeys = ???

  override def getLiveObjectService: RLiveObjectService = ???

  override def shutdown(): Unit = ???

  override def shutdown(quietPeriod: Long, timeout: Long, unit: TimeUnit): Unit = ???

  override def getConfig: Config = ???

  override def getRedisNodes[T <: BaseRedisNodes](nodes: RedisNodes[T]): T = ???

  @nowarn
  override def getNodesGroup: NodesGroup[Node] = ???

  @nowarn
  override def getClusterNodesGroup: ClusterNodesGroup = ???

  override def isShutdown: Boolean = ???

  override def isShuttingDown: Boolean = ???

  override def getId: String = ???
}
