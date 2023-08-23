package org.breact.core


data class OneProps<T>(
    val key: Any,
    val dom: VirtualDomNode<Any?>?,
    val render: (v: T) -> Unit,
    val dep: T
)

private data class OneCache(
    var key: Any?,
    var fiber: Fiber?
)

private fun initCache(i: Int): OneCache {
    return OneCache(null, null)
}

fun <M> renderOneF(
    createDom: VirtualDomOperator<Any?, Any?>?,
    data: M,
    outRender: (data: M) -> OneProps<Any?>,
    outDeps: Any?
): VirtualDomNode<Any?>? {
    return renderBaseFiber(createDom, true, {
        draftParentFiber()
        val child = outRender(data)
        revertParentFiber()
        var commitWork: (() -> Unit)? = null
        val pl = useParentFiber()
        val envModel = pl.first
        val parentFiber = pl.second

        val cache = useBaseMemoGet(::initCache, 0)()
        if (cache.key == child.key && cache.fiber != null) {
            cache.fiber!!.changeRender(RenderDeps(child.render, child.dep))
        } else {
            if (cache.fiber != null) {
                envModel.addDelect(cache.fiber!!)
            }
            val placeFiber = createOnChild(
                envModel,
                parentFiber, child.dom,
                RenderDeps(child.render, child.dep),
                false
            )
            commitWork = fun() {
                cache.key = child.key
                cache.fiber = placeFiber
            }
        }
        useBeforeAttrEffect {
            if (commitWork != null) {
                commitWork()
            }
            null
        }
    }, outDeps)
}