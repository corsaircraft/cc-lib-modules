package cc.modules

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@ModulePort
private interface TestClock {
    fun now(): Long
}

private class TestClockModule : PluginModule(), TestClock {
    override fun now(): Long = 42L
}

private class TestClockConsumer(
    val clock: TestClock,
) : PluginModule()

private class OtherTestClockModule : PluginModule(), TestClock {
    override fun now(): Long = 7L
}

private object NoopListenerRegistration : ListenerRegistration {
    override fun register(l: org.bukkit.event.Listener) = Unit

    override fun unregister(l: org.bukkit.event.Listener) = Unit
}

class ModuleLoaderSpec {
    @Test
    fun `annotated module ports resolve to loaded module implementations`() {
        val loader = ModuleLoader(NoopListenerRegistration)

        loader.load(listOf(TestClockModule::class.java, TestClockConsumer::class.java))

        val consumer = loader.get(TestClockConsumer::class.java)
        assertEquals(42L, consumer.clock.now())
    }

    @Test
    fun `loading a second provider for the same module port fails loudly`() {
        val loader = ModuleLoader(NoopListenerRegistration)

        val error =
            assertFailsWith<Exception> {
                loader.load(listOf(TestClockModule::class.java, OtherTestClockModule::class.java))
            }

        assertTrue(error.message!!.contains(TestClock::class.java.name))
        assertTrue(error.message!!.contains(TestClockModule::class.java.name))
        assertTrue(error.message!!.contains(OtherTestClockModule::class.java.name))
    }
}
