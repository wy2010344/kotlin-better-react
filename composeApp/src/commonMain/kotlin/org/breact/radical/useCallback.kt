package org.breact.radical

import org.breact.core.AnyFun
import org.breact.helper.useCallback

fun <T:AnyFun> Any.useCallback(callback:T):T{
    return useCallback<T>(callback,this)
}