package sexy.kostya.enodia.movement.importance

import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import sexy.kostya.enodia.util.ReusablePoint
import java.time.Duration

class TeleportationMovementImportance(
    averageExecutionTime: Long,
    maxExecutionTime: Long
) : MovementImportance(averageExecutionTime, maxExecutionTime) {

    constructor(averageExecutionTime: Duration, maxExecutionTime: Duration) : this(
        averageExecutionTime.toMillis(),
        maxExecutionTime.toMillis()
    )

    override fun onPathCalculationFailed(entity: Entity, destination: ReusablePoint): Boolean {
        entity.teleport(
            Pos(
                destination.x.toDouble(),
                destination.y.toDouble(),
                destination.z.toDouble(),
                entity.position.yaw,
                entity.position.pitch
            )
        )
        return true
    }

}