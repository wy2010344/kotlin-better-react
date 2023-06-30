package org.breact.core

fun <T> quote(v: T) = v

fun depsNotEqualWithEmpty(a:Any?,b:Any?):Boolean{
    return !(a!=null && b!=null && a==b)
}

typealias EMPTY_FUN = (vararg : Any?) -> Unit
interface ManageValue<T>{
    fun add(v:T)
    fun remove(v:T)
}
