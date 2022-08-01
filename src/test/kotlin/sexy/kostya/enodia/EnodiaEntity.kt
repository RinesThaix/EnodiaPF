package sexy.kostya.enodia

import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import sexy.kostya.enodia.movement.MovementProcessor

class EnodiaEntity(
    entityType: EntityType
) : Entity(entityType) {

    var movementProcessor: MovementProcessor? = null

    override fun update(time: Long) {
        super.update(time)
        movementProcessor?.tick(time)
    }

}