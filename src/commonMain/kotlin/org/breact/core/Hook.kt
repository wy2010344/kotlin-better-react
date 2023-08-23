package org.breact.core


private var allowWipFiber = true
fun draftParentFiber() {
    allowWipFiber = false
}

fun revertParentFiber() {
    allowWipFiber = true
}

private var wipFiber: Pair<EnvModel, Fiber>? = null
internal fun useParentFiber(): Pair<EnvModel, Fiber> {
    if (allowWipFiber) {
        return wipFiber ?: throw Error("此处禁止访问fiber")
    }
    throw Error("禁止在此处访问Fiber")
}

private var hookIndex_state = 0
private val hookIndex_effect = mutableMapOf<EFFECT_LEVEL, Int>(
    EFFECT_LEVEL.first to 0, EFFECT_LEVEL.second to 0, EFFECT_LEVEL.third to 0
)
private var hookIndex_memo = 0
private var hookIndex_beforeFiber: Fiber? = null
private var hookIndex_cusomer = 0

internal fun updateFunctionComponent(envModel: EnvModel, fiber: Fiber) {
    wipFiber = Pair(envModel, fiber)
    hookIndex_state = 0
    hookIndex_effect[EFFECT_LEVEL.first] = 0
    hookIndex_effect[EFFECT_LEVEL.second] = 0
    hookIndex_effect[EFFECT_LEVEL.third] = 0
    hookIndex_memo = 0
    hookIndex_cusomer = 0
    hookIndex_beforeFiber = null
    fiber.render()
    wipFiber = null
}

typealias ReducerFun<T, F> = (T, F) -> T

internal fun <T, F> buildSetValue(
    atom: StoreRef<T>, envModel: EnvModel, parentFiber: Fiber, reducer: ReducerFun<T, F>
): HookValueSet<F, T> {
    return fun(temp: F) {
        if (parentFiber.destroyed) {
            print("更新已经销毁的fiber")
            return
        }
        envModel.reconcile{
            val oldValue = atom.get()
            val newValue = reducer(oldValue, temp)
            if (newValue != oldValue) {
                parentFiber.effectTag.set(EffectTag.UPDATE)
                atom.set(newValue)
            }
        }
    }
}

class ReducerResult<F, T>(
    val value: T,
    val dispatch: (F) -> Unit
)

fun <F, T> ReducerResult<F, T>.dispatch(f: F) {
    this.dispatch(f)
}

fun <F, M, T> useBaseReducer(
    reducer: ReducerFun<T, F>,
    init: M,
    initFun: (m: M) -> T,
): ReducerResult<F, T> {
    val pf = useParentFiber()
    val envModel = pf.first
    val parentFiber = pf.second
    if (parentFiber.effectTag.get() == EffectTag.CREATE) {
        val hookValues = parentFiber.hookValue ?: mutableListOf()
        parentFiber.hookValue = hookValues
        val value = envModel.createChangeAtom(initFun(init))
        val set = buildSetValue(value, envModel, parentFiber, reducer)
        hookValues.add(
            HookValue(
                value, set,
                reducer,
                init,
                initFun
            ) as HookValue<Any?, Any?>
        )
        return ReducerResult(value.get(), set)
    } else {
        val hookValues = parentFiber.hookValue ?: throw Error("原组件上不存在reducer")
        val hook = hookValues[hookIndex_state] as HookValue<F, T>

        if (hook.reducer != reducer) {
            println("useReducer的reducer变化")
        }
        if (hook.initFun != initFun) {
            println("useReducer的initFun变化")
        }

        hookIndex_state++
        return ReducerResult(hook.value.get(), hook.set)
    }
}


typealias EffectBody<T> = (T) -> ((T) -> Unit)?

internal fun <T> useEffectLevel(level: EFFECT_LEVEL, effect: EffectBody<T>, deps: T) {
    val pf = useParentFiber()
    val envModel = pf.first
    val parentFiber = pf.second
    if (parentFiber.effectTag.get() == EffectTag.CREATE) {
        val hookEffects = parentFiber.hookEffects ?: HashMap()
        parentFiber.hookEffects = hookEffects
        val hookEffectLevel = hookEffects[level] ?: mutableListOf()
        hookEffects[level] = hookEffectLevel
        val state = HookEffect(
            deps, null
        )
        val hookEffect = envModel.createChangeAtom(state)
        hookEffectLevel.add(hookEffect as StoreRef<HookEffect<Any?>>)
        envModel.updateEffect(level) {
            state.destroy = effect(deps)
        }
    } else {
        val hookEffects = parentFiber.hookEffects ?: throw Error("原组件上不存在")
        val hookEffectLevel = hookEffects[level] ?: throw Error("未存在相应level的effect")
        val index = hookIndex_effect[level]!!
        val hookEffect = hookEffectLevel[index] as StoreRef<HookEffect<T>>
        hookIndex_effect[level] = index + 1
        val state = hookEffect.get()
        if (depsNotEqualWithEmpty(state.deps, deps)) {
            val newState = HookEffect(
                deps, null
            ) as HookEffect<T>
            hookEffect.set(newState)
            envModel.updateEffect(level) {
                val destroy = state.destroy
                if (destroy != null) {
                    destroy(state.deps)
                }
                newState.destroy = effect(deps)
            }
        }
    }
}

