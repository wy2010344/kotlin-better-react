package org.breact.core


private object hookIndex{
    var effect=0
    var memo=0
    var beforeFiber:Fiber?=null
}
internal fun updateFunctionComponent(fiber: Fiber) {
    revertParentFiber()
    hookAddFiber(fiber)
    hookIndex.effect=0
    hookIndex.memo=0
    hookIndex.beforeFiber=null
    fiber.render()
    draftParentFiber()
    hookAddFiber(null)
}

sealed class EffectEvent<V,T>(
    val isInit:Boolean,
    val trigger:T,
   val setRealTime:EmptyFun
)

class EffectEventTrue<V,T>(
     trigger:T,
     setRealTime:EmptyFun
):EffectEvent<V,T>(true,trigger,setRealTime)

class EffectEventFalse<V,T>(
    val beforeTrigger:T,
    val value:V,
    trigger:T,
    setRealTime:EmptyFun
):EffectEvent<V,T>(false,trigger,setRealTime)

typealias EffectDestroy<V,T> = ((EffectDestroyEvent<V,T>)->Unit)?
typealias EffectResult<V,T> = Pair<V,EffectDestroy<V,T>>?

fun <V,T> useLevelEffect(
    level: Float,
    shouldUpdate: (a: T, b: T) -> Boolean,
    effect: (EffectEvent<V,T>)->EffectResult<V,T>,
    deps: T) {
    val parentFiber = hookParentFiber()
    val isInit=parentFiber.effectTag.get() == EffectTag.CREATE
    if (isInit) {
        val hookEffects = parentFiber.hookEffects ?: mutableListOf()
        parentFiber.hookEffects = hookEffects
        val state= HookEffectInit<V,T>(level,shouldUpdate,deps)
        val hookEffect=parentFiber.envModel.createChangeAtom(state as HookEffect<Any?,Any?>)
        hookEffects.add(hookEffect)
        parentFiber.envModel.updateEffect(level){
            hookAddEffect(parentFiber.envModel.layoutEffect)
            val out=effect(EffectEventTrue(deps,parentFiber.envModel.setRealTime))
            hookAddEffect(null)
            if(out!=null){
                state.value=out.first
                state.destroy=out.second
            }
        }
    } else {
        val hookEffects = parentFiber.hookEffects ?: throw Error("原组件上不存在")
        val index=hookIndex.effect
        val hookEffect=hookEffects[index]
        val state=hookEffect.get() as HookEffect<V,T>
        hookIndex.effect=index+1
        if(state.shouldChange(state.deps,deps)){
            val newState=HookEffectRun<V,T>(level,shouldUpdate,deps)
            hookEffect.set(newState as HookEffect<Any?, Any?>)
            parentFiber.envModel.updateEffect(level){
                val destroy=state.destroy
                if(destroy!=null){
                    hookAddEffect(parentFiber.envModel.layoutEffect)
                    destroy(EffectDestroyEventFalse(
                        trigger = deps,
                        value = state.value,
                        beforeTrigger = state.deps,
                        beforeIsInit = state.isInit,
                        setRealTime = parentFiber.envModel.setRealTime
                    ))
                    hookAddEffect(null)
                }
            }
        }
    }
}

fun hookLevelEffect(level:Float,effect:EmptyFun){
    val parentFiber= hookParentFiber()
    parentFiber.envModel.updateEffect(level,effect)
}

sealed class MemoEvent<M,T>(
    val isInit: Boolean,
    val trigger: T,
)

class MemoEventTrue<M,T>(
    trigger: T,
):MemoEvent<M,T>(true, trigger)

class MemoEventFalse<M,T>(
    trigger: T,
    beforeTrigger: T,
    beforeValue:M
):MemoEvent<M,T>(false,trigger)

fun <T,V> useBaseMemo(
    shouldUpdate: Equal<T>,
    effect:(MemoEvent<V,T>)->V,
    deps:T
):V{
    val parentFiber= hookParentFiber()
    val isInit=parentFiber.effectTag.get()==EffectTag.CREATE
    if(isInit){
        val hookMemo=parentFiber.hookMemo ?: mutableListOf()
        parentFiber.hookMemo=hookMemo

        draftParentFiber()
        val state=HookMemo(
            value = effect(MemoEventTrue(deps)),
            deps = deps,
            shouldChange = shouldUpdate
        )
        revertParentFiber()

        val hook=parentFiber.envModel.createChangeAtom(state) as StoreRef<HookMemo<Any?, Any?>>
        hookMemo.add(hook)
        return state.value
    }else{
    val hookMemo=parentFiber.hookMemo ?: throw Error("原组件上不存在memos")
        val index=hookIndex.memo
        val hook=hookMemo[index]?:throw Error("出现更多的memo")
        val state=hook.get() as HookMemo<T,V>
        if(state.shouldChange(state.deps,deps)){
            draftParentFiber()
            val newState=HookMemo(
                value = effect(MemoEventFalse(deps,state.deps,state.value)),
                deps=deps,
                shouldChange = shouldUpdate
            )
            revertParentFiber()
            hook.set(newState as HookMemo<Any?, Any?>)
            return newState.value
        }
        return state.value
    }
}

