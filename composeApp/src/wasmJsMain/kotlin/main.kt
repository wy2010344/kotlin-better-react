import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val node= document.createElement("div")
    node.textContent="dsssddd"
    ComposeViewport(document.body!!) {
        App()
    }
    val a=BReactScope()
    a.apply {
call()
        9.useMemo(98)
    }
    document.body!!.appendChild(node)
}



class BReactScope{
fun  call(){
println("abc")
}
    fun Int.useMemo(right:Any){
println("add${this}${right}")
    }
}

