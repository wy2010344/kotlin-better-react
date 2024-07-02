package org.breact.core

fun <T> quote(v: T) = v
fun <T> simpleNotEqual(a:T,b:T):Boolean{
    return a!=b
}
fun <T> arrayEqual(a:List<T>,b:List<T>):Boolean{
    if(a.size==b.size){
        var i=0
        while (i<a.size){
            if(a[i]!=b[i]){
                return false
            }
            i++
        }
        return true
    }
    return false
}
fun <T> arrayNotEqual(a:List<T>,b:List<T>):Boolean{
    return !arrayEqual(a,b)
}
typealias EmptyFun = () -> Unit
typealias GetValue<T> = ()->T
typealias SetValue<T> = (T)->Unit
val emptyFun:EmptyFun={}
typealias AnyFun=(vararg:Any?)->Any?

typealias Equal<T> = (T,T)->Boolean
private val simpleEqual:Equal<Any?> = {a,b->
    a==b
}
fun <T> getSimpleEqual():Equal<T>{
    return simpleEqual
}
fun <T:EmptyFun> expandFunReturn(function: T){
    return function()
}
fun <T> arrayFunToOneOrEmpty(list: List<SetValue<T>?>): SetValue<T>? {
    if (list.size == 1) {
        return list[0]
    } else if (list.size>0) {
        return   {arg->
            list.forEach {
                it?.invoke(arg)
            }
        }
    }
    return null
}

interface ManageValue<T>{
    fun add(v:T)
    fun remove(v:T)
}

val alawaysTrue:GetValue<Boolean> = {
    true
}
fun run(func:EmptyFun){
    func()
}

inline fun <T> List<T>.some(predicate: (T) -> Boolean): Boolean =this.indexOfFirst(predicate)>-1

typealias EventChangeHandler<T> = (v: T, old: T) -> Unit;
interface VirtualEventCenter<T>{
    fun subscribe(notify:EventChangeHandler<T>):EmptyFun
}
interface ReadValueCenter<T>:VirtualEventCenter<T>{
    fun get():T
}

interface ValueCenter<T>:ReadValueCenter<T>{
    fun set(v:T)
}

private class EventCenter<T>() {
    val pool = mutableSetOf<EventChangeHandler<T>>()
 fun   poolSize(): Int {
        return pool.size
    }
    fun subscribe(notify: EventChangeHandler<T>): EmptyFun {
        if (this.pool.contains(notify)) {
            return emptyFun
        }
        this.pool.add(notify)
        return {
            pool.remove(notify)
        }
    }
   fun notify(v: T, oldV: T) {
        this.pool.forEach{notify -> notify(v, oldV)}
    }
}
class ValueCenterImpl<T>(
    private var value:T
):ValueCenter<T>{
    private val ec=EventCenter<T>()
    override fun subscribe(notify: EventChangeHandler<T>): EmptyFun {
        return ec.subscribe(notify)
    }
    override fun set(v: T) {
        val oldValue=this.value
        this.value=v
        ec.notify(v,oldValue)
    }
    override fun get(): T {
        return this.value
    }
}

fun <T> valueCenterOf(value:T):ValueCenter<T>{
    return ValueCenterImpl(value)
}