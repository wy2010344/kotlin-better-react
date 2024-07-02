package org.breact.helper

import org.breact.core.*

fun <T> useBaseEffect(
    level: Float,
    shouldChange: Equal<T>,
    effect: (e: EffectEvent<Nothing?, T>) -> EffectDestroy<Nothing?, T>,
    deps: T) {
    useLevelEffect(level,
        shouldChange,
        {
        Pair(null, effect(it))
    }, deps)
}

typealias EffectSelf = (e: EffectEvent<Nothing?, *>) -> EffectDestroy<Nothing?, *>


fun <T> useEffect(effect: (e: EffectEvent<Nothing?, T>) -> EffectDestroy<Nothing?, T>, deps: T){
    useBaseEffect(1f, ::simpleNotEqual,effect,deps)
}
fun <T> useEffect(effect: (e: EffectEvent<Nothing?, List<T>>) -> EffectDestroy<Nothing?, List<T>>, deps: List<T>){
    useBaseEffect(1f, ::arrayNotEqual,effect,deps)
}
fun useEffect(effect: EffectSelf){
    useEffect(effect,effect)
}


fun <T> useAttrEffect(effect: (e: EffectEvent<Nothing?, T>) -> EffectDestroy<Nothing?, T>, deps: T){
    useBaseEffect(0f, ::simpleNotEqual,effect,deps)
}
fun <T> useAttrEffect(effect: (e: EffectEvent<Nothing?, List<T>>) -> EffectDestroy<Nothing?, List<T>>, deps: List<T>){
    useBaseEffect(0f, ::arrayNotEqual,effect,deps)
}
fun useAttrEffect(effect: EffectSelf){
    useAttrEffect(effect,effect)
}



fun <T> useBeforeAttrEffect(effect: (e: EffectEvent<Nothing?, T>) -> EffectDestroy<Nothing?, T>, deps: T){
    useBaseEffect(-1f, ::simpleNotEqual,effect,deps)
}
fun <T> useBeforeAttrEffect(effect: (e: EffectEvent<Nothing?, List<T>>) -> EffectDestroy<Nothing?, List<T>>, deps: List<T>){
    useBaseEffect(-1f, ::arrayNotEqual,effect,deps)
}
fun useBeforeAttrEffect(effect: EffectSelf){
    useBeforeAttrEffect(effect,effect)
}


/*** */
fun buildHookEffect(level: Float): (EmptyFun) -> Unit {
    return fun (effect: EmptyFun) {
        return hookLevelEffect(level, effect)
    }
}

val hookBeforeAttrEffect = buildHookEffect(-1f)
val hookAttrEffect = buildHookEffect(0f)
val hookEffect = buildHookEffect(1f)


//////888/8888

private var globalVS:MutableList<(e: EffectDestroyEvent<Any?,Any?>) -> Unit>? = null

fun <V, T> addEffectDestroy(call: (e: EffectDestroyEvent<V, T>) -> Unit) {
    if (globalVS!=null) {
        globalVS!!.add(call as (e: EffectDestroyEvent<Any?,Any?>) -> Unit)
    } else {
        throw Error("必须在effect里执行")
    }
}
typealias EffectHookSelf<V> = (e: EffectEvent<V, *>) -> V

fun <V, T> useBaseHookEffect(
    level: Float,
    shouldChange: Equal<T>,
    effect: (e: EffectEvent<V, T>) -> V,
    deps: T) {
    useLevelEffect(
        level,
        shouldChange,
        {
            val vs= mutableListOf<(e: EffectDestroyEvent<V, T>) -> Unit>()
            val d=arrayFunToOneOrEmpty(vs)
            globalVS = vs as MutableList<(e: EffectDestroyEvent<Any?,Any?>) -> Unit>
            val value = effect(it)
            globalVS = null
            Pair(value, d)
        }, deps)
}
fun <V,T> useHookEffect(effect: (e: EffectEvent<V, T>) -> V, deps: T){
    useBaseEffect(1f, ::simpleNotEqual,effect,deps)
}
fun <T> useHookEffect(effect: (e: EffectEvent<Nothing?, List<T>>) -> EffectDestroy<Nothing?, List<T>>, deps: List<T>){
    useBaseEffect(1f, ::arrayNotEqual,effect,deps)
}
fun useHookEffect(effect: EffectSelf){
    useEffect(effect,effect)
}
