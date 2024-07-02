package org.breact.core



interface TempReal{
   fun reset()
    fun add(vararg vs:Any)
}
sealed class AbsTempOps<T:TempReal>(
    creater:()->T
){
    val data=creater()
    abstract fun notifyChange()
    abstract fun createSub():TempSubOps<T>

    fun addNode(vararg n:Any){
        data.add(n)
        notifyChange()
    }
}

class TempOps<T:TempReal>(
    val creater: () -> T,
    private val notify:()->Unit
):AbsTempOps<T>(creater){
    override fun notifyChange() {
        notify()
    }

    override fun createSub(): TempSubOps<T> {
        return TempSubOps(this)
    }
}
class TempSubOps<T:TempReal>(
   private val belong:TempOps<T>
):AbsTempOps<T>(belong.creater){
    override fun notifyChange() {
        belong.notifyChange()
    }

    override fun createSub(): TempSubOps<T> {
        return TempSubOps(belong)
    }
}