package helper

import org.breact.core.ReducerResult
import org.breact.core.quote



private fun <T> change(old:T,action:T):T{
    return action
}

fun <T,M> useChange(v:M,trans:(v:M)->T): ReducerResult<T,T> {
    return useReducer(::change,v,trans)
}
fun <T> useChange(v:T): ReducerResult<T,T> {
    return useChange(v,::quote)
}

fun <T> useChange():ReducerResult<T?,T?>{
    return useChange(null)
}

fun <T> useChangeFun(init:(vararg :Any?)->T):ReducerResult<T,T>{
    return useChange(null,init)
}

sealed class StateAction<T>
data class StatePure<T>(val value:T):StateAction<T>()
data class StateReduce<T>(val reducer:(T)->T):StateAction<T>()

private fun <T> stateReducer(old:T,action:StateAction<T>):T {
    return when(action){
        is StatePure<T> -> action.value
        is StateReduce<T> -> action.reducer(old)
    }
}
fun <T> useState(init:T): ReducerResult<StateAction<T>, T> {
    return useReducer(::stateReducer,init)
}

fun <T> useState(init:(vararg :Any?)->T): ReducerResult<StateAction<T>, T> {
    return useReducer(::stateReducer,null,init)
}

fun <T,M> useState(init:M,trans: (v: M) -> T): ReducerResult<StateAction<T>, T> {
    return useReducer(::stateReducer,init,trans)
}