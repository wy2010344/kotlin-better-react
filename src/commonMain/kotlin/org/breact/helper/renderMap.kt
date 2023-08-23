package org.breact.helper

import org.breact.core.MapRowRender
import org.breact.core.MapTranslate
import org.breact.core.renderMapF


val defaultMapTranslate: MapTranslate<List<Any>, Any> = object : MapTranslate<List<Any>, Any> {
    override fun sizeOf(m: List<Any>): Int {
        return m.size
    }

    override fun getAt(m: List<Any>, i: Int): Any {
        return m[i]
    }
}

fun <T> getDefaultTranslate(): MapTranslate<List<T>, T> {
    return defaultMapTranslate as MapTranslate<List<T>, T>
}

fun <T> renderMap(
    array: List<T>,
    getKey: (v: T,Int) -> Any,
    render: (T, Int) -> Unit
) {
    renderMapF(null, array, getDefaultTranslate(), { row, i ->
        MapRowRender(
            getKey(row,i),
            null,
            {
                render(row, i)
            },
            null
        )
    }, null)
}