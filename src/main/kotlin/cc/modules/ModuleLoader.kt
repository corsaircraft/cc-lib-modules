package cc.modules

import java.lang.IllegalStateException
import java.lang.reflect.Constructor
import java.lang.reflect.Type
import java.util.SplittableRandom
import one.wabbit.data.Need
import one.wabbit.data.closure
import one.wabbit.data.shuffle
import one.wabbit.graph.toposort.Graph
import one.wabbit.reflection.supertypes
import org.bukkit.event.Listener

private class ModuleInstantiationException(message: String, cause: Throwable) :
    Exception(message, cause)

private class MoreThanOneConstructor(val moduleClass: Class<out PluginModule>) :
    Exception("More than one constructor in class $moduleClass")

private class UnknownModuleType(val type: Class<out PluginModule>) :
    Exception("Unknown module type: $type")

private class UnknownModulePort(
    val consumer: Class<out PluginModule>,
    val port: Class<*>,
) : Exception("Unknown module port for ${consumer.name}: ${port.name}")

private class DuplicateModulePort(
    val port: Class<*>,
    val left: Class<out PluginModule>,
    val right: Class<out PluginModule>,
) : Exception("Multiple modules provide port ${port.name}: ${left.name}, ${right.name}")

private class UnknownType(val type: Type) : Exception("Unknown type: $type")

private fun isModulePort(type: Class<*>): Boolean =
    type.isInterface && type.isAnnotationPresent(ModulePort::class.java)

private fun modulePorts(moduleClass: Class<out PluginModule>): Set<Class<*>> =
    moduleClass.supertypes().filter(::isModulePort).toSet()

@Throws(MoreThanOneConstructor::class)
private fun moduleDependencies(
    moduleClass: Class<out PluginModule>,
    providers: Map<Class<*>, Class<out PluginModule>> = emptyMap(),
): List<Class<out PluginModule>> {
    try {
        val constructors = moduleClass.declaredConstructors

        if (constructors.size != 1) {
            throw MoreThanOneConstructor(moduleClass)
        }
        val constructor = constructors[0]

        return constructor.parameterTypes.mapNotNull {
            when {
                PluginModule::class.java.isAssignableFrom(it) -> it.asSubclass(PluginModule::class.java)
                isModulePort(it) -> providers[it] ?: throw UnknownModulePort(moduleClass, it)
                else -> null
            }
        }
    } catch (e: Throwable) {
        if (e is VirtualMachineError) throw e
        throw ModuleInstantiationException(
            "Failed to instantiate module '$moduleClass': ${e.message}",
            e,
        )
    }
}

sealed class ClassMatcher<T> {
    data class Exact<T>(val type: Class<T>) : ClassMatcher<T>()

    data class Subtype<T>(val type: Class<T>) : ClassMatcher<T>()

    data class ListOf<T>(val type: Class<T>) : ClassMatcher<T>()
}

interface ListenerRegistration {
    fun register(l: Listener): Unit

    fun unregister(l: Listener): Unit

    fun onModuleLoad(kClass: Class<out PluginModule>) {}

    fun onModuleLoaded(module: PluginModule) {}

    fun onModuleStart(module: PluginModule) {}

    fun onModuleStarted(module: PluginModule) {}
}

// val owner: JavaPlugin
class ModuleLoader(val registration: ListenerRegistration) {
    // Type axis:
    // - Global components: These are components that are created once and are available to all
    // modules.
    // - Per-module components: These are components that are created once per module and are
    // available to that module.
    // - Modules themselves: These are components that are modules themselves.
    //
    // Lazy/eager axis:
    //   + Lazy components: Initialized on first use.
    //   + Eager components: Initialized immediately.
    //
    // Along the matching method axis:
    // + Exact components: These are components that match the type exactly
    //   (e.g. JavaPlugin::class.java resolves to the current plugin).
    // + Subtype components: These are components that match any subtype of a class
    //   (e.g. Any subtype of JavaPlugin::class.java resolves can be used to resolve a plugin).
    //
    // |         | Global     | Per-module | Module   |
    // |---------|------------|------------|----------|
    // | Exact   | Lazy/Eager |   Lazy     |   Lazy   |
    // | Subtype | Lazy       |   Lazy     |   N/A    |

    private val unloadOrder = mutableListOf<Class<*>>()

