package org.breact.helper

import org.breact.core.ReducerFun
import org.breact.core.ReducerResult
import org.breact.core.quote
import org.breact.core.useBaseReducer

fun <F,M,T> useReducer(
    reducer: ReducerFun<T, F>,
    init: M,
    initFun: (m: M) -> T,
): ReducerResult<F, T> {
    return useBaseReducer(reducer,init,initFun)
}
fun <F, T> useReducer(
    reducer: ReducerFun<T, F>, init: T
): ReducerResult<F, T> {
    return useBaseReducer(reducer, init, ::quote)
}
fun <T,F> useReducerFun(reducer:ReducerFun<T,F>,init:(vararg:Any?)->T): ReducerResult<F, T> {
    return useReducer(reducer,null,init)
}