package sexy.kostya.enodia.pathfinding

data class PathfindingCapabilities(
    val fireResistant: Boolean,
    val aquaphobic: Boolean,
    val aquatic: Boolean,
    val avian: Boolean
) {
    companion object {
        val Default = PathfindingCapabilities(false, true, false, false)
    }
}