    private class GlobalComponent<T : Any>(
        val type: Class<out T>,
        val value: Need<T>,
        val destruct: (Class<out T>, T) -> Unit,
    )

    private class GlobalComponentInitializer<T>(
        val create: (Class<out T>) -> T,
        val destruct: (Class<out T>, T) -> Unit,
    )

    private val global = mutableMapOf<Class<*>, GlobalComponent<*>>()

    // private val globalExactInitializers = mutableMapOf<Class<*>,
    // PerModuleComponentInitializer<*>>()
    private val globalSubtypeInitializers = mutableMapOf<Class<*>, GlobalComponentInitializer<*>>()
    private val globalListInitializers = mutableMapOf<Class<*>, GlobalComponentInitializer<*>>()

    fun <T : Any> global(
        clazz: ClassMatcher<T>,
        destructor: (Class<out T>, T) -> Unit = { _, _ -> },
        supplier: (Class<out T>) -> T,
    ) {
        when (clazz) {
            is ClassMatcher.Exact -> {
                val subtypeClass: Class<out T> = clazz.type.asSubclass(clazz.type)
                val value = Need.apply { supplier(subtypeClass) }
                global[subtypeClass] = GlobalComponent(subtypeClass, value, destructor)
                unloadOrder.add(subtypeClass)
            }
            is ClassMatcher.Subtype -> {
                val subtypeClass: Class<out T> = clazz.type.asSubclass(clazz.type)
                globalSubtypeInitializers[subtypeClass] =
                    GlobalComponentInitializer(supplier, destructor)
            }
            is ClassMatcher.ListOf -> {
                val subtypeClass: Class<out T> = clazz.type.asSubclass(clazz.type)
                globalListInitializers[subtypeClass] =
                    GlobalComponentInitializer(supplier, destructor)
            }
        }
    }

    private class Subcomponent<T : Any>(
        val moduleType: Class<out PluginModule>,
        val componentType: Class<T>,
        val value: T,
        val destruct: (PluginModule, Class<out T>, T) -> Unit,
    )

    private data class SubcomponentInitializer<T>(
        val create: (Class<out PluginModule>, Class<out T>) -> T,
        val destruct: (PluginModule, Class<out T>, T) -> Unit,
    )

    private val exactSubcomponents = mutableMapOf<Class<*>, SubcomponentInitializer<*>>()
    private val subtypeComponents = mutableMapOf<Class<*>, SubcomponentInitializer<*>>()
    private val listComponents = mutableMapOf<Class<*>, SubcomponentInitializer<*>>()

    fun <T> subcomponent(
        clazz: ClassMatcher<T>,
        destructor: (PluginModule, Class<out T>, T) -> Unit = { _, _, _ -> },
        supplier: (Class<out PluginModule>, Class<out T>) -> T,
    ) {
        when (clazz) {
            is ClassMatcher.Exact -> {
                val subtypeClass: Class<out T> = clazz.type.asSubclass(clazz.type)
                exactSubcomponents[subtypeClass] = SubcomponentInitializer<T>(supplier, destructor)
            }
            is ClassMatcher.Subtype -> {
                val subtypeClass: Class<out T> = clazz.type.asSubclass(clazz.type)
                subtypeComponents[subtypeClass] = SubcomponentInitializer<T>(supplier, destructor)
            }
            is ClassMatcher.ListOf -> {
                val subtypeClass: Class<out T> = clazz.type.asSubclass(clazz.type)
                listComponents[subtypeClass] = SubcomponentInitializer<T>(supplier, destructor)
            }
        }
    }

    private enum class ModuleState {
        ALLOCATED,
        STARTED,
    }

    private class ModuleComponent<T : PluginModule>(
        var state: ModuleState,
        val moduleType: Class<T>,
        var ref: T,
        val dependencies: MutableMap<Class<*>, Subcomponent<*>>,
    )

    private val modules = mutableMapOf<Class<out PluginModule>, ModuleComponent<*>>()
    private val modulePortProviders = mutableMapOf<Class<*>, Class<out PluginModule>>()
    private val startOrder = mutableListOf<Class<out PluginModule>>()

    fun allLoadedModules(): List<PluginModule> = modules.values.map { it.ref }

