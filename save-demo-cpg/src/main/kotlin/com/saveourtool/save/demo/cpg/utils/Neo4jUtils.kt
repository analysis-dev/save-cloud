/**
 * This class contains util methods for NEO4J
 */

package com.saveourtool.save.demo.cpg.utils

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import org.neo4j.driver.exceptions.AuthenticationException
import org.neo4j.ogm.config.Configuration
import org.neo4j.ogm.exception.ConnectionException
import org.neo4j.ogm.session.Session
import org.neo4j.ogm.session.SessionFactory
import java.lang.IllegalArgumentException
import kotlin.jvm.Throws


typealias SessionWithFactory = Pair<Session, SessionFactory>

/**
 * Try to connect Neo4j database using OGM or returns [ConnectionException]
 *
 * @param uri
 * @param username
 * @param password
 * @param packageName
 */
@Throws(IllegalArgumentException::class)
fun tryConnect(
    uri: String,
    username: String,
    password: String,
    packageName: String
): Either<ConnectionException, SessionWithFactory> = try {
    val configuration =
        Configuration.Builder()
            .uri(uri)
            .autoIndex("none")
            .credentials(username, password)
            .verifyConnection(true)
            .build()
    val sessionFactory = SessionFactory(configuration, packageName)
    val session = requireNotNull(sessionFactory.openSession()) {
        "Failed to open session"
    }
    (session to sessionFactory).right()
} catch (ex: ConnectionException) {
    ex.left()
} catch (ex: AuthenticationException) {
    throw IllegalArgumentException("Unable to connect to $uri, wrong username/password of the database", ex)
}

/**
 * Invoke [function] on [Session] and close it with [SessionFactory] after it
 *
 * @param function on [Session]
 * @return result of [function]
 */
fun <R> SessionWithFactory.use(function: (Session) -> R): R {
    try {
        return function(first)
    } finally {
        first.clear()
        second.close()
    }
}

/**
 * @param maxRetry
 * @param timeoutMills sleep between tries
 * @param supplier returns a result or an exception if it's failed
 * @return a result or an exception if we failed to get some result after [maxRetry] tries
 */
fun <R, E : Throwable> retry(
    maxRetry: Int,
    timeoutMills: Long,
    supplier: () -> Either<E, R>
): R {
    return generateSequence(supplier) { previousResult ->
        if (previousResult.isLeft()) {
            Thread.sleep(timeoutMills)
            supplier()
        } else {
            previousResult
        }
    }
        .withIndex()
        .first { (attempt, result) ->
            result.isRight() || attempt >= maxRetry
        }
        .value
        .getOrHandle {
            throw it
        }
}