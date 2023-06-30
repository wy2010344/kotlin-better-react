package org.breact.helper

import MapRowRender
import MapTranslate
import useMapF


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

fun <T> useMap(
    array: List<T>,
    getKey: (v: T) -> Any,
    render: (T, Int) -> Unit
) {
    useMapF(null, array, getDefaultTranslate(), { row, i ->
        MapRowRender(
            getKey(row),
            null,
            {
                render(row, i)
            },
            null
        )
    }, null)
}