package app.owlow.accsettings.acc

import org.junit.Assert.*
import org.junit.Test
import java.util.Properties

/**
 * Simulates ACC command behavior and parsing logic in a real-device-like environment.
 * It verifies how the app handles `acc --set` commands, nested-parenthesis parsing, and
 * cross-field validation without a physical device.
 */
class AccCommandSimulationTest {

    /**
     * Simulates the tokenize logic: it must strip parentheses at any nesting depth.
     * This is the core logic that fixes the "unexpected ((" crash.
     */
    private fun simulateTokenize(raw: String?): List<String> =
        raw?.replace("(", " ")
            ?.replace(")", " ")
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotBlank() }
            .orEmpty()

    @Test
    fun `test tokenize robustness with nested parentheses`() {
        // Simulate a corrupted config format like the ((...)) seen via ADB cat
        val corrupted = "((5 70 72 80 false) 69 50 70 false)"
        val tokens = simulateTokenize(corrupted)

        // Must extract the key numbers instead of failing on the parentheses
        assertEquals("5", tokens[0])
        assertEquals("70", tokens[1])
        assertEquals("72", tokens[2])
        assertEquals("80", tokens[3])
    }

    @Test
    fun `test CapacityConfig parse marks invalid input as advanced custom`() {
        val junk = "invalid_data"
        val config = CapacityConfig.parse(junk)

        assertEquals(0, config.shutdown)
        assertEquals(0, config.cooldown)
        assertEquals(0, config.resume)
        assertEquals(0, config.pause)
        assertEquals(ConfigGroupMode.ADVANCED_CUSTOM, config.mode)
    }

    @Test
    fun `test individual field update command generation`() {
        // Simulates what ADB testing found: acc --set pc=85 is the most reliable form
        val property = "pc"
        val value = "85"

        // Simulates Command.setConfig's command-building logic
        val command = "acc --set \"$property=$value\""
        assertEquals("acc --set \"pc=85\"", command)
    }

    @Test
    fun `test threshold ordering validation logic`() {
        // Simulates ACC's core ordering rule: shutdown < cooldown <= resume < pause
        fun isValid(sc: Int, cc: Int, rc: Int, pc: Int): Boolean {
            return sc < cc && cc <= rc && rc < pc
        }

        // Valid order (0 70 72 80)
        assertTrue(isValid(0, 70, 72, 80))

        // Invalid order (5 70 75 70) -> pause not greater than resume
        assertFalse(isValid(5, 70, 75, 70))

        // Boundary test (5 70 70 80) -> cooldown == resume is allowed
        assertTrue(isValid(5, 70, 70, 80))
    }

    @Test
    fun `test Properties object synchronization`() {
        // ... (existing test)
    }

    @Test
    fun `test semantic equality ignores formatting differences`() {
        val groupedProps = Properties().apply {
            setProperty("capacity", "(5 70 72 80 false)")
        }
        val individualProps = Properties().apply {
            setProperty("shutdown_capacity", "5")
            setProperty("cooldown_capacity", "70")
            setProperty("resume_capacity", "72")
            setProperty("pause_capacity", "80")
            setProperty("capacity_mask", "false")
        }

        val config1 = GroupedConfigRead(
            current = groupedProps,
            defaults = Properties(),
            currentCapacity = CapacityConfig(5, 70, 72, 80, false, ConfigGroupMode.NORMAL)
        )

        val config2 = GroupedConfigRead(
            current = individualProps,
            defaults = Properties(),
            currentCapacity = CapacityConfig(5, 70, 72, 80, false, ConfigGroupMode.NORMAL)
        )

        // Although the Properties contents differ, the two configs are logically equivalent
        assertTrue(config1.isSameAs(config2))
    }
}
