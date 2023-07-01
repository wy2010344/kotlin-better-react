package helper

fun <T:(vararg:Any?)->Any> useEvent(callback:T):T{
    val get= useAlaway(callback)
    return useCallback({
        get().invoke(it)
    },1) as T
}