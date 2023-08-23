package org.breact.helper

import org.breact.core.renderFiber


fun <T> renderFragment(render: (T) -> Unit, dep: T) {
    renderFiber(null, render, dep)
}