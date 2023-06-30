package org.breact.core


internal fun findParentAndBefore(fiber: Fiber) {
    val dom = fiber.dom
    if (dom != null) {
        val prevData = fiber.before.get()
        val parentBefore = if (prevData != null) {
            getCurrentBefore(prevData)
        } else {
            findParentBefore(fiber)
        }
        if (parentBefore != null) {
            dom.appendAfter(parentBefore.first, parentBefore.second)
        }
    }
}

private fun findParentBefore(fiber: Fiber): Pair<VirtualDomNode<Any?>?, VirtualDomNode<Any?>?>? {
    val parent = fiber.parent
    if (parent != null) {
        if (parent.dom != null) {
            return Pair(parent.dom, null)
        }
        val prev = parent.before.get()
        if (prev != null) {
            val dom = getCurrentBefore(prev)
            if (dom != null) {
                return dom
            }
        }
        return findParentBefore(parent)
    }
    return null
}

private fun getParentDomFiber(fiber: Fiber): Fiber {
    var domParentFiber = fiber.parent
    while (domParentFiber?.dom == null) {
        domParentFiber = domParentFiber?.parent
    }
    return domParentFiber
}

private fun getCurrentBefore(fiber: Fiber): Pair<VirtualDomNode<Any?>?, VirtualDomNode<Any?>?>? {
    if (fiber.dom != null) {
        return Pair(getParentDomFiber(fiber).dom, fiber.dom)
    }
    val lastChild = fiber.lastChild.get()
    if (lastChild != null) {
        val dom = getCurrentBefore(lastChild)
        if (dom != null) {
            return dom
        }
    }
    val prev = fiber.before.get()
    if (prev != null) {
        val dom = getCurrentBefore(prev)
        if (dom != null) {
            return dom
        }
    }
    return findParentBefore(fiber)
}