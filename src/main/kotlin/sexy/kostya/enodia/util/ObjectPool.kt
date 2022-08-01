package sexy.kostya.enodia.util

import org.jctools.queues.MpmcUnboundedXaddArrayQueue
import java.lang.ref.SoftReference

class ObjectPool<T>(
    private val creator: () -> T,
    private val sanitizer: (T) -> Unit,
    maxSize: Int
) {

    private val pool = MpmcUnboundedXaddArrayQueue<SoftReference<T>>(maxSize)

    fun acquire(): T {
        var ref: SoftReference<T>
        while (true) {
            ref = pool.poll() ?: break
            return ref.get() ?: continue
        }
        return creator()
    }

    fun release(item: T) {
        sanitizer(item)
        pool.offer(SoftReference(item))
    }

    inline fun use(crossinline action: (T) -> Unit) {
        val item = acquire()
        try {
            action(item)
        } finally {
            release(item)
        }
    }

}