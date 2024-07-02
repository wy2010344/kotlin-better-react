package org.breact.radical
import org.breact.core.MemoEvent
import org.breact.helper.useMemo

fun <V,T : Any?> V.useMemo(
    callback: (MemoEvent<T,V>) -> T
): T {
    return useMemo(callback,this)
}
