package org.breact.core

fun <T> quote(v: T) = v
fun depsNotEqualWithEmpty(a:Any?,b:Any?):Boolean{
    return !(a!=null && b!=null && a==b)
}

typealias EmptyFun = (vararg : Any?) -> Unit
val emptyFun:EmptyFun={}
typealias AnyFun=(vararg:Any?)->Any?

typealias Equal<T> = (T,T)->Boolean
private val simpleEqual:Equal<Any?> = {a,b->
    a==b
}
fun <T> getSimpleEqual():Equal<T>{
    return simpleEqual
}
fun <T:EmptyFun> expandFunCall(function: T){
    function(null)
}
fun <T:EmptyFun> expandFunReturn(function: T){
    return function(null)
}

interface ManageValue<T>{
    fun add(v:T)
    fun remove(v:T)
}
