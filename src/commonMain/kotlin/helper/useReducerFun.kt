package helper

import org.breact.core.ReducerFun
import org.breact.core.ReducerResult
import org.breact.core.useReducer

fun <T,F> useReducerFun(reducer:ReducerFun<T,F>,init:(vararg:Any?)->T): ReducerResult<F, T> {
    return useReducer(reducer,null,init)
}