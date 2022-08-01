package sexy.kostya.enodia.provider

import net.minestom.server.instance.Instance

abstract class BlockStateProviderFactory<P : BlockStateProvider>(
    private val instanceProviders: MutableMap<Instance, P>
) {

    protected abstract fun initializeProvider(instance: Instance): P

    fun create(instance: Instance): P {
        val provider = initializeProvider(instance)
        instanceProviders[instance] = provider
        return provider
    }

    operator fun get(instance: Instance) = instanceProviders[instance]!!

    open fun remove(instance: Instance) = instanceProviders.remove(instance)

}