    private fun knownModulePortProviders(
        additionalModules: Collection<Class<out PluginModule>> = emptyList()
    ): Map<Class<*>, Class<out PluginModule>> {
        val result = modulePortProviders.toMutableMap()

        for (moduleClass in additionalModules) {
            for (port in modulePorts(moduleClass)) {
                val existing = result[port]
                if (existing != null && existing != moduleClass) {
                    throw DuplicateModulePort(port, existing, moduleClass)
                }
                result[port] = moduleClass
            }
        }

        return result
    }

    private fun registerModulePorts(moduleClass: Class<out PluginModule>) {
        for (port in modulePorts(moduleClass)) {
            val existing = modulePortProviders[port]
            if (existing != null && existing != moduleClass) {
                throw DuplicateModulePort(port, existing, moduleClass)
            }
            modulePortProviders[port] = moduleClass
        }
    }

    @Throws(UnknownModuleType::class, UnknownType::class)
    private fun <T : Any> getArg(
        moduleType: Class<out PluginModule>,
        subcomponents: MutableMap<Class<*>, Subcomponent<*>>,
        type: Class<T>,
    ): T {
        // Try global components first
        @Suppress("UNCHECKED_CAST") val globalComponent = global[type] as? GlobalComponent<T>
        if (globalComponent != null) {
            return globalComponent.value.value
        }

        val parents = type.supertypes()
        for (parent in parents) {
            @Suppress("UNCHECKED_CAST")
            val globalComponent =
                globalSubtypeInitializers[parent] as? GlobalComponentInitializer<T>
            if (globalComponent != null) {
                val result = globalComponent.create(type)
                global[type] = GlobalComponent(type, Need.now(result), globalComponent.destruct)
                unloadOrder.add(type)
                return result
            }
        }

        // Find exact subcomponent providers first.
        @Suppress("UNCHECKED_CAST")
        val supplier = exactSubcomponents[type] as? SubcomponentInitializer<T>
        if (supplier != null) {
            val result = supplier.create(moduleType, type)
            subcomponents[type] = Subcomponent(moduleType, type, result, supplier.destruct)
            return result
        }

        // Find subtype subcomponent providers.
        for (parent in parents) {
            @Suppress("UNCHECKED_CAST")
            val supplier = subtypeComponents[parent] as? SubcomponentInitializer<T>
            if (supplier != null) {
                val result = supplier.create(moduleType, type)
                subcomponents[type] = Subcomponent(moduleType, type, result, supplier.destruct)
                return result
            }
        }

        if (isModulePort(type)) {
            val moduleClass = modulePortProviders[type] ?: throw UnknownModulePort(moduleType, type)
            val module = modules[moduleClass]?.ref ?: throw UnknownModuleType(moduleClass)
            return type.cast(module)
        }

        if (PluginModule::class.java.isAssignableFrom(type)) {
            val clazz = type.asSubclass(PluginModule::class.java)
            val module = modules[clazz]?.ref
            if (module == null) throw UnknownModuleType(clazz)
            return type.cast(module)
        }

        throw UnknownType(type)
    }

    fun <T : Any> get(type: Class<T>): T {
        // Try global components first
        @Suppress("UNCHECKED_CAST") val globalComponent = global[type] as? GlobalComponent<T>
        if (globalComponent != null) {
            return globalComponent.value.value
        }

        val parents = type.supertypes()
        for (parent in parents) {
            @Suppress("UNCHECKED_CAST")
            val globalComponent =
                globalSubtypeInitializers[parent] as? GlobalComponentInitializer<T>
            if (globalComponent != null) {
                val result = globalComponent.create(type)
                global[type] = GlobalComponent(type, Need.now(result), globalComponent.destruct)
                unloadOrder.add(type)
                return result
            }
        }

        if (isModulePort(type)) {
            val moduleClass = modulePortProviders[type] ?: throw UnknownType(type)
            val module = modules[moduleClass]?.ref ?: throw UnknownModuleType(moduleClass)
            return type.cast(module)
        }

        if (PluginModule::class.java.isAssignableFrom(type)) {
            val clazz = type.asSubclass(PluginModule::class.java)
            val module = modules[clazz]?.ref
            if (module == null) throw UnknownModuleType(clazz)
            return type.cast(module)
        }

        throw UnknownType(type)
    }

