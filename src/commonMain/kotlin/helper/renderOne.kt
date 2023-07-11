package org.breact.helper

import org.breact.core.OneProps
import org.breact.core.renderOneF

fun <T> renderOne(key: T, render: (T) -> Unit) {
    renderOneF(null, key, {
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