fun <T> useBeforeAttrEffect(effect: EffectBody<T>, deps: T) {
    useEffectLevel(EFFECT_LEVEL.first, effect, deps)
}

fun useBeforeAttrEffect(effect: EffectBody<Any?>) {
    useBeforeAttrEffect(effect, null)
}

fun <T> useAttrEffect(effect: EffectBody<T>, deps: T) {
    useEffectLevel(EFFECT_LEVEL.second, effect, deps)
}

fun useAttrEffect(effect: EffectBody<Any?>) {
    useAttrEffect(effect, null)
}

fun <T> useEffect(effect: EffectBody<T>, deps: T) {
    useEffectLevel(EFFECT_LEVEL.third, effect, deps)
}

fun useEffect(effect: EffectBody<Any?>) {
    useEffect(effect, null)
}

fun useGetCreateChangeAtom(): EnvInc {
    val pl = useParentFiber()
    return pl.first
}

fun <T, V> useBaseMemoGet(
    effect: (dep: V) -> T, deps: V
): () -> T {
    val pf = useParentFiber()
    val envModel = pf.first
    val parentFiber = pf.second
    if (parentFiber.effectTag.get() == EffectTag.CREATE) {
        val hookMemos = parentFiber.hookMemo ?: mutableListOf()
        parentFiber.hookMemo = hookMemos
        draftParentFiber()
        val state = HookMemo(
            effect(deps), deps
        )
        revertParentFiber()
        val hook = envModel.createChangeAtom(state)
        val get = fun() = hook.get().value
        hookMemos.add(HookMemoValue(get, hook) as HookMemoValue<Any?, Any?>)
        return get
    } else {
        val hookMemos = parentFiber.hookMemo ?: throw Error("原组件上不存在Memo")
        val hook = hookMemos[hookIndex_memo] as HookMemoValue<T, V>
        hookIndex_memo++
        val state = hook.value.get()
        return if (state.deps == deps) {
            hook.get
        } else {
            draftParentFiber()
            val newState = HookMemo(
                effect(deps), deps
            )
            revertParentFiber()
            hook.value.set(newState)
            hook.get
        }
    }
}

data class VirtualDomOperator<T, M>(
    val create: (m: M) -> VirtualDomNode<T>,
    val props: T,
    val init: M
)

internal fun <T> renderBaseFiber(
    createDom: VirtualDomOperator<Any?, Any?>?,
    dynamicChild: Boolean,
    render: (v: T) -> Unit,
    deps: T
): VirtualDomNode<Any?>? {
    val pf = useParentFiber()
    val envModel = pf.first
    val parentFiber = pf.second

    var currentFiber: Fiber
    if (parentFiber.effectTag.get() == EffectTag.CREATE) {
        val dom = if (createDom == null) {
            null
        } else {
            createDom.create(createDom.init)
        }
        currentFiber = createFix(
            envModel, parentFiber, dom, RenderDeps(render, deps) as RenderDeps<Any?>, dynamicChild
        )
        currentFiber.before.set(hookIndex_beforeFiber)
        if (hookIndex_beforeFiber != null) {
            hookIndex_beforeFiber!!.next.set(currentFiber)
        } else {
            parentFiber.firstChild.set(currentFiber)
        }
        parentFiber.lastChild.set(currentFiber)
        hookIndex_beforeFiber = currentFiber
    } else {
        var oldFiber: Fiber? = null
        if (hookIndex_beforeFiber != null) {
            oldFiber = hookIndex_beforeFiber!!.next.get()
        }
        if (oldFiber == null) {
            oldFiber = parentFiber.firstChild.get()
        }
        if (oldFiber == null) throw Error("非预期地出现了Fiber")

        currentFiber = oldFiber
        hookIndex_beforeFiber = currentFiber
        currentFiber.changeRender(RenderDeps(render, deps) as RenderDeps<Any?>)
    }
    val currentDom = currentFiber.dom
    if (currentDom != null) {
        if (createDom == null) throw Error("需要更多参数")
        currentDom.useUpdate(createDom.props)
    }
    return currentDom
}

fun <T> renderFiber(
    createDom: VirtualDomOperator<Any?, Any?>?, render: (v: T) -> Unit, deps: T
): VirtualDomNode<Any?>? {
    return renderBaseFiber(createDom, false, render, deps)
}

