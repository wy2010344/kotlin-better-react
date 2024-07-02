package org.breact.core

interface EnvInc {
    fun <T : Any?> createChangeAtom(value: T, didCommit: ((v: T) -> T)): StoreRef<T>
}

private enum class OnWork{ False,
    True,
    Commit
}
class EnvModel : EnvInc {
    val realTime = storeRef(false)
    val setRealTime:EmptyFun={
        if(onWork!=OnWork.Commit){
            throw Error("只能在commit work中提交")
        }
        realTime.set(true)
    }
    private var onWork:OnWork = OnWork.False
   fun setOnWork(isCommit:Boolean? ) {
       if(isCommit == true){
           this.onWork=OnWork.True
       }else{
           this.onWork=OnWork.Commit
       }
    }
    fun isOnWork(): Boolean {
        return this.onWork!=OnWork.False
    }
   fun finishWork() {
        this.onWork = OnWork.False
    }
    lateinit var commitAll:()->Unit
    lateinit var reconcile: (work: EmptyFun?) -> Unit
    private val deletions: MutableList<Fiber> = mutableListOf()
    fun addDelect(fiber: Fiber) {
        deletions.add(fiber)
    }

    private val updateEffects = mutableMapOf<Float,MutableList<EmptyFun>>()
    fun updateEffect(level: Float, set: EmptyFun) {
        var old=updateEffects.get(level)
        if(old==null){
            old= mutableListOf()
            updateEffects.put(level,old)
        }
        old.add(set)
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

    fun shouldRender(): Boolean {
        return changeAtoms.size>0 || deletions.size>0 || updateEffects.size>0
    }

    override fun <T : Any?> createChangeAtom(value: T, didCommit: ((v: T) -> T)): StoreRef<T> =
        ChangeAtom(manageChangeAtom, value, didCommit)

    fun<T:Any?> createChangeAtom(value:T)=createChangeAtom(value,::quote)

    fun rollback() {
        changeAtoms.forEach {
            it.rollback()
        }
        changeAtoms.clear()
        deletions.clear()
        updateEffects.clear()
    }

    //最后执行是否有layoutWork
    lateinit var layoutEffect:SetValue<EmptyFun>
    //在useEffect里执行的LayoutEffect
    lateinit var layoutWork:EmptyFun
    fun commit() {
        realTime.set(false)
        changeAtoms.forEach {
            it.commit()
        }
        changeAtoms.clear()
        deletions.forEach {
            notifyDel(it)
        }
        deletions.clear()
        updateEffects.keys.sorted().forEach {
            updateEffects.get(it)?.forEach{
                it()
            }
        }
        updateEffects.clear()
        layoutWork()
    }
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
        val envModel=fiber.envModel
        effects.forEach{
            val state=it.get()
            envModel.updateEffect(state.level){
                val destroy=state.destroy
                if(destroy!=null){
                    hookAddEffect(envModel.layoutEffect)
                    destroy(
                        EffectDestroyEventTrue(
                            state.value,
                            beforeIsInit=state.isInit,
                            beforeTrigger = state.deps,
                            setRealTime = envModel.setRealTime
                        )
                    )
                    hookAddEffect(null)
                }
            }
        }
    }
}