    @Throws(UnknownModuleType::class, UnknownType::class, MoreThanOneConstructor::class)
    private fun <T : PluginModule> createModule(clazz: Class<T>): ModuleComponent<T> {
        if (modules.containsKey(clazz)) {
            throw IllegalStateException("Module already loaded: $clazz")
        }

        @Suppress("UNCHECKED_CAST")
        val constructors = clazz.declaredConstructors as Array<Constructor<T>>

        if (constructors.size != 1) {
            throw InstantiationException("Too many constructors.")
        }
        val constructor = constructors[0]

        val dependencies: MutableMap<Class<*>, Subcomponent<*>> = mutableMapOf()

        val parameters = constructor.genericParameterTypes
        val arguments =
            parameters
                .map { type ->
                    if (type !is Class<*>) throw UnknownType(type)
                    getArg(clazz, dependencies, type)
                }
                .toTypedArray()

        registration.onModuleLoad(clazz)

        val module =
            try {
                constructor.newInstance(*arguments)
            } catch (e: Throwable) {
                if (e is VirtualMachineError) throw e

                throw ModuleInstantiationException(
                    "Failed to instantiate module '$clazz': ${e.message}",
                    e,
                )
            }

        startOrder.add(clazz)

        try {
            for (f in module.deferredOnLoad) f()
            module.onLoad()
        } catch (e: Throwable) {
            if (e is VirtualMachineError) throw e
            throw ModuleInstantiationException("Failed to load module '$clazz': ${e.message}", e)
        }

        registerModulePorts(clazz)

        val state = ModuleComponent(state = ModuleState.ALLOCATED, clazz, module, dependencies)
        modules[clazz] = state
        unloadOrder.add(clazz)

        registration.onModuleLoaded(module)

        return state
    }

    fun <T : PluginModule> load(clazz: Class<T>, start: Boolean = false): T {
        load(listOf(clazz))
        return get(clazz)
    }

    fun startAll() {
        for (clazz in startOrder) {
            start(clazz)
        }
    }

    private fun start(clazz: Class<out PluginModule>) {
        val module = modules[clazz] ?: throw IllegalStateException("Module not loaded '$clazz'")

        registration.register(module.ref)
        //        owner.server.pluginManager.registerEvents(module, owner)

        registration.onModuleStart(module.ref)
        try {
            for (f in module.ref.deferredOnStart) f()
            module.ref.start()
        } catch (e: Throwable) {
            if (e is VirtualMachineError) throw e
            throw ModuleInstantiationException("Failed to start module '$clazz': ${e.message}", e)
        }

        module.state = ModuleState.STARTED
        registration.onModuleStarted(module.ref)
    }

    private val MODULE_INIT_RANDOM = SplittableRandom()

    fun load(initialModuleList: List<Class<out PluginModule>>) {
        val providers = knownModulePortProviders(initialModuleList)
        val allModules =
            closure(initialModuleList.toList()) { moduleDependencies(it, providers) }.toMutableList()
        val dependencies =
            allModules.flatMap { c -> moduleDependencies(c, providers).map { Pair(c, it) } }.toMutableList()

        // Introduce randomization to test a different initialization order each time.
        shuffle(allModules, MODULE_INIT_RANDOM)
        shuffle(dependencies, MODULE_INIT_RANDOM)

        val graph = Graph(allModules, dependencies)
        val loadOrder = graph.topoSort()
        for (clazz in loadOrder) {
            if (clazz in modules) continue
            createModule(clazz)
        }
    }

    fun unloadAll() {
        val unloadOrder = unloadOrder.reversed()

        for (clazz in unloadOrder) {
            val module = modules[clazz]
            if (module != null) {
                module.ref.stop()
                for (f in module.ref.deferredOnStop.reversed()) f()

                registration.unregister(module.ref)

                for ((_, subcomponent) in module.dependencies) {
                    val c = subcomponent as Subcomponent<Any>
                    subcomponent.destruct(
                        module.ref,
                        subcomponent.componentType,
                        subcomponent.value,
                    )
                }

                module.dependencies.clear()
                modules.remove(clazz)
                continue
            }

            val globalComponent = global[clazz]
            if (globalComponent != null) {
                val c = globalComponent as GlobalComponent<Any>
                c.destruct(c.type, c.value.value)
                global.remove(clazz)
                continue
            }
        }

        this.unloadOrder.clear()
        modules.clear()
        modulePortProviders.clear()
        global.clear()
    }
}
