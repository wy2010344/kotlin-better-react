package org.breact.helper

import org.breact.core.AnyFun

fun <T:AnyFun> useCallback(callback:T,deps:Any):T{
    return useMemo({
        callback
    },deps)
}