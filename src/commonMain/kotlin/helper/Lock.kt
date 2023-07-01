package helper

import org.breact.core.useEffect


fun useVersionLock(init: Int = 0): Pair<Int, () -> Int> {
    val ref = useRef(0)
    return Pair(ref.get(), fun(): Int {
        val v = ref.get() + 1
        ref.set(v)
        return v
    })
}

fun useIsLaunchLock(): Boolean {
    val ref = useRef(true)
    useEffect({
        ref.set(false)
        null
    }, 1)
    return ref.get()
}