@file:Suppress("FILE_NAME_MATCH_CLASS", "MatchingDeclarationName")

package org.cqfn.save.testutils

import okhttp3.mockwebserver.*
import org.junit.Assert.assertTrue
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.nio.charset.Charset
import java.util.concurrent.*

private typealias ResponsesMap = ConcurrentMap<String, BlockingQueue<MockResponse>>

/**
 * Queue dispatcher with additional logging
 */
class LoggingQueueDispatcher : Dispatcher() {
    /**
     * Map that matches path that is set with regex to queue of MockResponses
     */
    internal val responses: ResponsesMap = ConcurrentHashMap()

    /**
     * Map that matches path that is set with regex to default MockResponse
     */
    internal val defaultResponses: ConcurrentMap<String, MockResponse> = ConcurrentHashMap()
    private var failFastResponse: MockResponse = MockResponse().setResponseCode(HttpURLConnection.HTTP_NOT_FOUND)

    private fun getProperRegexKey(path: String?, setOfRegexes: Iterable<String>) = path?.let {
        val suitableRegexes = setOfRegexes.filter { regex -> Regex(regex).matches(path) }
        if (suitableRegexes.size > 1) {
            logger.warn("For path $path found more than one key from ResponsesMap: $suitableRegexes. Taking ${suitableRegexes.first()}")
        }
        suitableRegexes.firstOrNull()?.also { logger.debug("Path [$path] is matched with [$it]") }
    }

    @Suppress("UnsafeCallOnNullableType", "AVOID_NULL_CHECKS")
    override fun dispatch(request: RecordedRequest): MockResponse {
        val regexKeyForDefaultResponses = getProperRegexKey(request.path, defaultResponses.keys)
        val regexKeyForEnqueuedResponses = getProperRegexKey(request.path, responses.keys)
        val result = if (regexKeyForEnqueuedResponses != null) {
            if (regexKeyForDefaultResponses != null) {
                logger.debug("Default response for path $regexKeyForDefaultResponses that matches ${request.path} is ignored due to enqueued one ($regexKeyForEnqueuedResponses)")
            }
            responses[regexKeyForEnqueuedResponses]!!.take()
        } else if (regexKeyForDefaultResponses != null) {
            logger.debug("Default response [${defaultResponses[regexKeyForDefaultResponses]}] exists for path [$request.path].")
            defaultResponses[regexKeyForDefaultResponses]!!
        } else {
            logger.info("No response is present in queue with path [${request.path}].")
            return failFastResponse
        }

        if (result == deadLetter) {
            responses[regexKeyForEnqueuedResponses]!!.add(deadLetter)
        }
        return result.also { logger.info("Response [$result] was taken for request [$request].") }
    }

    /**
     * @param regexKey
     * @param defaultMockResponse
     */
    fun setDefaultResponseForPath(regexKey: String, defaultMockResponse: MockResponse) {
        defaultResponses[regexKey] = defaultMockResponse
    }

    override fun shutdown() {
        responses.values.forEach { it.add(deadLetter) }
    }

    override fun peek(): MockResponse = responses.values
        .filter { it.isNotEmpty() }
        .firstNotNullOfOrNull { it.peek() }
        ?: failFastResponse

    /**
     * @param regexKey that matches with path to method that should cause [response]
     * @param response that will be added to queue that matches [regexKey]
     */
    fun enqueueResponse(regexKey: String, response: MockResponse) {
        responses[regexKey]?.let {
            it.add(response)
            logger.info("Added [$response] into queue with path [$regexKey]. ")
            logger.debug("Now there are ${it.count()} responses.")
        }
            ?: run {
                responses[regexKey] = LinkedBlockingQueue<MockResponse>().apply { add(response) }
                logger.info("Added LinkedBlockingQueue for a new path [$regexKey] and put there [$response].")
            }
    }

    /**
     * Checks if there is any response in each queue
     */
    fun checkQueues() {
        responses.keys.forEach { checkQueue(it) }
    }

    private fun checkQueue(regexKey: String) = responses[regexKey]?.peek()?.let { mockResponse ->
        val errorMessage = "There is an enqueued response in the MockServer after a test has completed. " +
                "Enqueued body: ${mockResponse.getBody()?.readString(Charset.defaultCharset())}. " +
                "Path: $regexKey."
        assertTrue(errorMessage, mockResponse.getBody().let { it == null || it.size == 0L })
    }

    /**
     * Cleans responses queues
     */
    fun cleanup() {
        responses.clear()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(LoggingQueueDispatcher::class.java)
        private val deadLetter = MockResponse().apply {
            this.status = "HTTP/1.1 ${HttpURLConnection.HTTP_UNAVAILABLE} shutting down"
        }
    }
}

/**
 * @param regexKey that determines which queue is to store enqueued [response]
 * @param response response to enqueue
 * @throws IllegalStateException
 */
fun MockWebServer.enqueue(regexKey: String, response: MockResponse) {
    if (dispatcher is LoggingQueueDispatcher) {
        (dispatcher as LoggingQueueDispatcher).enqueueResponse(regexKey, response.clone())
    } else {
        throw IllegalStateException("dispatcher type should be LoggingQueueDispatcher")
    }
}

/**
 * Cleans `responses` map
 */
fun MockWebServer.cleanup() {
    (dispatcher as LoggingQueueDispatcher).cleanup()
}

/**
 * Checks if there are any MockResponses left in any path queue
 */
fun MockWebServer.checkQueues() {
    (dispatcher as LoggingQueueDispatcher).checkQueues()
}

/**
 * Sets default MockResponse for certain path
 *
 * @param regexKey
 * @param response
 */
fun MockWebServer.setDefaultResponseForPath(regexKey: String, response: MockResponse) =
        (dispatcher as LoggingQueueDispatcher).setDefaultResponseForPath(regexKey, response)

/**
 * Creates MockWebServer with LoggingQueueDispatcher
 *
 * @return MockWebServer used for testing
 */
fun createMockWebServer() = MockWebServer().apply {
    dispatcher = LoggingQueueDispatcher()
}
