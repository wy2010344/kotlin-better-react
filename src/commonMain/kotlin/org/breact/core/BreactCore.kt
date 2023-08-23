package org.breact.core

fun <T> render(
    dom: VirtualDomNode<T>,
    props: T,
    render: () -> Unit,
    layout: () -> Unit,
    getAsk: AskNextTimeWork
): () -> Unit {
    val envModel = EnvModel()
    val rootFiber = createFix(
        envModel,
        null,
        dom as VirtualDomNode<Any?>,
        RenderDeps(
            {
                dom.useUpdate(props)
                render()
            },
            null
        ) as RenderDeps<Any?>
    )
    val batchWork = BatchWork(rootFiber, envModel, layout)
    val reconcile = getReconcile(batchWork, envModel, getAsk)
    envModel.reconcile = reconcile
    reconcile(null)
    return fun() {
        batchWork.destroy()
    }
}

private fun getReconcile(
    batchWork: BatchWork,
    envModel: EnvModel,
    askWork: AskNextTimeWork
): (EmptyFun?) -> Unit {
    val workUnit = WorkUnits(envModel, batchWork)
    val askNextTimeWork = askWork(envModel.realTime) {
        workUnit.getNextWork()
    }
    return getRec1(askNextTimeWork) {
        workUnit.appendWork(it)
    }
}

private fun getRec1(
    askNextTimeWork: EmptyFun,
    appendWork: (work: WorkUnit) -> Unit,
): (EmptyFun?) -> Unit {
    var batchUpdateOn = false
    val batchUpdateWorks = mutableListOf<LoopWork>()
    return fun(work:EmptyFun?) {
        batchUpdateWorks.add(
            LoopWork(
                isLow = currentWorkIsLow,
                work
            )
        )
        if (!batchUpdateOn) {
            batchUpdateOn = true
            appendWork(BatchCollect {
                //批量提交
                batchUpdateOn = false
                batchUpdateWorks.forEach {
                    appendWork(it)
                }
                batchUpdateWorks.clear()
            })
            askNextTimeWork(null)
        }
    }
}

private class WorkUnits(
    val envModel: EnvModel,
    val batchWork: BatchWork
) {
    val workList = mutableListOf<WorkUnit>()
    val currentTick = CurrentTick {
        workList.add(0, it)
    }
    val renderWorks = RenderWorks()
    private fun getBatchWork(): RealWork? {
        val index = workList.indexOfFirst { it is BatchCollect }
        if (index < 0) {
            return null
        }
        return RealWork {
            (workList.removeAt(index) as BatchCollect).work(null)
        }
    }

    private fun getRenderWork(isLow: Boolean): RealWork? {
        var loopIndex = -1
        fun shouldAdd(work: LoopWork) = (isLow && work.isLow) || (!isLow && !work.isLow)
        var i = workList.size - 1
        while (i > -1 && loopIndex < 0) {
            val work = workList[i]
            if (work is LoopWork && shouldAdd(work)) {
                loopIndex = i
                return RealWork(true) {
                    currentTick.open((isLow))
                    //寻找渲染前的任务
                    workList.removeAll {work->
                        if (work is LoopWork && shouldAdd(work)) {
                            if (work.work != null) {
                                renderWorks.appendWork(work.work)
                            }
                            currentTick.appendLowRollback(work)
                            true
                        }else{
                            false
                        }
                    }
                    //动态添加渲染任务
                    renderWorks.appendWork {
                        batchWork.beginRender(currentTick, renderWorks)
                    }
                }
            }
            i--
        }
        return null
    }

    fun appendWork(work: WorkUnit) {
        workList.add(work)
    }

    fun getNextWork(): RealWork? {
        //寻找批量任务
        val collectWork = getBatchWork()
        if (collectWork != null) {
            return collectWork
        }
        if (currentTick.isOnLow()) {
            //寻找是否有渲染任务,如果有,则中断
            val work = getRenderWork(false)
            if (work != null) {
                renderWorks.rollback()
                currentTick.rollback()
                envModel.rollback()
                print("强行中断低优先级任务,执行高优先级")
                return work
            }
        }
        //执行计划任务
        val renderWork = renderWorks.getFirstWork()
        if (renderWork != null) {
            return renderWork
        }
        //寻找渲染任务
        val work = getRenderWork(false)
        if (work != null) {
            return work
        }
        //寻找低优先级渲染任务
        val lowWork = getRenderWork(true)
        if (lowWork != null) {
            return lowWork
        }
        return null
    }
}

