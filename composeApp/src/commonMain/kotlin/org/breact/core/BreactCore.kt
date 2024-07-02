package org.breact.core

fun render(
    getSubOps:(inv:EnvInc)->AbsTempOps<TempReal>,
    render:EmptyFun,
    getAsk: AskNextTimeWork
): () -> Unit {
    val envModel = EnvModel()
    val rootFiber = createFix(
        envModel,
        null,
        RenderDeps<Any?>(
            alawaysTrue as (Any?,Any?)->Boolean,
            render as SetValue<FiberEvent<Any?>>,
            FiberEventInit(null)
        ) as RenderDeps<Any?>
    )
    rootFiber.subOps = getSubOps(envModel)
    val batchWork = BatchWork(rootFiber, envModel)
    val reconcile = getReconcile(batchWork::beginRender, envModel, getAsk)
    envModel.reconcile = reconcile
    reconcile(null)
    return {
        batchWork.destroy()
    }
}


private fun getReconcile(
    beginRender: BeginRender,
    envModel: EnvModel,
    askWork: AskNextTimeWork
): (EmptyFun?) -> Unit {
    val workUnit = WorkUnits(envModel, beginRender)

    envModel.commitAll={
        if (envModel.isOnWork()){
            throw Error("render中不能commit all")
        }
        var work=workUnit.getNextWork()
        while (work!=null){
            work.run()
            work=workUnit.getNextWork()
        }
    }
    val askNextTimeWork = askWork(envModel.realTime,workUnit::getNextWork)
    envModel.layoutWork =  {
        if (workUnit.hasLayoutWork()) {
            //把实时任务执行了
            var work = workUnit.getLayoutWork()
            while (work!=null) {
                work!!.run()
                work = workUnit.getLayoutWork()
            }
        }
    }
    envModel.layoutEffect = ::layoutEffect
    flushWorkMap.set(envModel) {
        if (workUnit.hasFlushWork()) {
            if (envModel.isOnWork()) {
                throw Error("render中不能commit all")
            }
            //把实时任务执行了
            var work =workUnit. getFlushWork()
            while (work != null) {
                work!!.run()
                work =workUnit. getFlushWork()
            }
            //其余任务可能存储,再申请异步
            if (workUnit.getNextWork() != null) {
                println("继续执行普通任务")
                askNextTimeWork()
            }
        }
    }
    return {
       workUnit. appendWork(LoopWork(currentTaskLevel,it))
        askNextTimeWork()
    }
}

internal typealias BeginRender = (renderWorks: RenderWorks, commitWork: EmptyFun) ->Unit
private data class CheckGet<T>(
    val has:()->Boolean,
    val get:()->T
)
private class WorkUnits(
    val envModel: EnvModel,
    val beginRender: BeginRender
) {
    private  val workList = mutableListOf<LoopWork>()
    private   val currentTick = CurrentTick {
        workList.add(0, it)
    }
    private  val renderWorks = RenderWorks()

    private   val commitWork:EmptyFun={
        currentTick.commit()
    }

    private fun getTheRenderWork(
        level: WorkLevel
    ): CheckGet<NextTimeWork?> {
        val shouldAdd = {work:LoopWork->
            work.level==level
        }
        fun hasWork()=workList.some(shouldAdd)
        fun openAWork(vararg v:Any?){
            if(hasWork()){
                workList.removeAll {
                    if(shouldAdd(it)){
                        if(it.work!=null){
                            renderWorks.appendWork(it.work)
                        }
                        currentTick.appendLowRollback(it)
                        false
                    }
                    true
                }
                renderWorks.appendWork(::openAWork)
            }else{
                beginRender(renderWorks,commitWork)
            }
        }
        return CheckGet(::hasWork) {
            if (hasWork()) {
                NextTimeWork {
                    currentTick.open(level)
                    openAWork()
                }
            } else null
        }
    }

    private   val getRenderWorkLow = getTheRenderWork(WorkLevel.Low)
    private   val getRenderWork = getTheRenderWork(WorkLevel.Normal)
    private   val getLayoutRenderWork = getTheRenderWork(WorkLevel.Layout)
    private   val getFlushRenderWork = getTheRenderWork(WorkLevel.Flush)

    private   val rollBackWork = NextTimeWork{
        println("执行${currentTick.level()}优先级任务,中断低优先级")
        renderWorks.rollback()
        currentTick.rollback()
        envModel.rollback()
//        val work=getWork.get()
//        work?.run()
    }


    fun appendWork(work: LoopWork){
        workList.add(work)
    }
    val hasFlushWork=getFlushRenderWork.has
    fun getFlushWork():NextTimeWork?{
        val level= currentTick.level()
        if (level!=null &&level > WorkLevel.Flush) {
            if (getFlushRenderWork.has()) {
                return rollBackWork
            }
        }
        val renderWork = renderWorks.getFirstWork()
        if (renderWork!=null) {
            return renderWork
        }
        val flushWork = getFlushRenderWork.get()
        if (flushWork!=null) {
            return flushWork
        }
        return null
    }

    val hasLayoutWork=getLayoutRenderWork.has


    fun getLayoutWork(): NextTimeWork? {
        val level= currentTick.level()
        if (level!=null &&level>WorkLevel.Layout){
            if(getLayoutRenderWork.has()){
                return rollBackWork
            }
        }

        val renderWork = renderWorks.getFirstWork()
        if (renderWork!=null) {
            return renderWork
        }
        val layoutWork = getLayoutRenderWork.get()
        if (layoutWork!=null) {
            return layoutWork
        }
        return null
    }

    fun getNextWork(): NextTimeWork? {
        val level= currentTick.level()
        if (level!=null && level > WorkLevel.Normal) {
            /**
             * 如果当前是延迟任务
             * 寻找是否有渲染任务,如果有,则中断
             * 如果有新的lazywork,则也优先
             */
            if (getRenderWork.has()) {
                return rollBackWork
            }
            if (getRenderWorkLow.has()) {
                return rollBackWork
            }
        }
        //执行计划的渲染任务
        val renderWork = renderWorks.getFirstWork()
        if (renderWork!=null) {
            return renderWork
        }
        //@todo 渲染后的任务可以做在这里....
        //寻找渲染任务
        val work = getRenderWork.get()
        if (work!=null) {
            return work
        }
        //寻找低优先级渲染任务
        val lowWork = getRenderWorkLow.get()
        if (lowWork!=null) {
            return lowWork
        }
        return null
    }
}

