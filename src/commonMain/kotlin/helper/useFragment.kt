package org.breact.helper

import org.breact.core.useFiber


fun <T> useFragment(render: (T) -> Unit, dep: T) {
    useFiber(null, render, dep)
}