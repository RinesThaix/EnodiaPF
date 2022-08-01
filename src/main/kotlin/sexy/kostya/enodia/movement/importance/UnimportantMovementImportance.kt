package sexy.kostya.enodia.movement.importance

import net.minestom.server.entity.Entity
import sexy.kostya.enodia.util.ReusablePoint
import java.time.Duration

class UnimportantMovementImportance(
    averageExecutionTime: Long,
    maxExecutionTime: Long
) : MovementImportance(averageExecutionTime, maxExecutionTime) {

    constructor(averageExecutionTime: Duration, maxExecutionTime: Duration) : this(
        averageExecutionTime.toMillis(),
        maxExecutionTime.toMillis()
    )

    override fun onPathCalculationFailed(entity: Entity, destination: ReusablePoint) = false

}