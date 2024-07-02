package org.breact.core


 enum class EffectTag {
    UPDATE,
    CREATE,
    EMPTY
}


sealed class FiberEvent<D>(
    val trigger: D,
    val isInit:Boolean
)
class FiberEventInit<D>(
    trigger: D):FiberEvent<D>(trigger,true)
class FiberEventRun<D>(
    trigger: D,
    val beforeTrigger:D):FiberEvent<D>(trigger,false)


data class RenderDeps<T>(
    val shouldChange: (a: T, b: T) -> Boolean,
    val render:SetValue<FiberEvent<T>>,
    val event: FiberEvent<T>
)

private fun didCommitTag(old: EffectTag) = EffectTag.EMPTY

typealias HookValueSet<F, T> = (F) -> Unit

 sealed class HookEffect<V,T>(
     val isInit: Boolean,
    val level:Float,
    val shouldChange:(a:T,b:T)->Boolean,
    val deps: T
){
     var destroy: ((EffectDestroyEvent<V,T>) -> Unit)?=null
     var value:V=null!!
 }

class HookEffectInit<V,T>(
    level:Float,
    shouldChange:(a:T,b:T)->Boolean,
     deps: T
):HookEffect<V,T>(
    true,
    level,
    shouldChange,
    deps
)
class HookEffectRun<V,T>(
    level:Float,
    shouldChange:(a:T,b:T)->Boolean,
    deps: T
):HookEffect<V,T>(
false,
level,
shouldChange,
deps
)


sealed class EffectDestroyEvent<V,T>(
    val isDestroy:Boolean,
   val value:V,
   val beforeIsInit:Boolean,
    val beforeTrigger:T,
    val setRealTime:EmptyFun
)

class EffectDestroyEventTrue<V,T>(
    value:V,
    beforeIsInit: Boolean,
    beforeTrigger: T,
    setRealTime: EmptyFun
):EffectDestroyEvent<V,T>(
    true,
    value,
    beforeIsInit,
    beforeTrigger,
    setRealTime
)
class EffectDestroyEventFalse<V,T>(
    val trigger:T,
    value:V,
    beforeIsInit: Boolean,
    beforeTrigger: T,
    setRealTime: EmptyFun
):EffectDestroyEvent<V,T>(
    false,
    value,
    beforeIsInit,
    beforeTrigger,
    setRealTime
)


 data class HookMemo<T,D>(
    var value: D,
    var deps: T,
    val shouldChange: (a: T, b: T) -> Boolean,
)
class Fiber(
    val envModel: EnvModel,
    val parent: Fiber?,
    val before: StoreRef<Fiber?>,
    val next: StoreRef<Fiber?>,
    rd: RenderDeps<Any?>,
    dynamicChild: Boolean
) {
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
    var contextProvider: MutableMap<Any, ValueCenter<Any?>>? = null
    var hookEffects: MutableList<StoreRef<HookEffect<Any?,Any?>>>?=null
    var hookMemo: MutableList<StoreRef<HookMemo<Any?, Any?>>>? = null

    val effectTag: StoreRef<EffectTag> = envModel.createChangeAtom(EffectTag.CREATE, ::didCommitTag)
    var destroyed = false
    private val renderDeps: StoreRef<RenderDeps<Any?>> = envModel.createChangeAtom(rd)
    fun <D> changeRender(
        shouldChange:(a:D,b:D)->Boolean,
        render:(e:FiberEvent<D>)->Unit,
        deps:D) {
        val rd = renderDeps.get()
        if (rd.shouldChange(rd.event.trigger, deps)) {
            renderDeps.set(
                RenderDeps<D>(
                    shouldChange,
                    render,
                    FiberEventRun<D>(deps,rd.event.trigger as D)
                ) as RenderDeps<Any?>)
            effectTag.set(EffectTag.UPDATE)
        }
    }
    lateinit var subOps:AbsTempOps<TempReal>
    fun render() {
        val rd = renderDeps.get()
        subOps.data.reset()
        rd.render(rd.event)
    }
}

 fun createFix(
    envModel: EnvModel,
    parent: Fiber?,
    rd: RenderDeps<Any?>,
    dynamicChild: Boolean = false
): Fiber {
    return Fiber(
        envModel, parent,
        storeRef(null),
        storeRef(null),
        rd,
        dynamicChild
    )
}

 fun createMapChild(
    envModel: EnvModel,
    parent: Fiber?,
    rd: RenderDeps<Any?>,
    dynamicChild: Boolean
): Fiber {
    return Fiber(
        envModel, parent,
        envModel.createChangeAtom(null),
        envModel.createChangeAtom(null),
        rd,
        dynamicChild
    )
}

 fun  deepTravelFiber(call: (Fiber) -> Unit) =
    fun(fiber: Fiber): Fiber? {
        call(fiber)
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


