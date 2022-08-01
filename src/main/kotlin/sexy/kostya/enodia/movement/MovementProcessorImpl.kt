package sexy.kostya.enodia.movement

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.utils.position.PositionUtils
import sexy.kostya.enodia.movement.importance.MovementImportance
import sexy.kostya.enodia.pathfinding.PathfindingCapabilities
import sexy.kostya.enodia.pathfinding.PathfindingResult
import sexy.kostya.enodia.pathfinding.PathfindingTask
import sexy.kostya.enodia.provider.BlockStateProvider
import sexy.kostya.enodia.util.ReusablePoint
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MovementProcessorImpl internal constructor(
    private val entity: Entity,
    private val pathfindingCapabilities: PathfindingCapabilities,
    private val hub: MovementProcessingHub,
    private val blockStateProvider: BlockStateProvider
) : MovementProcessor {

    private val step = entity.boundingBox.width().coerceAtMost(entity.boundingBox.depth()).toFloat() / 2F
    private var path = mutableListOf<ReusablePoint>()
    private var pathIndex = 0
    private var pathLength = 0F

    private var inProgress: PathfindingTask? = null

    private var lastDestination: ReusablePoint? = null
    private var lastTarget: Entity? = null
    private var lastImportance: MovementImportance? = null
    private var lastRange: Float = 0F

    init {
        if (pathfindingCapabilities.avian || pathfindingCapabilities.aquatic) {
            entity.setNoGravity(true)
        }
    }

    override fun tick(time: Long) {
        val lastTarget = lastTarget
        if (lastTarget != null && (lastTarget.isRemoved || lastTarget.instance != entity.instance)) {
            stop()
            return
        }
        when {
            path.size == 0 -> return

            pathIndex == path.size -> {
                if (inProgress == null) {
                    if (lastTarget != null) {
                        val lastImportance = lastImportance!!
                        val lastRange = lastRange
                        clearLastData(true)
                        goTo(lastTarget, lastImportance, lastRange)
                    } else {
                        clearLastData(true)
                    }
                }
            }

            else -> {
                if (inProgress == null && lastTarget != null) {
                    val last = path.last()
                    val actual = getEntityPosition(lastTarget)
                    if (actual == null) {
                        stop()
                        return
                    }
                    val range = lastRange
                    val importance = lastImportance!!

                    val dist = last.distanceSquared(actual)
                    if (dist > range * range) {
                        val currentPosition = ReusablePoint.fromPoint(entity.position)
                        if (pathLength + sqrt(dist) > 1.5 * currentPosition.distance(actual)) {
                            stop(false)
                            goTo(lastTarget, importance, range)
                            return
                        }
                        currentPosition.release()
                        initialize(last, { getEntityPosition(lastTarget) }, range, importance, 0, false)
                    }
                }
                move()
            }
        }
    }

    private fun move() {
        if (pathIndex >= path.size) {
            return
        }
        var speed = hub.entitySpeedGetter(entity)
        var dest = path[pathIndex]
        var pos = ReusablePoint.fromPoint(entity.position)
        while (true) {
            val dx = dest.x - pos.x
            val dy = dest.y - pos.y
            val dz = dest.z - pos.z
            val distance = sqrt(dx * dx + dz * dz)
            if (speed > distance) {
                pos.release()
                pos = dest
                if (++pathIndex == path.size) {
                    break
                }
                dest = path[pathIndex]
                speed -= distance
            } else if (speed == distance) {
                pos.release()
                pos = dest
                pathIndex++
                break
            } else {
                val radians = atan2(dz, dx)
                pos.x += speed * cos(radians)
                pos.y += dy
                pos.z += speed * sin(radians)
                break
            }
        }
        val from = entity.position
        val dx = pos.x - from.x
        val dz = pos.z - from.z
        entity.refreshPosition(
            Pos(
                pos.x.toDouble(),
                pos.y.toDouble(),
                pos.z.toDouble(),
                PositionUtils.getLookYaw(dx, dz),
                0F
            )
        )
    }

    override fun goTo(destination: Point, importance: MovementImportance, range: Float): Boolean {
        if (hub.entitySpeedGetter(entity) <= 0F) {
            return false
        }
        val reusableDestination = ReusablePoint.fromPoint(destination)
        if (lastDestination != null && lastDestination!!.samePoint(reusableDestination) && lastImportance == importance && lastRange == range) {
            reusableDestination.release()
            return false
        }
        stop()
        lastDestination = reusableDestination
        lastImportance = importance
        lastRange = range

        return initialize(ReusablePoint.fromPoint(entity.position), { reusableDestination }, range, importance, 0, false)
    }

    override fun goTo(target: Entity, importance: MovementImportance, range: Float): Boolean {
        if (lastTarget === target && lastImportance == importance && lastRange == range) {
            return false
        }
        if (hub.entitySpeedGetter(entity) <= 0F) {
            return false
        }
        stop()
        lastTarget = target
        lastImportance = importance
        lastRange = range

        return initialize(ReusablePoint.fromPoint(entity.position), { getEntityPosition(target) }, range, importance, 0, false)
    }

    private fun initialize(
        from: ReusablePoint,
        destination: () -> ReusablePoint?,
        range: Float,
        importance: MovementImportance,
        retries: Int,
        useMaxTime: Boolean
    ): Boolean {
        if (retries == hub.maxRetries) {
            importance.onPathCalculationFailed(entity, destination() ?: return false)
            return true
        }

        val nextDestination = destination() ?: return false
        if (inProgress != null) {
            inProgress?.cancel(PathfindingResult.CancellationReason.OUTDATED)
            inProgress = null
        }
        ReusablePoint.fromPoint(entity.position).use { currentPosition ->
            val distanceSquared = currentPosition.distanceSquared(nextDestination)
            if (distanceSquared <= range * range) {
                clearLastData(true)
                return true
            }
            val lastTarget = lastTarget
            val continueFollowing = hub.entityContinueFollowing
            if (lastTarget != null && !continueFollowing(entity, lastTarget, distanceSquared)) {
                clearLastData(true)
                importance.onPathCalculationFailed(entity, nextDestination)
                return true
            }
        }
        val task = hub.pf.createPathfindingTask(
            from,
            nextDestination,
            range,
            step,
            blockStateProvider,
            pathfindingCapabilities,
            entity.boundingBox,
        )
        inProgress = task
        hub.movementExecutor.execute {
            try {
                val result = task.run()
                entity.scheduleNextTick {
                    if (inProgress === task) {
                        processResult(result, destination, range, importance, retries, useMaxTime)
                    }
                }
            } catch (t: Throwable) {
                MinecraftServer.LOGGER.error("Could not run pathfinding", t)
            }
        }
        hub.cancellationExecutor.schedule({
            task.cancel(PathfindingResult.CancellationReason.TIMED_OUT)
        }, if (useMaxTime) importance.maxExecutionTime else importance.averageExecutionTime, TimeUnit.MILLISECONDS)
        return true
    }

    private fun processResult(
        result: PathfindingResult,
        destination: () -> ReusablePoint?,
        range: Float,
        importance: MovementImportance,
        retries: Int,
        useMaxTime: Boolean
    ) {
        if (result.path.isNotEmpty()) {
            if (pathIndex == path.size) {
                path.clear()
                path.addAll(result.path)
                pathIndex = 0
                ReusablePoint.fromPoint(entity.position).use {
                    pathLength = result.path.length(it)
                }
            } else if (pathIndex != 0) {
                path = path.subList(pathIndex, path.size)
                path.addAll(result.path)
                pathIndex = 0
                ReusablePoint.fromPoint(entity.position).use {
                    pathLength = path.length(it)
                }
            } else {
                path += result.path
                pathLength += result.path.length(path.last())
            }
        }
        inProgress = null
        when (result.status) {
            PathfindingResult.Status.FAILED -> {
                if (result.path.isEmpty()) {
                    destination()?.let { importance.onPathCalculationFailed(entity, it) }
                    stop()
                } else {
                    initialize(path.last(), destination, range, importance, retries + 1, false)
                }
            }

            PathfindingResult.Status.CANCELLED -> {
                if (result.cancellationReason == PathfindingResult.CancellationReason.TIMED_OUT) {
                    if (result.path.isEmpty()) {
                        if (useMaxTime || importance.averageExecutionTime == importance.maxExecutionTime) {
                            stop(destination()?.let { importance.onPathCalculationFailed(entity, it) } == true)
                        } else {
                            initialize(
                                path.lastOrNull() ?: ReusablePoint.fromPoint(entity.position),
                                destination,
                                range,
                                importance,
                                retries + 1,
                                true
                            )
                        }
                    } else {
                        initialize(path.last(), destination, range, importance, retries + 1, false)
                    }
                }
            }

            PathfindingResult.Status.PARTIAL -> {
                if (result.path.isEmpty() && destination()?.let { importance.onPathCalculationFailed(entity, it) } == true) {
                    stop()
                } else {
                    initialize(path.last(), destination, range, importance, retries + 1, false)
                }
            }

            PathfindingResult.Status.COMPLETED -> {
                val last = result.path.lastOrNull() ?: return
                val dest = destination() ?: return
                if (last.distanceSquared(dest) > range * range) {
                    val currentPosition = ReusablePoint.fromPoint(entity.position)
                    if (pathLength + last.distance(dest) > 1.5 * currentPosition.distance(dest)) {
                        val lastTarget = lastTarget
                        if (lastTarget != null) {
                            stop(false)
                            goTo(lastTarget, importance, range)
                        }
                    } else {
                        currentPosition.release()
                        initialize(last, destination, range, importance, 0, false)
                    }
                }
            }
        }
    }

    override fun isActive() = pathIndex != path.size || inProgress != null

    override fun stop(clearPath: Boolean) {
        cancelTaskInProgress()
        clearLastData(clearPath)
    }

    private fun cancelTaskInProgress() {
        val inProgress = inProgress
        if (inProgress != null) {
            inProgress.cancel(PathfindingResult.CancellationReason.OUTDATED)
            this.inProgress = null
        }
    }

    private fun clearLastData(clearPath: Boolean) {
        if (clearPath) {
            for (i in pathIndex until path.size) {
                path[i].release()
            }
            path.clear()
            pathIndex = 0
            pathLength = 0F
        }

        lastDestination = null
        lastTarget = null
        lastImportance = null
        lastRange = 0F
    }

    private fun getEntityPosition(target: Entity): ReusablePoint? {
        if (target.instance != entity.instance) {
            return null
        }
        val pos = ReusablePoint.fromPoint(target.position)
        if (pathfindingCapabilities.avian) {
            pos.y += target.eyeHeight.toFloat()
        }
        return pos
    }

    private fun List<ReusablePoint>.length(from: ReusablePoint): Float {
        var result = 0F
        var current = from
        for (next in this) {
            result += current.distance(next)
            current = next
        }
        return result
    }
}