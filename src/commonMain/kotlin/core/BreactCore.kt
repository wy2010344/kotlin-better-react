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
    reconcile(null,null)
    return fun() {
        batchWork.destroy()
    }
}

private fun getReconcile(
    batchWork: BatchWork,
    envModel: EnvModel,
    askWork: AskNextTimeWork
): (EMPTY_FUN?, EMPTY_FUN?) -> Unit {
    val workUnit = WorkUnits(envModel, batchWork)
    val askNextTimeWork = askWork {
        workUnit.getNextWork()
    }
    return getRec1(askNextTimeWork) {
        workUnit.appendWork(it)
    }
}

private fun getRec1(
    askNextTimeWork: EMPTY_FUN,
    appendWork: (work: WorkUnit) -> Unit,
): (EMPTY_FUN?, EMPTY_FUN?) -> Unit {
    var batchUpdateOn = false
    val batchUpdateWorks = mutableListOf<LoopWork>()
    return fun(
        beforeLoop: EMPTY_FUN?,
        afterLoop: EMPTY_FUN?
    ): Unit {
        batchUpdateWorks.add(
            LoopWork(
                isLow = currentWorkIsLow,
                beforeLoop,
                afterLoop
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
                    var x = 0
                    while (x < workList.size) {
                        val work = workList[x]
                        if (work is LoopWork && shouldAdd(work)) {
                            if (work.beforeWork != null) {
                                renderWorks.appendWork(work.beforeWork)
                            }
                            currentTick.appendLowRollback(work)
                        }
                        x++
                    }
                    //动态添加渲染任务
                    renderWorks.appendWork {
                        batchWork.beginRender(currentTick, renderWorks)
                    }
                    //寻找渲染后的任务
                    var y = 0
                    while (y < workList.size) {
                        val work = workList[y]
                        if (work is LoopWork && shouldAdd(work)) {
                            if (work.afterWork != null) {
                                renderWorks.appendWork(work.afterWork)
                            }
                        }
                        y++
                    }
                    //清空渲染任务
                    var z = workList.size - 1
                    while (z > -1) {
                        val work = workList[z]
                        if (work is LoopWork && shouldAdd(work)) {
                            workList.removeAt(z)
                        }
                        z--
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
    private val list = mutableListOf<EMPTY_FUN>()
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

    fun appendWork(work: EMPTY_FUN) {
        list.add(work)
    }

    fun unshiftWork(work: EMPTY_FUN) {
        list.add(0, work)
    }
}

internal class BatchWork(
    private var rootFiber: Fiber?,
    private val envModel: EnvModel,
    private val layout: () -> Unit
) {
    fun beginRender(currentTick: CurrentTick, renderWorks: RenderWorks) {
        if (envModel.hasChangeAtoms() && rootFiber != null) {
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
            envModel.reconcile(null) {
                rootFiber = null
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

typealias AskNextTimeWork = (nextCall: () -> RealWork?) -> EMPTY_FUN

sealed class WorkUnit
data class BatchCollect(
    val work: EMPTY_FUN
) : WorkUnit()

data class LoopWork(
    val isLow: Boolean,
    val beforeWork: EMPTY_FUN?,
    val afterWork: EMPTY_FUN?
) : WorkUnit()

internal enum class EFFECT_LEVEL {
    first, second, third
}


private val performUnitOfWrok = deepTravelFiber<EnvModel>(fun(fiber, envModel) {
    if (fiber.effectTag.get() != EffectTag.EMPTY) {
        updateFunctionComponent(envModel, fiber)
    }
})