package org.breact.core

interface StoreRef<T> {
    fun set(v: T)
    fun get(): T
}

private class StoreRefImpl<T>(var value: T) : StoreRef<T> {
    override fun get(): T {
        return value
    }

    override fun set(v: T) {
        value = v
    }
}

fun <T> storeRef(value:T):StoreRef<T>{
    return StoreRefImpl(value)
}