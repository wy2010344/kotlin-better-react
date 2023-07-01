package helper

import org.breact.core.StoreRef
import org.breact.core.quote
import org.breact.core.storeRef
import org.breact.core.useMemoGet

fun <T, V> useMemo(effect: (deps: V) -> T, deps: V): T {
    return useMemoGet(effect, deps)()
}

fun <M, T> useRef(init: M, trans: (m: M) -> T): StoreRef<T> {
    return useMemo({
        val ref = storeRef(trans(init))
        ref
    }, 1)
}

fun <T> useRef(init:T):StoreRef<T>{
    return useRef(init, ::quote)
}

fun <T> useRef():StoreRef<T?>{
    return useRef(null)
}

fun <T> useRefFun(init:(vararg:Any?)->T):StoreRef<T>{
    return useRef(null,init)
}

fun <T> useAlaway(init:T):()->T{
    return useMemoGet(::quote,init)
}