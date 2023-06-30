package org.breact.helper

import org.breact.core.OneProps
import org.breact.core.useOneF

fun <T> useOne(key: T, render: (T) -> Unit) {
    useOneF(null, key, {
        OneProps(
            key as Any,
            null,
            {
                render(key)
                null
            },
            null
        )
    }, null)
}