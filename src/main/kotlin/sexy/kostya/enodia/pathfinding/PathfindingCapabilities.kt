package sexy.kostya.enodia.pathfinding

data class PathfindingCapabilities(
    /**
     * if set to true, entity will not be scared of going through the igniting environment (lava, fire, magma, etc).
     */
    val fireResistant: Boolean,
    /**
     * if set to true, entity will try to avoid any kind of liquids.
     */
    val aquaphobic: Boolean,
    /**
     * if set to true, entity will not be able to move outside of liquids.
     */
    val aquatic: Boolean,
    /**
     * it set to true, entity can fly.
     */
    val avian: Boolean
) {

    companion object {

        val Default = PathfindingCapabilities(
            fireResistant = false,
            aquaphobic = true,
            aquatic = false,
            avian = false
        )
    }
}