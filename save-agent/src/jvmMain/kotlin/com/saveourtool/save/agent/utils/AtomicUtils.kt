package com.saveourtool.save.agent.utils

actual typealias AtomicLong = java.util.concurrent.atomic.AtomicLong

@Suppress("USE_DATA_CLASS")
actual class GenericAtomicReference<T> actual constructor(valueToStore: T) {
    private val holder: java.util.concurrent.atomic.AtomicReference<T> = java.util.concurrent.atomic.AtomicReference(valueToStore)
    actual fun get(): T = holder.get()
    actual fun set(newValue: T) {
        holder.set(newValue)
    }
}