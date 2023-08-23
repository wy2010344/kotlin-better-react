package org.breact.core

interface MapTranslate<M, T> {
    fun sizeOf(m: M): Int
    fun getAt(m: M, i: Int): T
}

data class MapRowRender<D>(
    val key: Any,
    val dom: VirtualDomNode<Any?>?,
    val render: (v: D) -> Unit,
    val deps: D
)

private fun createMapRef(i: Int): StoreRef<Map<Any, MutableList<Fiber>>> {
    return storeRef(HashMap())
}

fun <M, K, D> renderMapF(
    createDom: VirtualDomOperator<Any?, Any?>?,
    data: M,
    mapTranslate: MapTranslate<M, K>,
    render: (row: K, i: Int) -> MapRowRender<D>,
    deps: Any?
): VirtualDomNode<Any?>? {
    return renderBaseFiber(createDom, true, {
        val mapRef = useBaseMemoGet(::createMapRef, 0)()
        val oldMap = cloneMap(mapRef.get())
        val newMap = HashMap<Any, MutableList<Fiber>>()
        useBeforeAttrEffect {
            mapRef.set(newMap)
            null
        }
        var beforeFiber: Fiber? = null
        val pl = useParentFiber()
        val envModel = pl.first
        val parentFiber = pl.second

        parentFiber.firstChild.set(null)
        parentFiber.lastChild.set(null)
        val maxSize = mapTranslate.sizeOf(data)
        var i = 0
        while (i < maxSize) {
            val v = mapTranslate.getAt(data, i)
            draftParentFiber()
            val child = render(v, i)
            revertParentFiber()
            val oldFibers = oldMap[child.key]
            var oldFiber = oldFibers?.get(0)
            if (oldFiber != null) {
                oldFiber.changeRender(
                    RenderDeps(
                        child.render,
                        child.deps
                    ) as RenderDeps<Any?>
                )
                oldFiber.before.set(beforeFiber)
                oldFibers!!.removeAt(0)
            } else {
                val tempFiber = createMapChild(
                    envModel, parentFiber, child.dom,
                    RenderDeps(
                        child.render,
                        child.deps
                    ) as RenderDeps<Any?>,
                    false
                )
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
            i++
        }
        oldMap.values.forEach { it ->
            it.forEach { it ->
                it.before.set(null)
                it.next.set(null)
                envModel.addDelect(it)
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
