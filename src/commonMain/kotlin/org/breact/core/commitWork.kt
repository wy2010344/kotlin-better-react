package org.breact.core

interface EnvInc {
    fun <T : Any?> createChangeAtom(value: T, didCommit: ((v: T) -> T)): StoreRef<T>
}

internal class EnvModel : EnvInc {

    val realTime = storeRef(false)
    val flushSync = fun(callback: EmptyFun) {
        realTime.set(true)
        callback(null)
        reconcile{
            updateEffect(EFFECT_LEVEL.first) {
                realTime.set(false)
            }
        }
    }

    lateinit var reconcile: (work: EmptyFun?) -> Unit
    private val draftConsumers: MutableList<ContextListener<Any?, Any?>> = mutableListOf()
    fun addDraftConsumer(v: ContextListener<Any?, Any?>) {
        draftConsumers.add(v)
    }

    private val deletions: MutableList<Fiber> = mutableListOf()
    fun addDelect(fiber: Fiber) {
        deletions.add(fiber)
    }

    private val updateEffects = mapOf(
        EFFECT_LEVEL.first to mutableListOf<EmptyFun>(),
        EFFECT_LEVEL.second to mutableListOf<EmptyFun>(),
        EFFECT_LEVEL.third to mutableListOf<EmptyFun>()
    )

    fun updateEffect(level: EFFECT_LEVEL, set: EmptyFun) {
        updateEffects[level]!!.add(set)
    }

    private val changeAtoms = mutableListOf<ChangeAtom<Any?>>()
    private val manageChangeAtom = object : ManageValue<ChangeAtom<Any?>> {
        override fun add(v: ChangeAtom<Any?>) {
            changeAtoms.add(v)
        }

        override fun remove(v: ChangeAtom<Any?>) {
            changeAtoms.remove(v)
        }
    }

    override fun <T : Any?> createChangeAtom(value: T, didCommit: ((v: T) -> T)): StoreRef<T> =
        ChangeAtom(manageChangeAtom, value, didCommit)

    fun shouldRender(): Boolean {
        return changeAtoms.size > 0 || deletions.size > 0
    }

    fun rollback() {
        changeAtoms.forEach {
            it.rollback()
        }
        changeAtoms.clear()
        draftConsumers.forEach {
            it.destroy()
        }
        draftConsumers.clear()
        deletions.clear()
        updateEffects.forEach { e ->
            e.value.clear()
        }
    }

    fun commit(rootFiber: Fiber, layout: () -> Unit) {
        changeAtoms.forEach {
            it.commit()
        }
        changeAtoms.clear()
        runUpdateEffect(updateEffects[EFFECT_LEVEL.first])
        draftConsumers.clear()
        deletions.forEach {
            notifyDel(it)
            commitDeletion(it)
        }
        deletions.clear()
        runUpdateEffect(updateEffects[EFFECT_LEVEL.second])
        updateFixDom(rootFiber)
        layout()
        runUpdateEffect(updateEffects[EFFECT_LEVEL.third])
    }

    private fun runUpdateEffect(list: MutableList<EmptyFun>?) {
        list?.forEach { call ->
            call(null)
        }
        list?.clear()
    }
}

internal fun <T> EnvModel.createChangeAtom(value: T): StoreRef<T> {
    return createChangeAtom(value, ::quote)
}


private class ChangeAtom<T : Any?>(
    private val manage: ManageValue<ChangeAtom<Any?>>,
    private var value: T,
    private var whenCommit: ((v: T) -> T)
) : StoreRef<T> {
    private var isCreate = true

    init {
        manage.add(this as ChangeAtom<Any?>)
    }

    private var dirty = false
    private var draftValue = value
    override fun set(v: T) {
        if (isCreate) {
            value = v
        } else {
            if (v != value) {
                if (!dirty) {
                    dirty = true
                    manage.add(this as ChangeAtom<Any?>)
                }
                draftValue = v
            } else {
                if (dirty) {
                    dirty = false
                    manage.remove(this as ChangeAtom<Any?>)
                }
                draftValue = value
            }
        }
    }

    override fun get(): T {
        if (isCreate) {
            return value
        } else {
            if (dirty) {
                return draftValue
            }
            return value
        }
    }

    fun commit() {
        if (isCreate) {
            isCreate = false
            value = whenCommit(value)
        } else {
            dirty = false
            value = whenCommit(draftValue)
        }
    }

    fun rollback() {
        if (isCreate) {

        } else {
            dirty = false
        }
    }
}

private fun updateFixDom(_fiber: Fiber?) {
    var fiber = _fiber
    while (fiber != null) {
        fiber = fixDomAppend(fiber, Unit)
    }
}

private val fixDomAppend = deepTravelFiber<Unit>(fun(fiber, _) {
    findParentAndBefore(fiber)
})

private fun notifyDel(fiber: Fiber) {
    destroyFiber(fiber)
    val child = fiber.firstChild.get()
    if (child != null) {
        var next = child
        while (next != null) {
            notifyDel(next)
            next = next.next.get()
        }
    }
}

private fun destroyFiber(fiber: Fiber) {
    fiber.destroyed = true
    val effects = fiber.hookEffects
    if (effects != null) {
        destroyEffect(effects[EFFECT_LEVEL.first])
        destroyEffect(effects[EFFECT_LEVEL.second])
        destroyEffect(effects[EFFECT_LEVEL.third])
    }
    fiber.hookContextCosumer?.forEach {
        it.destroy()
    }
    fiber.dom?.destroy()
}


private fun destroyEffect(effect: List<StoreRef<HookEffect<Any?>>>?) {
    effect?.forEach {
        val state = it.get()
        val destroy = state.destroy
        if (destroy != null) {
            destroy(state.deps)
        }
    }
}


private fun commitDeletion(fiber: Fiber) {
    if (fiber.dom != null) {
        fiber.dom.removeFromParent()
    } else {
        circleCommitDelection(fiber.firstChild.get())
    }
}

private fun circleCommitDelection(fiber: Fiber?) {
    if (fiber != null) {
        commitDeletion(fiber)
        circleCommitDelection(fiber.next.get())
    }
}