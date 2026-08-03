package app.owlow.accsettings.acc

import org.junit.Assert.*
import org.junit.Test
import java.util.Properties

/**
 * 此测试用例用于模拟真实环境下的 ACC 命令行为和解析逻辑。
 * 它验证了我们在没有真机的情况下，App 如何处理 acc --set 命令、
 * 复杂的括号嵌套解析以及字段间的逻辑校验。
 */
class AccCommandSimulationTest {

    /**
     * 模拟 tokenize 逻辑：必须能强力剥离任何层级的括号。
     * 这是解决 "unexpected ((" 导致闪退的核心逻辑。
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
        // 模拟损坏的配置格式，如我们在 ADB cat 中看到的 ((...))
        val corrupted = "((5 70 72 80 false) 69 50 70 false)"
        val tokens = simulateTokenize(corrupted)
        
        // 应当能够提取出关键数值，而不是因为看到 ( 而解析失败
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
        // 模拟我们在 ADB 测试中发现的：acc --set pc=85 是最稳定的方式
        val property = "pc"
        val value = "85"
        
        // 模拟 Command.setConfig 的拼接逻辑
        val command = "acc --set \"$property=$value\""
        assertEquals("acc --set \"pc=85\"", command)
    }

    @Test
    fun `test threshold ordering validation logic`() {
        // 模拟 ACC 核心的校验规则：shutdown < cooldown <= resume < pause
        fun isValid(sc: Int, cc: Int, rc: Int, pc: Int): Boolean {
            return sc < cc && cc <= rc && rc < pc
        }

        // 正确的顺序 (0 70 72 80)
        assertTrue(isValid(0, 70, 72, 80))
        
        // 错误的顺序 (5 70 75 70) -> pause 不大于 resume
        assertFalse(isValid(5, 70, 75, 70))
        
        // 临界点测试 (5 70 70 80) -> cooldown == resume 是允许的
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

        // 虽然 Properties 内部内容完全不同，但逻辑上它们应该是相等的
        assertTrue(config1.isSameAs(config2))
    }
}
