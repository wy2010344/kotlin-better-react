package org.breact.radical
import org.breact.core.useBaseMemoGet
import org.breact.helper.useMemo
val a = 9

fun <V, T> V.useMemoGet(
    callback: (V) -> T
): () -> T {
    return useBaseMemoGet(callback, this)
}

fun <V,T : Any?> V.useMemo(
    callback: (V) -> T
): T {
    return useMemo(callback,this)
}