package org.breact.core
typealias LayoutEffect = (funC: EmptyFun) -> Unit
private object cache{
    var wipFiber:Fiber?=null
    var allowWipFiber=false
    var tempOps:AbsTempOps<TempReal>?=null
    var effect:LayoutEffect?=null
}


fun hookAddFiber(fiber: Fiber?){
    cache.wipFiber=fiber
    cache.tempOps=fiber?.subOps
}

fun hookParentFiber(): Fiber {
    if(cache.allowWipFiber){
        return cache.wipFiber!!
    }
    throw Error("禁止在此处访问Fiber")
}

fun draftParentFiber(){
    cache.allowWipFiber=false
}
fun revertParentFiber(){
    cache.allowWipFiber=true
}

fun hookTempOps(): AbsTempOps<TempReal> {
    if(cache.tempOps!=null){
        return cache.tempOps!!
    }
    throw Error("未找到相应的tempOs")
}


fun hookBeginTempOps(op: TempOps<TempReal>): AbsTempOps<TempReal>? {
    val before = cache.tempOps
    cache.tempOps = op
    op.data.reset()
    return before
}
fun hookEndTempOps(op: AbsTempOps<TempReal>) {
    cache.tempOps = op
}

fun hookAddResult(vararg vs: Any) {
    if (cache.tempOps==null) {
        throw Error("必须在render中进行")
    }
    for(value in vs){
        cache.tempOps!!.addNode(value)
    }
}


fun hookAddEffect(effect: LayoutEffect?) {
    cache.effect = effect
}

fun effectLayout(func: EmptyFun) {
    if (cache.effect!=null) {
        cache.effect!!(func)
    } else {
        throw Error("请在effect中执行")
    }
}