package org.breact.core

abstract class VirtualDomNode<T>(
    val isPortal:Boolean=false
) {
    abstract fun useUpdate(props: T)
    abstract fun appendAfter(parent: VirtualDomNode<Any?>?, before: VirtualDomNode<Any?>?)
    abstract fun destroy()
    abstract fun removeFromParent()
}