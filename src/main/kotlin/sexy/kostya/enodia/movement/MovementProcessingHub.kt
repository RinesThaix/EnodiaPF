package sexy.kostya.enodia.movement

import net.minestom.server.entity.Entity
import sexy.kostya.enodia.EnodiaPF
import sexy.kostya.enodia.pathfinding.PathfindingCapabilities
import sexy.kostya.enodia.util.ReusablePoint
import java.util.concurrent.Executors

class MovementProcessingHub internal constructor(
    internal val pf: EnodiaPF,
    threads: Int,
    internal val maxRetries: Int = 5,
    internal val entitySpeedGetter: (Entity) -> Float,
    internal val entityContinueFollowing: ((entity: Entity, target: Entity, distanceSquared: Float) -> Boolean)?,
    internal val entityTeleport: (Entity, point: ReusablePoint) -> Unit
) {

    private var counter = 0
    internal val movementExecutor = Executors.newFixedThreadPool(threads) {
        val thread = Thread(it, "EnodiaPF-MovementHandler-${++counter}")
        thread.isDaemon = true
        thread
    }
    internal val cancellationExecutor = Executors.newScheduledThreadPool(1) {
        val thread = Thread(it, "EnodiaPF-MovementCanceller")
        thread.isDaemon = true
        thread
    }

    fun createMovementProcessor(entity: Entity, pathfindingCapabilities: PathfindingCapabilities) = MovementProcessor(
        entity,
        pathfindingCapabilities,
        this,
        pf.blockStateProviderFactory[entity.instance ?: throw IllegalArgumentException("could not initialize movement processor for entity that's not in any instance")]
    )

}