interface Context<T> {
    fun useProvider(v: T)
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
    private val defaultContext: ContextProvider<T>

    init {
        defaultContext = createProvider(out)
    }

    private fun createProvider(value: T): ContextProvider<T> {
        return ContextProvider(value)
    }

    private fun <M> createConsumer(
        envModel: EnvModel,
        fiber: Fiber,
        getValue: (T) -> M,
        shouldUpdate: ((M, M) -> Boolean)?
    ): ContextListener<T, M> {
        return ContextListener(envModel, findProvider(fiber), fiber, getValue, shouldUpdate)
    }

    private fun findProvider(_fiber: Fiber): ContextProvider<T> {
        var fiber: Fiber? = _fiber
        while (fiber != null) {
            if (fiber.contextProvider != null) {

                val providers = fiber.contextProvider!!
                if (providers.contains(this as ContextFactory<Any?>)) {
                    return providers[this] as ContextProvider<T>
                }
            }
            fiber = fiber.parent
        }
        return defaultContext
    }

    val id = contextUid++
    override fun useProvider(v: T) {
        val pf = useParentFiber()
        val parentFiber = pf.second
        val map = parentFiber.contextProvider ?: HashMap()
        parentFiber.contextProvider = map
        var hook = map.get(this as ContextFactory<Any?>)
        if (hook == null) {
            hook = createProvider(v) as ContextProvider<Any?>
            map[this] = hook
        }
        hook.changeValue(v)
    }

    override fun <M> useSelector(
        getValue: (v: T) -> M, shouldUpdate: ((a: M, b: M) -> Boolean)?
    ): M {
        val pf = useParentFiber()
        val envModel = pf.first
        val parentFiber = pf.second
        if (parentFiber.effectTag.get() == EffectTag.CREATE) {
            val hookConsumers = parentFiber.hookContextCosumer ?: mutableListOf()
            parentFiber.hookContextCosumer = hookConsumers

            val hook = createConsumer(envModel, parentFiber, getValue, shouldUpdate)
            hookConsumers.add(hook as ContextListener<Any?, Any?>)
            envModel.addDraftConsumer(hook)
            return hook.getValue()
        } else {
            val hookConsoumers =
                parentFiber.hookContextCosumer ?: throw Error("原组件上不存在hookConsumers")
            val hook = hookConsoumers[hookIndex_cusomer] as ContextListener<T, M>
            if (hook.select !== getValue) {
                println("useSelector的getValue变化")
            }
            if (hook.shouldUpdate != shouldUpdate) {
                println("useSelector的shouldUpdate变化")
            }
            hookIndex_cusomer++
            return hook.getValue()
        }
    }

    override fun useConsumer(): T {
        return useSelector(::quote)
    }
}

internal class ContextProvider<T>(
    var value: T
) {
    fun changeValue(v: T) {
        if (value != v) {
            value = v
            callChange()
        }
    }

    private fun callChange() {
        list.forEach {
            it.change()
        }
    }

    private val list = HashSet<ContextListener<T, Any?>>()

    fun on(callback: ContextListener<T, Any?>) {
        if (!list.add(callback)) {
            throw Error("已经存在相应函数")
        }
    }

    fun off(callback: ContextListener<T, Any?>) {
        if (!list.remove(callback)) {
            throw Error("重复删除context")
        }
    }
}

internal class ContextListener<T, M>(
    envModel: EnvModel,
    val context: ContextProvider<T>,
    private val fiber: Fiber,
    var select: (v: T) -> M,
    var shouldUpdate: ((a: M, b: M) -> Boolean)?
) {
    private val atom: StoreRef<M>

    init {
        atom = envModel.createChangeAtom(getFilberValue())
        context.on(this as ContextListener<T, Any?>)
    }

    fun getValue(): M {
        return this.atom.get()
    }

    private fun getFilberValue(): M {
        draftParentFiber()
        val v = select(context.value)
        revertParentFiber()
        return v
    }

    fun change() {
        val newValue = getFilberValue()
        val oldValue = atom.get()
        if (newValue != oldValue && didShouldUpdate(newValue, oldValue)) {
            atom.set(newValue)
            fiber.effectTag.set(EffectTag.UPDATE)
        }
    }

    private fun didShouldUpdate(newValue: M, oldValue: M): Boolean {
        return if (shouldUpdate == null) {
            true
        } else {
            draftParentFiber()
            val v = shouldUpdate!!(newValue, oldValue)
            revertParentFiber()
            v
        }
    }

    fun destroy() {
        context.off(this as ContextListener<T, Any?>)
    }

}

fun useFlushSync(): (EmptyFun) -> Unit {
    return useParentFiber().first.flushSync
}