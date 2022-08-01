package sexy.kostya.enodia

import net.minestom.server.MinecraftServer
import net.minestom.server.collision.BoundingBox
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.event.instance.InstanceChunkLoadEvent
import net.minestom.server.event.instance.InstanceChunkUnloadEvent
import net.minestom.server.event.instance.InstanceRegisteredEvent
import net.minestom.server.event.instance.InstanceUnregisteredEvent
import net.minestom.server.instance.Instance
import sexy.kostya.enodia.movement.MovementProcessingHub
import sexy.kostya.enodia.pathfinding.PathfindingCapabilities
import sexy.kostya.enodia.pathfinding.PathfindingTask
import sexy.kostya.enodia.provider.BlockStateProvider
import sexy.kostya.enodia.provider.BlockStateProviderFactory
import sexy.kostya.enodia.provider.immutable.ImmutableBlockStateProviderFactory
import sexy.kostya.enodia.provider.mutable.MutableBlockStateProviderFactory
import sexy.kostya.enodia.util.ReusablePoint

class EnodiaPF(internal val blockStateProviderFactory: BlockStateProviderFactory<*>) {

    companion object {

        fun forMutableWorlds() = EnodiaPF(MutableBlockStateProviderFactory())

        fun forImmutableWorlds() = EnodiaPF(ImmutableBlockStateProviderFactory())

    }

    var debug = false

    init {
        MinecraftServer.getGlobalEventHandler().addListener(InstanceRegisteredEvent::class.java) { instanceEvent ->
            val instance = instanceEvent.instance
            val provider = blockStateProviderFactory.create(instance)
            instance.eventNode().addListener(InstanceChunkLoadEvent::class.java) { chunkEvent ->
                provider.loadChunk(chunkEvent.chunk)
            }
            instance.eventNode().addListener(InstanceChunkUnloadEvent::class.java) { chunkEvent ->
                provider.unloadChunk(chunkEvent.chunk)
            }
        }
        MinecraftServer.getGlobalEventHandler().addListener(InstanceUnregisteredEvent::class.java) { instanceEvent ->
            blockStateProviderFactory.remove(instanceEvent.instance)
        }
    }

    /**
     * @param start - point from where we start to calculate our path.
     * @param end - destination point.
     * @param range - the distance to the end point we need to reach to consider the calculation of the path completed.
     * @param step - distance between points within the calculated path.
     *                  Decreasing it's value results is more points in the path.
     *                  Increasing it results in less accurate navigation.
     * @param instance - the instance we're calculating path in.
     * @param capabilities - pathfinding capabilities of an entity.
     * @param boundingBox - bounding box of an entity.
     * @param entityPadding - allowable threshold for bounding box collision check.
     * @param maxRangeFromStart - the maximum distance path is allowed to go away from the starting point.
     */
    fun createPathfindingTask(
        start: Point,
        end: Point,
        range: Float,
        step: Float,
        instance: Instance,
        capabilities: PathfindingCapabilities,
        boundingBox: BoundingBox,
        entityPadding: Float = .1F,
        maxRangeFromStart: Float = start.distance(end).toFloat() * 2F
    ) = createPathfindingTask(
        ReusablePoint.fromPoint(start),
        ReusablePoint.fromPoint(end),
        range,
        step,
        blockStateProviderFactory[instance],
        capabilities,
        boundingBox,
        entityPadding,
        maxRangeFromStart
    )

    /**
     * @param start - point from where we start to calculate our path.
     * @param end - destination point.
     * @param range - the distance to the end point we need to reach to consider the calculation of the path completed.
     * @param step - distance between points within the calculated path.
     *                  Decreasing it's value results is more points in the path.
     *                  Increasing it results in less accurate navigation.
     * @param blockStateProvider - block state provider of the instance we're calculating path in.
     * @param capabilities - pathfinding capabilities of an entity.
     * @param boundingBox - bounding box of an entity.
     * @param entityPadding - allowable threshold for bounding box collision check.
     * @param maxRangeFromStart - the maximum distance path is allowed to go away from the starting point.
     */
    fun createPathfindingTask(
        start: ReusablePoint,
        end: ReusablePoint,
        range: Float,
        step: Float,
        blockStateProvider: BlockStateProvider,
        capabilities: PathfindingCapabilities,
        boundingBox: BoundingBox,
        entityPadding: Float = .1F,
        maxRangeFromStart: Float = start.distance(end) * 2F
    ) = PathfindingTask(
        start,
        end,
        range,
        step,
        blockStateProvider,
        capabilities,
        boundingBox,
        entityPadding,
        maxRangeFromStart,
        debug
    )

    /**
     * @param threads - amount of threads for movement concurrent calculation and execution.
     *                  Recommended value is max(2, Runtime.getRuntime().availableProcessors()).
     * @param maxRetries - in case we were unable to calculate complete path to the destination point,
     *                     how many times should we retry before giving up. Increasing it may drastically increase
     *                     CPU usage.
     * @param entitySpeedGetter - function to retrieve entity's speed.
     * @param entityContinueFollowing - function (may be null) to understand whether we want to continue following
     *                                  given target. Default value restricts following of target that's not
     *                                  within 24 blocks range, because path calculation complexity increases
     *                                  quadratically with distance.
     */
    fun initializeMovementProcessingHub(
        threads: Int,
        maxRetries: Int,
        entitySpeedGetter: (Entity) -> Float,
        entityContinueFollowing: (entity: Entity, target: Entity, distanceSquared: Float) -> Boolean = { _, _, ds -> ds <= 24 * 24 }
    ) = MovementProcessingHub(this, threads, maxRetries, entitySpeedGetter, entityContinueFollowing)

}