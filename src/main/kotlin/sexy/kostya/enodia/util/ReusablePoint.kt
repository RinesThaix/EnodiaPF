package sexy.kostya.enodia.util

import net.minestom.server.coordinate.Point
import kotlin.math.sqrt

class ReusablePoint private constructor() : AutoCloseable {

    companion object {

        private val Pool = ObjectPool(::ReusablePoint, {}, 1024)

        operator fun get(x: Float, y: Float, z: Float): ReusablePoint {
            val result = Pool.acquire()
            result.x = x
            result.y = y
            result.z = z
            return result
        }

        fun fromPoint(point: Point) = this[
                point.x().toFloat(),
                point.y().toFloat(),
                point.z().toFloat()
        ]

    }

    var x = 0F
    var y = 0F
    var z = 0F

    fun release() = Pool.release(this)

    fun distanceSquared(other: ReusablePoint): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return dx * dx + dy * dy + dz * dz
    }

    fun distance(other: ReusablePoint) = sqrt(distanceSquared(other))

    fun samePoint(other: ReusablePoint) = x.compareTo(other.x) == 0 && y.compareTo(other.y) == 0 && z.compareTo(other.z) == 0

    override fun close() = release()

    override fun toString() = "ReusablePoint(x=$x, y=$y, z=$z)"

}