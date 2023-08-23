package org.breact.core


internal enum class EffectTag {
    UPDATE,
    CREATE,
    EMPTY
}

internal data class RenderDeps<T : Any?>(
    val render: (deps: T) -> Unit,
    val deps: T
)

private fun didCommitTag(old: EffectTag) = EffectTag.EMPTY

typealias HookValueSet<F, T> = (F) -> Unit

internal data class HookValue<F, T>(
    val value: StoreRef<T>,
    val set: HookValueSet<F, T>,
    val reducer:Any?,
    val init:Any?,
    val initFun:Any?
)

internal data class HookEffect<T>(
    var deps: T,
    var destroy: ((T) -> Unit)?
)

internal data class HookMemo<D, T>(
    var value: D,
    var deps: T,
)

internal data class HookMemoValue<D, T>(
    val get: () -> D,
    val value: StoreRef<HookMemo<D, T>>
)

internal class Fiber(
    envModel: EnvModel,
    val parent: Fiber?,
    val dom: VirtualDomNode<Any?>?,
    val before: StoreRef<Fiber?>,
    val next: StoreRef<Fiber?>,
    rd: RenderDeps<Any?>,
    dynamicChild: Boolean
) {

    var contextProvider: MutableMap<ContextFactory<Any?>, ContextProvider<Any?>>? = null


    var hookValue: MutableList<HookValue<Any?, Any?>>? = null

    var hookEffects: MutableMap<EFFECT_LEVEL, MutableList<StoreRef<HookEffect<Any?>>>>? = null

    var hookMemo: MutableList<HookMemoValue<Any?, Any?>>? = null
    var hookContextCosumer: MutableList<ContextListener<Any?, Any?>>? = null

    val firstChild: StoreRef<Fiber?>
    val lastChild: StoreRef<Fiber?>

    init {
        if (dynamicChild) {
            firstChild = envModel.createChangeAtom(null)
            lastChild = envModel.createChangeAtom(null)
        } else {
            firstChild = storeRef(null)
            lastChild = storeRef(null)
        }
    }

    val effectTag: StoreRef<EffectTag> = envModel.createChangeAtom(EffectTag.CREATE, ::didCommitTag)
    var destroyed = false
    private val renderDeps: StoreRef<RenderDeps<Any?>> = envModel.createChangeAtom(rd)
    fun changeRender(nrd: RenderDeps<Any?>) {
        val rd = renderDeps.get()
        if (depsNotEqualWithEmpty(rd.deps, nrd.deps)) {
            renderDeps.set(nrd)
            effectTag.set(EffectTag.UPDATE)
        }
    }

    fun render() {
        val rd = renderDeps.get()
        rd.render(rd.deps)
    }
}

internal fun createFix(
    envModel: EnvModel,
    parent: Fiber?,
    dom: VirtualDomNode<Any?>?,
    rd: RenderDeps<Any?>,
    dynamicChild: Boolean = false
): Fiber {
    return Fiber(
        envModel, parent, dom,
        storeRef(null),
        storeRef(null),
        rd,
        dynamicChild
    )
}

internal fun createMapChild(
    envModel: EnvModel,
    parent: Fiber?,
    dom: VirtualDomNode<Any?>?,
    rd: RenderDeps<Any?>,
    dynamicChild: Boolean
): Fiber {
    return Fiber(
        envModel, parent, dom,
        envModel.createChangeAtom(null),
        envModel.createChangeAtom(null),
        rd,
        dynamicChild
    )
}

internal fun createOnChild(
    envModel: EnvModel,
    parent: Fiber?,
    dom: VirtualDomNode<Any?>?,
    rd: RenderDeps<Any?>,
    dynamicChild: Boolean
): Fiber {
    return Fiber(
        envModel, parent, dom,
        emptyPlace,
        emptyPlace,
        rd,
        dynamicChild
    )
}

private val emptyPlace = storeRef<Fiber?>(null)
internal fun <T> deepTravelFiber(call: (Fiber, value: T) -> Unit) =
    fun(fiber: Fiber, v: T): Fiber? {
        call(fiber, v)
        val child = fiber.firstChild.get()
        if (child != null) {
            return child
        }
        var nextFiber: Fiber? = fiber
        while (nextFiber != null) {
            val next = nextFiber.next.get()
            if (next != null) {
                return next
            }
            nextFiber = nextFiber.parent
        }
        return null
    }