internal class CurrentTick(
    private val rollbackWork: (work: LoopWork) -> Unit
) {
    private var on:WorkLevel? = null
    private val lowRollbackList = mutableListOf<LoopWork>()
    fun open(level: WorkLevel) {
        on = level
    }

    private fun close() {
        on = null
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

    fun level()=on
}

internal class RenderWorks {
    private val list = mutableListOf<Pair<EmptyFun,Boolean>>()
    fun rollback() {
        list.clear()
    }
    private fun getRetWork(lastJob:Boolean)= NextTimeWork(lastJob){
        val work=list.removeAt(0)
        work!!.first()
    }
    private val lastRetWork = getRetWork(true)
    private val retWork = getRetWork(false)
    fun getFirstWork(): NextTimeWork? {
        if (list.size > 0) {
            return if (list[0].second)lastRetWork else retWork
        }
        return null
    }

    fun appendWork(work: EmptyFun) {
        list.add(Pair(work,false))
    }
    fun appendLastWork(work:EmptyFun){
        list.add(Pair(work,true))
    }
}

internal class BatchWork(
    private var rootFiber: Fiber?,
    private val envModel: EnvModel
) {
    fun beginRender(
        renderWorks: RenderWorks,
        commitWork:EmptyFun
    ) {
        if (envModel.shouldRender() && rootFiber != null) {
            workLoop(renderWorks, rootFiber!!,commitWork)
        }
    }

    private fun workLoop(
        renderWorks: RenderWorks,
        unitOfWork: Fiber,
        commitWork:EmptyFun
    ) {
        val nextUnitOfWork = performUnitOfWrok(unitOfWork)
        if (nextUnitOfWork != null) {
            renderWorks.appendWork {
                workLoop(renderWorks, nextUnitOfWork,commitWork)
            }
        } else {
            renderWorks.appendLastWork {
                commitWork()
                finishRender()
            }
        }
    }

    private fun finishRender() {
        envModel.commit()
    }

    fun destroy() {
        if (rootFiber != null) {
            envModel.addDelect(rootFiber!!)
            envModel.reconcile{
                envModel.updateEffect(0f){
                    rootFiber = null
                }
            }
        }
    }
}

var currentTaskLevel = WorkLevel.Normal
fun startTransition(callback: EmptyFun) {
    val old = currentTaskLevel
    currentTaskLevel = WorkLevel.Low
    callback()
    currentTaskLevel = old
}
private fun layoutEffect(callbacl: EmptyFun) {
    val old = currentTaskLevel
    currentTaskLevel = WorkLevel.Layout
    callbacl()
    currentTaskLevel = old
}
private val flushWorkMap:MutableMap<EnvModel,EmptyFun> = mutableMapOf()

fun flushSync(callback: EmptyFun) {
    val old = currentTaskLevel
    currentTaskLevel = WorkLevel.Flush
    callback()
    currentTaskLevel = old
    flushWorkMap.forEach{it.value()}
}
data class NextTimeWork(
    val lastJob: Boolean = false,
   private val callback: () -> Unit
){
    fun run(){
this.callback()
    }
}

typealias AskNextTimeWork = (realTime:StoreRef<Boolean>,nextCall: () -> NextTimeWork?) -> EmptyFun

data class LoopWork(
   val level: WorkLevel,
   val work: EmptyFun?
)

enum class WorkLevel(val value:Int){
    Flush(1),Layout(2),Normal(3),Low(4)
}

private val performUnitOfWrok = deepTravelFiber(fun(fiber) {
    if (fiber.effectTag.get() != EffectTag.EMPTY) {
        updateFunctionComponent(fiber)
    }
})