package org.breact.core

interface VirtualDomNode<T> {
    fun useUpdate(props: T)
    fun appendAfter(parent: VirtualDomNode<Any?>?, before: VirtualDomNode<Any?>?)
    fun destroy()
    fun removeFromParent()
}