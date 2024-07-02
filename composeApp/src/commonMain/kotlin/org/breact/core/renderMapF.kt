package org.breact.core

data class MapRowRender<M,D>(
    val data: M,
    val key: Any,
    val shouldChange: (a: D, b: D) -> Boolean,
    val render: (v: FiberEvent<D>) -> Unit,
    val deps: D
)

private fun createMapRef(i: Any): StoreRef<Map<Any, MutableList<Fiber>>> {
    return storeRef(HashMap())
}

fun <M, C, D> renderMapF(
    data: M,

    initCache: C,
    hasValue: (v: M, c: C) -> Boolean,
    shouldChange: (a: D, b: D) -> Boolean,
    render: (row: M, c: C) -> MapRowRender<C,Any>,
    deps: D
): Fiber {
    return renderBaseFiber( true,shouldChange, {
        val mapRef = useBaseMemo(alawaysTrue as Equal<Any>,::createMapRef, 0)
        val oldMap = cloneMap(mapRef.get())
        val newMap = mutableMapOf<Any, MutableList<Fiber>>()
        hookLevelEffect(0f) {
            mapRef.set(newMap)
            null
        }
        val parentFiber= hookParentFiber()
        parentFiber.firstChild.set(null)
        parentFiber.lastChild.set(null)
        var beforeFiber:Fiber?=null
        var cache = initCache
        while (hasValue(data, cache)) {
            draftParentFiber()
            val child = render(data, cache)
            revertParentFiber()
            val oldFibers = oldMap[child.key]
            var oldFiber = oldFibers?.get(0)
            if (oldFiber != null) {
                oldFiber.changeRender(
                    child::shouldChange as Equal<Any?>,
                    child::render as SetValue<FiberEvent<Any?>>,
                    child.deps
                )
                oldFiber.before.set(beforeFiber)
                oldFibers!!.removeAt(0)
            } else {
                val tempFiber = createMapChild(
                    parentFiber.envModel,
                    parentFiber,
                    RenderDeps(
                        child::shouldChange as Equal<Any?>,
                        child.render,
                        FiberEventInit(child.deps)
                    ) as RenderDeps<Any?>,
                    false
                )
                tempFiber.subOps = hookTempOps().createSub()
                tempFiber.before.set(beforeFiber)
                oldFiber = tempFiber
            }
            val newFibers = newMap[child.key] ?: mutableListOf()
            if (newFibers.size > 0) {
                print("重复的key---重复${child.key} ${newFibers.size}次数")
            }
            newFibers.add(oldFiber)
            newMap[child.key] = newFibers

            if (beforeFiber != null) {
                beforeFiber.next.set(oldFiber)
            } else {
                parentFiber.firstChild.set(oldFiber)
            }
            parentFiber.lastChild.set(oldFiber)
            oldFiber.next.set(null)
            beforeFiber = oldFiber
        }
        oldMap.values.forEach { it ->
            it.forEach { it ->
                it.before.set(null)
                it.next.set(null)
               parentFiber. envModel.addDelect(it)
            }
        }
    }, deps)
}

private fun cloneMap(map: Map<Any, MutableList<Fiber>>): Map<Any, MutableList<Fiber>> {
    return map.toMap()
    val newMap = HashMap<Any, MutableList<Fiber>>()
    map.forEach {
        newMap[it.key] = it.value
    }
    return newMap
}
