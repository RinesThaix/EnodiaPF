package sexy.kostya.enodia

import net.minestom.server.MinecraftServer
import net.minestom.server.attribute.Attribute
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.arguments.ArgumentType
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.*
import net.minestom.server.event.player.PlayerLoginEvent
import net.minestom.server.instance.block.Block
import sexy.kostya.enodia.movement.importance.MovementImportance
import sexy.kostya.enodia.pathfinding.PathfindingCapabilities
import kotlin.math.max

fun main() {
    val server = MinecraftServer.init()

    val enodia = EnodiaPF.forMutableWorlds()
    val hub = enodia.initializeMovementProcessingHub(
        max(2, Runtime.getRuntime().availableProcessors()),
        5,
        { if (it is LivingEntity) it.getAttributeValue(Attribute.MOVEMENT_SPEED) else Attribute.MOVEMENT_SPEED.defaultValue },
        { _, _, _ -> true }
    )

    val instance = MinecraftServer.getInstanceManager().createInstanceContainer()
    instance.setGenerator {
        val mod = it.modifier()
        mod.fillHeight(0, 20, Block.STONE)
        mod.fillHeight(20, 21, Block.GRASS_BLOCK)
        if (it.absoluteStart().x() == 16.0 && it.absoluteStart().z() == 16.0) {
            mod.fill(
                Vec(16.0, 1.0, 16.0),
                Vec(31.0, 21.0, 31.0),
                Block.WATER
            )
        }
    }

    MinecraftServer.getGlobalEventHandler().addListener(PlayerLoginEvent::class.java) {
        it.setSpawningInstance(instance)
        it.player.respawnPoint = Pos(0.0, 21.0, 0.0, 0F, 0F)
        it.player.gameMode = GameMode.CREATIVE
    }

    val entities = mutableListOf<EnodiaEntity>()

    MinecraftServer.getCommandManager().register(Command("enodia").apply {
        addSubcommand(Command("add").apply {
            val typeArg = ArgumentType.Word("type").from("default", "aquatic", "not-aquaphobic", "avian")
            addSyntax({ sender, ctx ->
                if (sender !is Player) {
                    return@addSyntax
                }
                val type: EntityType
                val capabilities: PathfindingCapabilities
                when (ctx[typeArg]) {
                    "aquatic" -> {
                        type = EntityType.DOLPHIN
                        capabilities = PathfindingCapabilities.Default.copy(aquatic = true, aquaphobic = false)
                    }
                    "not-aquaphobic" -> {
                        type = EntityType.DROWNED
                        capabilities = PathfindingCapabilities.Default.copy(aquaphobic = false)
                    }
                    "avian" -> {
                        type = EntityType.BEE
                        capabilities = PathfindingCapabilities.Default.copy(avian = true)
                    }
                    else -> {
                        type = EntityType.IRON_GOLEM
                        capabilities = PathfindingCapabilities.Default
                    }
                }
                val entity = EnodiaEntity(type)
                entity.setInstance(instance, sender.position).thenRun {
                    entity.movementProcessor = hub.createMovementProcessor(entity, capabilities)
                    synchronized(entities) {
                        entities.add(entity)
                    }
                }
            }, typeArg)
        })
        addSubcommand(Command("remove", "rem").apply {
            addSyntax({_, _ ->
                synchronized(entities) {
                    entities.removeLastOrNull()?.remove()
                }
            })
        })
        addSubcommand(Command("follow").apply {
            addSyntax({ sender, _ ->
                if (sender !is Player) {
                    return@addSyntax
                }
                synchronized(entities) {
                    entities.forEach { it.movementProcessor!!.goTo(sender, MovementImportance.UNIMPORTANT, 2F) }
                }
            })
        })
        addSubcommand(Command("stop").apply {
            addSyntax({ _, _ ->
                synchronized(entities) {
                    entities.forEach { it.movementProcessor!!.stop(true) }
                }
            })
        })
    })

    server.start("127.0.0.1", 25565)
}