package helper

import org.breact.core.ReducerResult

private fun increase(old:Int,vararg :Any?):Int{
    return old+1
}
fun useVersion(init:Int=0): ReducerResult<Any?, Int> {
    return useReducer(::increase,init)
}