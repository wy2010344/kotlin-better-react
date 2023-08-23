package org.breact.radical


fun <V> V.renderFragment(render: (V) -> Unit){
    org.breact.helper.renderFragment(render,this)
}