internal fun <T> renderBaseFiber(
    dynamicChild: Boolean,
    shouldUpdate: (a: T, b: T) -> Boolean,
    render: (v: FiberEvent<T>) -> Unit,
    deps: T
): Fiber{
    val parentFiber= hookParentFiber()
    var currentFiber:Fiber
    val isInit=parentFiber.effectTag.get()==EffectTag.CREATE
    if (isInit) {
        currentFiber= createFix(
            parentFiber.envModel,
            parentFiber,
            RenderDeps<T>(
                shouldUpdate,
                render,
                event = FiberEventInit(deps)
            ) as RenderDeps<Any?>,
            dynamicChild
        )
        currentFiber.subOps= hookTempOps().createSub()
        currentFiber.before.set(hookIndex.beforeFiber)
        if(hookIndex.beforeFiber!=null){
            hookIndex.beforeFiber!!.next.set(currentFiber)
        }else{
            parentFiber.firstChild.set(currentFiber)
        }
        hookIndex.beforeFiber=currentFiber
    } else {
        var oldFiber: Fiber? = null
        if (hookIndex.beforeFiber != null) {
            oldFiber = hookIndex.beforeFiber!!.next.get()
        }
        oldFiber = parentFiber.firstChild.get() ?: throw Error("非预期地出现了Fiber")
        currentFiber = oldFiber
        hookIndex.beforeFiber=currentFiber
        currentFiber.changeRender(shouldUpdate,render,deps)
    }
    hookTempOps().addNode(currentFiber.subOps)
    return currentFiber
}

fun <T> renderFiber(
    shouldUpdate: (a: T, b: T) -> Boolean,
    render: (v: FiberEvent<T>) -> Unit,
    deps: T
): Fiber {
    return renderBaseFiber(false, shouldUpdate, render, deps)
}

interface Context<T> {
    fun hookProvider(v: T)
    fun <M> useSelector(getValue: (v: T) -> M, shouldUpdate: ((a: M, b: M) -> Boolean)? = null): M
    fun useConsumer(): T
}

fun <T> createContext(v: T): Context<T> {
    return ContextFactory(v)
}

private var contextUid = 0

internal class ContextFactory<T>(
    out: T
) : Context<T> {
    val id = contextUid++
    private val defaultContext=valueCenterOf(out)


    override fun hookProvider(v: T) {
        val parentFiber = hookParentFiber()
        val map = parentFiber.contextProvider ?: mutableMapOf()
        parentFiber.contextProvider = map
        var hook = map.get(this as ContextFactory<Any?>)
        if (hook == null) {
            hook = valueCenterOf(v)
            map[this] = hook
        }
        hook.set(v)
    }

    private fun findProviderFiber(_fiber: Fiber): Fiber? {
        var fiber: Fiber? = _fiber
        while (fiber != null) {
            if (fiber.contextProvider != null) {

                val providers = fiber.contextProvider!!
                if (providers.contains(this as ContextFactory<Any?>)) {
                    return fiber
                }
            }
            fiber = fiber.parent
        }
        return null
    }

    override fun <M> useSelector(
        getValue: (v: T) -> M,
        shouldUpdate: ((a: M, b: M) -> Boolean)?
    ): M {
        val shouldChange=shouldUpdate?: ::simpleNotEqual
        val parentFiber = hookParentFiber()
        val providerFiber=this.findProviderFiber(parentFiber)
        val context=(providerFiber?.contextProvider?.get(this)?:this.defaultContext) as ValueCenter<T>
        val thisValue=getValue(context.get())
        val notSelf=providerFiber!=parentFiber
        useLevelEffect<Any?,List<Any?>>(0f,::arrayNotEqual,{
            Pair(
                null,
                context.subscribe{ a, _ ->
                    val m = getValue(a)
                    if (notSelf && shouldChange(thisValue, m)) {
                        parentFiber.effectTag.set(EffectTag.UPDATE)
                    }
                } as EffectDestroy<Any?,List<Any?>>
            )
        }, listOf(context,getValue,shouldUpdate,notSelf))
        return thisValue
    }

    override fun useConsumer(): T {
        return useSelector(::quote)
    }
}
