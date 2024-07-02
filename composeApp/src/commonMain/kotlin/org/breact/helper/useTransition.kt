package org.breact.helper

import org.breact.core.EmptyFun
import org.breact.core.startTransition

fun useTransition(): Pair<Boolean, (EmptyFun) -> Unit> {
    val pending= useChange(false)
    return Pair(pending.value,fun(callback:EmptyFun){
        pending.dispatch(true)
        startTransition {
            callback()
            pending.dispatch(false)
        }
    })
}