internal class CurrentTick(
    private val rollbackWork: (work: LoopWork) -> Unit
) {
    private var on = false
    private var isLow = false
    private val lowRollbackList = mutableListOf<LoopWork>()
    fun open(isLow: Boolean) {
        on = true
        this.isLow = isLow
    }

    private fun close() {
        on = false
        lowRollbackList.clear()
    }

    fun appendLowRollback(work: LoopWork) {
        lowRollbackList.add(work)
    }

    fun commit() {
        close()
    }

    fun rollback() {
        var i = lowRollbackList.size - 1
        while (i > -1) {
            rollbackWork(lowRollbackList[i])
            i--
        }
        close()
    }

    fun isOnLow(): Boolean {
        return on && isLow
    }
}

internal class RenderWorks {
    private val list = mutableListOf<EmptyFun>()
    fun rollback() {
        list.clear()
    }

    fun getFirstWork(): RealWork? {
        if (list.size > 0) {
            return RealWork {
                val work = list.removeAt(0)
                work(null)
            }
        }
        return null
    }

    fun appendWork(work: EmptyFun) {
        list.add(work)
    }

    fun unshiftWork(work: EmptyFun) {
        list.add(0, work)
    }
}

internal class BatchWork(
    private var rootFiber: Fiber?,
    private val envModel: EnvModel,
    private val layout: () -> Unit
) {
    fun beginRender(currentTick: CurrentTick, renderWorks: RenderWorks) {
        if (envModel.shouldRender() && rootFiber != null) {
            workLoop(renderWorks, currentTick, rootFiber!!)
        }
    }

    private fun workLoop(renderWorks: RenderWorks, currentTick: CurrentTick, unitOfWork: Fiber) {
        val nextUnitOfWork = performUnitOfWrok(unitOfWork, envModel)
        if (nextUnitOfWork != null) {
            renderWorks.unshiftWork {
                workLoop(renderWorks, currentTick, nextUnitOfWork)
            }
        } else {
            renderWorks.unshiftWork {
                currentTick.commit()
                finishRender()
            }
        }
    }

    private fun finishRender() {
        envModel.commit(rootFiber!!, layout)
    }

    fun destroy() {
        if (rootFiber != null) {
            envModel.addDelect(rootFiber!!)
            envModel.reconcile{
                envModel.updateEffect(EFFECT_LEVEL.second){
                    rootFiber = null
                }
            }
        }
    }
}

var currentWorkIsLow = false
fun startTransition(callback: () -> Unit) {
    currentWorkIsLow = true
    callback()
    currentWorkIsLow = false
}

data class RealWork(
    val isRender: Boolean = false,
    val callback: () -> Unit
)

typealias AskNextTimeWork = (realTime:StoreRef<Boolean>,nextCall: () -> RealWork?) -> EmptyFun

sealed class WorkUnit
data class BatchCollect(
    val work: EmptyFun
) : WorkUnit()

data class LoopWork(
    val isLow: Boolean,
    val work: EmptyFun?
) : WorkUnit()

internal enum class EFFECT_LEVEL {
    first, second, third
}


private val performUnitOfWrok = deepTravelFiber<EnvModel>(fun(fiber, envModel) {
    if (fiber.effectTag.get() != EffectTag.EMPTY) {
        updateFunctionComponent(envModel, fiber)
    }
})