package sexy.kostya.enodia.pathfinding

import it.unimi.dsi.fastutil.ints.*
import net.minestom.server.MinecraftServer
import net.minestom.server.collision.BoundingBox
import net.minestom.server.particle.Particle
import net.minestom.server.particle.ParticleCreator
import net.minestom.server.utils.PacketUtils
import sexy.kostya.enodia.provider.BlockStateProvider
import sexy.kostya.enodia.util.ReusablePoint
import kotlin.math.*

class PathfindingTask internal constructor(
    start: ReusablePoint,
    end: ReusablePoint,
    rawRequiredRange: Float,
    step: Float,
    private val blockStateProvider: BlockStateProvider,
    private val capabilities: PathfindingCapabilities,
    bb: BoundingBox,
    entityPadding: Float = .1F,
    maxRangeFromStart: Float = start.distance(end) * 2F,
    private val debug: Boolean = false
) {

    companion object {

        private const val MaxRange = 25F
        private const val MinStep = .05F
        private const val SignAbs = (MaxRange / MinStep).toInt()
        private const val CoordinateOffset = SignAbs shl 1
        private const val CoordinateOffsetSquared = CoordinateOffset * CoordinateOffset
        private const val MaxCoordinate = CoordinateOffsetSquared * CoordinateOffset
    }

    private val partial: Boolean
    private val fullDistance = start.distance(end)
    private val maxDistanceFromStartSquared: Float
    private val maxDistanceFromEnd: Float
    private val requiredRange = max(rawRequiredRange, MinStep)
    private val step = max(min(.25F, min(requiredRange, step)), MinStep)
    private val stepSquared: Float

    private val startX = start.x
    private val startY = start.y
    private val startZ = start.z
    private val baseDeltaX = (start.x - end.x)
    private val baseDeltaY = (start.y - end.y)
    private val baseDeltaZ = (start.z - end.z)

    private val bb = bb.contract(entityPadding.toDouble(), 0.0, entityPadding.toDouble())

    private val relatives = IntArrayList(26)

    @Volatile
    private var cancelled: PathfindingResult.CancellationReason? = null

    init {
        require(requiredRange >= MinStep) { "Required range can't be less than $MinStep" }
        this.stepSquared = this.step * this.step
        val maxDistanceFromStart = min(maxRangeFromStart, MaxRange)
        if (fullDistance - requiredRange <= MaxRange) {
            partial = false
            maxDistanceFromEnd = requiredRange
        } else {
            partial = true
            maxDistanceFromEnd = fullDistance - requiredRange - MaxRange
        }
        maxDistanceFromStartSquared = maxDistanceFromStart * maxDistanceFromStart

        if (capabilities.avian || capabilities.aquatic) {
            for (dy in 1 downTo -1) {
                for (dx in -1..1) {
                    for (dz in -1..1) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue
                        }
                        relatives.add(relative(dx, dy, dz))
                    }
                }
            }
        } else {
            val oneDy = ceil(1F / this.step).toInt()
            val halfDy = ceil(.5F / this.step).toInt()
            for (dx in -1..1) {
                for (dz in -1..1) {
                    relatives.add(relative(dx, -oneDy, dz))
                    relatives.add(relative(dx, -halfDy, dz))
                    relatives.add(relative(dx, -1, dz))
                    if (dx == 0 && dz == 0) {
                        continue
                    }
                    relatives.add(relative(dx, 0, dz))
                    relatives.add(relative(dx, oneDy, dz))
                    relatives.add(relative(dx, halfDy, dz))
                }
            }
        }
    }

    fun run(): PathfindingResult {
        val passibilities = Int2ObjectOpenHashMap<Passibility>()
        val cameFrom = Int2IntOpenHashMap()
        cameFrom.defaultReturnValue(-1)
        val visited = IntOpenHashSet()
        val queue = IntHeapPriorityQueue { indexA, indexB ->
            val passibilityComparison = passibilities[indexA].compareTo(passibilities[indexB])
            if (passibilityComparison != 0) {
                passibilityComparison
            } else {
                val priceA = sqrt(indexA.distanceSquaredToStart()) + 2 * sqrt(indexA.distanceSquaredToEnd())
                val priceB = sqrt(indexB.distanceSquaredToStart()) + 2 * sqrt(indexB.distanceSquaredToEnd())
                priceA.compareTo(priceB)
            }
        }
        val startIndex = index(0, 0, 0)
        passibilities[startIndex] = Passibility.Safe
        queue.enqueue(startIndex)
        var best = startIndex
        var bestDist = 1000000F
        var bestPassibility = Passibility.Impassible
        while (!queue.isEmpty) {
            if (cancelled != null) {
                return PathfindingResult(
                    PathfindingResult.Status.CANCELLED,
                    if (cancelled == PathfindingResult.CancellationReason.TIMED_OUT) reconstructPath(cameFrom, best) else emptyList(),
                    cancelled
                )
            }
            val current = queue.dequeueInt()
            val currentX = ((current % CoordinateOffset) - SignAbs) * step
            val trimmed = current / CoordinateOffset
            val currentY = ((trimmed % CoordinateOffset) - SignAbs) * step
            val currentZ = (((trimmed / CoordinateOffset) % CoordinateOffset) - SignAbs) * step

            if (debug) {
                PacketUtils.sendGroupedPacket(
                    MinecraftServer.getConnectionManager().onlinePlayers,
                    ParticleCreator.createParticlePacket(
                        Particle.FLAME,
                        (startX + currentX).toDouble(),
                        (startY + currentY).toDouble(),
                        (startZ + currentZ).toDouble(),
                        0F, 0F, 0F,
                        1
                    )
                )
            }

            val distanceToEndSquared =
                (baseDeltaX + currentX).pow(2) + (baseDeltaY + currentY).pow(2) + (baseDeltaZ + currentZ).pow(2)
            val distanceToEnd = sqrt(distanceToEndSquared)
            if (distanceToEnd <= maxDistanceFromEnd && current.getPassibility(null, null, null) != Passibility.Impassible) {
                return PathfindingResult(
                    if (partial) PathfindingResult.Status.PARTIAL else PathfindingResult.Status.COMPLETED,
                    reconstructPath(cameFrom, current)
                )
            }
            if (currentX * currentX + currentY * currentY + currentZ * currentZ >= maxDistanceFromStartSquared) {
                continue
            }
            val currentPassibility = passibilities[current]
            if (distanceToEndSquared < bestDist || currentPassibility < bestPassibility) {
                best = current
                bestDist = distanceToEndSquared
                bestPassibility = currentPassibility
            }
            val iterator = relatives.intIterator()
            while (iterator.hasNext()) {
                val relative = current + iterator.nextInt()
                if (relative < 0 || relative >= MaxCoordinate) {
                    continue
                }
                if (visited.contains(relative) || passibilities.containsKey(relative)) {
                    continue
                }
                val passibility = relative.getPassibility(startX + currentX, startY + currentY, startZ + currentZ)
                if (passibility == Passibility.Impassible) {
                    continue
                }
                passibilities[relative] = maxOf(currentPassibility, passibility)
                queue.enqueue(relative)
                cameFrom[relative] = current
            }
            visited.add(current)
        }
        return PathfindingResult(PathfindingResult.Status.FAILED, reconstructPath(cameFrom, best))
    }

    fun cancel(reason: PathfindingResult.CancellationReason) {
        cancelled = reason
    }

    private fun reconstructPath(cameFrom: Int2IntMap, endIndex: Int): List<ReusablePoint> {
        val indexedPath = IntArrayList()
        var current = endIndex
        while (current != -1) {
            indexedPath.add(current)
            current = cameFrom[current]
        }
        if (indexedPath.size == 1) {
            return emptyList()
        }
        if (indexedPath.size == 2) {
            return listOf(indexedPath.first().toPoint())
        }
        indexedPath.removeLast()
        indexedPath.reverse()

        if (true) {
            return indexedPath.map { it.toPoint() }
        }

        val result = ArrayList<ReusablePoint>(indexedPath.size)
        val iterator = indexedPath.intIterator()
        val first = iterator.nextInt()
        var px = (first % CoordinateOffset) - SignAbs
        val pt = first / CoordinateOffset
        var py = (pt % CoordinateOffset) - SignAbs
        var pz = ((pt / CoordinateOffset) % CoordinateOffset) - SignAbs
        var dirX = -100
        var dirY = -100
        var dirZ = -100
        while (iterator.hasNext()) {
            val index = iterator.nextInt()
            val nx = (index % CoordinateOffset) - SignAbs
            val nt = index / CoordinateOffset
            val ny = (nt % CoordinateOffset) - SignAbs
            val nz = ((nt / CoordinateOffset) % CoordinateOffset) - SignAbs

            val dx = nx - px
            val dy = ny - py
            val dz = nz - pz
            if (dx != dirX || dy != dirY || dz != dirZ) {
                result.add(
                    ReusablePoint[
                            (startX + px * step),
                            (startY + py * step),
                            (startZ + pz * step),
                    ]
                )
                dirX = dx
                dirY = dy
                dirZ = dz
            }
            px = nx
            py = ny
            pz = nz
        }
        val endX = startX + px * step
        val initialEndY = startY + py * step
        var endY = initialEndY
        val endZ = startZ + pz * step
        while (blockStateProvider.getPassibility(
                bb,
                step,
                capabilities,
                endX, endY, endZ,
                endX, endY, endZ
            ) == Passibility.Impassible && endY >= initialEndY - 10F
        ) {
            endY -= MinStep
        }
        if (endY < initialEndY - 10F) {
            endY = initialEndY
        }
        result.add(ReusablePoint[endX, endY, endZ])
        return result
    }

    private fun index(dx: Int, dy: Int, dz: Int) =
        (dx + SignAbs) + CoordinateOffset * ((dy + SignAbs) + CoordinateOffset * (dz + SignAbs))

    private fun relative(dx: Int, dy: Int, dz: Int) =
        if (dx == 0) {
            if (dy == 0) {
                if (dz == 0) {
                    0
                } else {
                    CoordinateOffsetSquared * dz
                }
            } else {
                if (dz == 0) {
                    CoordinateOffset * dy
                } else {
                    CoordinateOffset * (dy + CoordinateOffset * dz)
                }
            }
        } else {
            if (dy == 0) {
                if (dz == 0) {
                    dx
                } else {
                    dx + CoordinateOffsetSquared * dz
                }
            } else {
                if (dz == 0) {
                    dx + CoordinateOffset * dy
                } else {
                    dx + CoordinateOffset * (dy + CoordinateOffset * dz)
                }
            }
        }

    private fun Int.distanceSquaredToEnd(): Float {
        val dx = (this % CoordinateOffset) - SignAbs
        val trimmed = this / CoordinateOffset
        val dy = (trimmed % CoordinateOffset) - SignAbs
        val dz = ((trimmed / CoordinateOffset) % CoordinateOffset) - SignAbs

        val x = baseDeltaX + dx * step
        val y = baseDeltaY + dy * step
        val z = baseDeltaZ + dz * step
        return x * x + y * y + z * z
    }

    private fun Int.distanceSquaredToStart(): Float {
        val dx = (this % CoordinateOffset) - SignAbs
        val trimmed = this / CoordinateOffset
        val dy = (trimmed % CoordinateOffset) - SignAbs
        val dz = ((trimmed / CoordinateOffset) % CoordinateOffset) - SignAbs

        val x = dx * step
        val y = dy * step
        val z = dz * step
        return x * x + y * y + z * z
    }

    private fun Int.toPoint(): ReusablePoint {
        val dx = (this % CoordinateOffset) - SignAbs
        val trimmed = this / CoordinateOffset
        val dy = (trimmed % CoordinateOffset) - SignAbs
        val dz = ((trimmed / CoordinateOffset) % CoordinateOffset) - SignAbs

        return ReusablePoint[
                startX + dx * step,
                startY + dy * step,
                startZ + dz * step
        ]
    }

    private fun Int.getPassibility(parentX: Float?, parentY: Float?, parentZ: Float?): Passibility {
        val dx = (this % CoordinateOffset) - SignAbs
        val trimmed = this / CoordinateOffset
        val dy = (trimmed % CoordinateOffset) - SignAbs
        val dz = ((trimmed / CoordinateOffset) % CoordinateOffset) - SignAbs
        val x = startX + dx * step
        val y = startY + dy * step
        val z = startZ + dz * step
        return blockStateProvider.getPassibility(
            bb, step, capabilities,
            parentX ?: x, parentY ?: y, parentZ ?: z,
            x, y, z
        )
    }

}