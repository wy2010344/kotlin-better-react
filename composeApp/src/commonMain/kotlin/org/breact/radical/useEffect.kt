package org.breact.radical

fun <V> V.useBeforeAttrEffectWithDestroy(callback: (V) -> ((V)->Unit)?) {
    org.breact.core.useBeforeAttrEffect(callback, this)
}

fun <V> V.useAttrEffectWithDestroy(callback: (V) -> ((V)->Unit)?) {
    org.breact.core.useAttrEffect(callback, this)
}

fun <V> V.useEffectWithDestroy(callback: (V) -> ((V)->Unit)?) {
    org.breact.core.useEffect(callback, this)
}
fun <V> V.useBeforeAttrEffect(callback: (V) -> Unit) {
    this.useBeforeAttrEffectWithDestroy{
        callback(it)
        null
    }
}
fun <V> V.useAttrEffect(callback: (V) -> Unit) {
    this.useAttrEffectWithDestroy{
        callback(it)
        null
    }
}
fun <V> V.useEffect(callback: (V) -> Unit) {
    this.useEffectWithDestroy{
        callback(it)
        null
    }
}



fun <V, T> V.useDestroy(callback: (V) -> Unit) {
    this.useEffectWithDestroy {
        callback
    }
}