package com.heirloom.engine.sim

import com.heirloom.engine.T
import com.heirloom.engine.model.ActiveEvent
import com.heirloom.engine.model.ActivityId
import com.heirloom.engine.model.Era
import com.heirloom.engine.model.EventType
import com.heirloom.engine.model.HeirloomId
import com.heirloom.engine.model.LevelProgress
import com.heirloom.engine.model.SkillId
import com.heirloom.engine.model.VentureId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The yield formula, factor by factor and composed. A new game starts in spring with no
 * modifiers, so FORAGE's baseline daily yield is exactly its configured base.
 */
class YieldCalculatorTest {
    private val cfg = T.quiet
    private val base = T.newState()

    @Test
    fun `unmodified yield equals the configured base`() {
        assertEquals(
            cfg.activityBaseYields.getValue(ActivityId.FORAGE),
            YieldCalculator.dailyYield(base, ActivityId.FORAGE, cfg),
            1e-12,
        )
    }

    @Test
    fun `activity levels make the activity itself faster`() {
        val leveled = base.copy(activityProgress = mapOf(ActivityId.FORAGE to LevelProgress(10)))
        val expected = cfg.activityBaseYields.getValue(ActivityId.FORAGE) * (1.0 + 0.08 * 10)
        assertEquals(expected, YieldCalculator.dailyYield(leveled, ActivityId.FORAGE, cfg), 1e-9)
    }

    @Test
    fun `grit speeds up all work and the resource skill stacks on top`() {
        val skilled = base.copy(
            skillProgress = mapOf(SkillId.GRIT to LevelProgress(10), SkillId.HUSBANDRY to LevelProgress(20)),
        )
        val expected = (1.0 + 0.015 * 10) * (1.0 + 0.03 * 20)
        assertEquals(expected, YieldCalculator.skillYieldFactor(skilled, ActivityId.FORAGE, cfg), 1e-12)
        // Money work has no dedicated yield skill; only Grit applies.
        assertEquals(
            1.0 + 0.015 * 10,
            YieldCalculator.skillYieldFactor(skilled, ActivityId.HAUL_CARGO, cfg),
            1e-12,
        )
    }

    @Test
    fun `craftsmanship boosts materials and community boosts standing`() {
        val skilled = base.copy(
            skillProgress = mapOf(
                SkillId.CRAFTSMANSHIP to LevelProgress(15),
                SkillId.COMMUNITY to LevelProgress(10),
            ),
        )
        assertEquals(1.0 + 0.02 * 15, YieldCalculator.skillYieldFactor(skilled, ActivityId.SCOUT_AHEAD, cfg), 1e-12)
        assertEquals(1.0 + 0.03 * 10, YieldCalculator.skillYieldFactor(skilled, ActivityId.TEACH_SCHOOL, cfg), 1e-12)
    }

    @Test
    fun `heirlooms stack multiplicatively per resource plus global pieces`() {
        val s = base.copy(
            heirlooms = setOf(
                HeirloomId.IRON_STOVE, HeirloomId.HUNTING_RIFLE,
                HeirloomId.OXEN_YOKE, HeirloomId.PIONEERS_JOURNAL,
            ),
        )
        val expected = 1.10 * 1.10 * 1.30 * 1.20
        assertEquals(expected, YieldCalculator.heirloomYieldFactor(s, com.heirloom.engine.model.ResourceType.FOOD, cfg), 1e-12)
        // Materials sees only the global pair without Father's Tools.
        assertEquals(1.10 * 1.10, YieldCalculator.heirloomYieldFactor(s, com.heirloom.engine.model.ResourceType.MATERIALS, cfg), 1e-12)
    }

    @Test
    fun `family ventures boost their resource`() {
        val s = base.copy(ventures = mapOf(VentureId.CATTLE_BRAND to 2, VentureId.SEAT_AT_THE_BANK to 1))
        assertEquals(2.0, YieldCalculator.ventureYieldFactor(s, com.heirloom.engine.model.ResourceType.FOOD, cfg), 1e-12)
        assertEquals(1.75, YieldCalculator.ventureYieldFactor(s, com.heirloom.engine.model.ResourceType.MONEY, cfg), 1e-12)
        assertEquals(1.0, YieldCalculator.ventureYieldFactor(s, com.heirloom.engine.model.ResourceType.MATERIALS, cfg), 1e-12)
    }

    @Test
    fun `seasons modify tagged activities only`() {
        val spring = base.copy(totalGameDays = 0.0)
        val fall = base.copy(totalGameDays = 14.0)
        val winter = base.copy(totalGameDays = 21.0)

        // Spring: planting +20%; non-planting unaffected.
        assertEquals(1.20, YieldCalculator.seasonFactor(spring, ActivityId.TEND_GARDEN, cfg), 1e-12)
        assertEquals(1.0, YieldCalculator.seasonFactor(spring, ActivityId.HUNT, cfg), 1e-12)
        // Fall: harvest +50%.
        assertEquals(1.50, YieldCalculator.seasonFactor(fall, ActivityId.HARVEST, cfg), 1e-12)
        assertEquals(1.0, YieldCalculator.seasonFactor(fall, ActivityId.HUNT, cfg), 1e-12)
        // Winter: outdoor -60%; indoor unaffected.
        assertEquals(0.40, YieldCalculator.seasonFactor(winter, ActivityId.HUNT, cfg), 1e-12)
        assertEquals(1.0, YieldCalculator.seasonFactor(winter, ActivityId.BLACKSMITH, cfg), 1e-12)
    }

    @Test
    fun `old almanac softens the winter outdoor penalty`() {
        val winter = base.copy(totalGameDays = 21.0, ventures = mapOf(VentureId.OLD_ALMANAC to 1))
        assertEquals(1.0 - 0.60 * 0.75, YieldCalculator.seasonFactor(winter, ActivityId.HUNT, cfg), 1e-12)
    }

    @Test
    fun `drought halves food work and good rains boost it`() {
        val drought = base.copy(activeEvents = listOf(ActiveEvent(EventType.DROUGHT, 7.0)))
        val mitigated = base.copy(activeEvents = listOf(ActiveEvent(EventType.DROUGHT, 7.0, mitigated = true)))
        val rains = base.copy(activeEvents = listOf(ActiveEvent(EventType.GOOD_RAINS, 7.0)))
        assertEquals(0.5, YieldCalculator.resourceEventFactor(drought, com.heirloom.engine.model.ResourceType.FOOD, cfg), 1e-12)
        assertEquals(0.75, YieldCalculator.resourceEventFactor(mitigated, com.heirloom.engine.model.ResourceType.FOOD, cfg), 1e-12)
        assertEquals(1.30, YieldCalculator.resourceEventFactor(rains, com.heirloom.engine.model.ResourceType.FOOD, cfg), 1e-12)
        assertEquals(1.0, YieldCalculator.resourceEventFactor(drought, com.heirloom.engine.model.ResourceType.MATERIALS, cfg), 1e-12)
    }

    @Test
    fun `the railroad doubles money work for the rest of the life`() {
        val s = base.copy(railroadArrivedThisLife = true)
        assertEquals(2.0, YieldCalculator.resourceEventFactor(s, com.heirloom.engine.model.ResourceType.MONEY, cfg), 1e-12)
        assertEquals(1.0, YieldCalculator.resourceEventFactor(s, com.heirloom.engine.model.ResourceType.FOOD, cfg), 1e-12)
    }

    @Test
    fun `global factor composes era posterity monuments supporter starvation and bounty`() {
        val s = base.copy(
            era = Era.CLAIM,
            posterity = 50.0,
            monuments = 2,
            supporter = true,
            starving = true,
            springBountyActive = true,
            totalGameDays = 0.0, // spring, so the bounty applies
        )
        val expected = (1.0 + 0.25) * (1.0 + 0.02 * 50) * (1.0 + 1.0 * 2) * 1.25 * 2.0 * 0.5
        assertEquals(expected, YieldCalculator.globalFactor(s, cfg), 1e-9)
    }

    @Test
    fun `spring bounty does not apply outside spring`() {
        val summer = base.copy(springBountyActive = true, totalGameDays = 7.0)
        assertEquals(1.0, YieldCalculator.globalFactor(summer, cfg), 1e-12)
    }

    @Test
    fun `the full formula is the product of its parts`() {
        val s = base.copy(
            era = Era.HOMESTEAD,
            totalGameDays = 14.0, // fall
            activityProgress = mapOf(ActivityId.HARVEST to LevelProgress(5)),
            skillProgress = mapOf(SkillId.GRIT to LevelProgress(8), SkillId.HUSBANDRY to LevelProgress(12)),
            heirlooms = setOf(HeirloomId.IRON_STOVE),
            ventures = mapOf(VentureId.CATTLE_BRAND to 1),
            posterity = 100.0,
        )
        val expected = cfg.activityBaseYields.getValue(ActivityId.HARVEST) *
            (1.0 + 0.08 * 5) *
            (1.0 + 0.015 * 8) * (1.0 + 0.03 * 12) *
            1.30 *
            1.50 *
            1.50 *
            (1.0 + 0.25 * 2) * (1.0 + 0.02 * 100)
        assertEquals(expected, YieldCalculator.dailyYield(s, ActivityId.HARVEST, cfg), 1e-6)
    }

    // ------------------------------------------------------------ food upkeep

    @Test
    fun `upkeep scales with era`() {
        assertEquals(1.0, YieldCalculator.dailyFoodUpkeep(base, cfg), 1e-12)
        val homestead = base.copy(era = Era.HOMESTEAD)
        assertEquals(2.5 * 2.5, YieldCalculator.dailyFoodUpkeep(homestead, cfg), 1e-12)
    }

    @Test
    fun `winter triples the drain and a hard winter quadruples it`() {
        val winter = base.copy(totalGameDays = 21.0)
        assertEquals(3.0, YieldCalculator.dailyFoodUpkeep(winter, cfg), 1e-12)
        val hard = winter.copy(activeEvents = listOf(ActiveEvent(EventType.HARD_WINTER, 7.0)))
        assertEquals(4.0, YieldCalculator.dailyFoodUpkeep(hard, cfg), 1e-12)
        val softened = winter.copy(activeEvents = listOf(ActiveEvent(EventType.HARD_WINTER, 7.0, mitigated = true)))
        assertEquals(3.5, YieldCalculator.dailyFoodUpkeep(softened, cfg), 1e-12)
    }

    @Test
    fun `recipes and almanac soften winter and thrift trims everything`() {
        val winter = base.copy(totalGameDays = 21.0, heirlooms = setOf(HeirloomId.MOTHERS_RECIPES))
        assertEquals(3.0 * 0.8, YieldCalculator.dailyFoodUpkeep(winter, cfg), 1e-12)

        val almanacWinter = base.copy(totalGameDays = 21.0, ventures = mapOf(VentureId.OLD_ALMANAC to 1))
        assertEquals(1.0 + 2.0 * 0.75, YieldCalculator.dailyFoodUpkeep(almanacWinter, cfg), 1e-12)

        val thrifty = base.copy(skillProgress = mapOf(SkillId.THRIFT to LevelProgress(25)))
        assertEquals(1.0 / (1.0 + 0.008 * 25), YieldCalculator.dailyFoodUpkeep(thrifty, cfg), 1e-12)
    }

    // ------------------------------------------------------------- skill XP

    @Test
    fun `skill xp gets the affinity boost when the work matches`() {
        val foraging = base.copy(activeActivity = ActivityId.FORAGE)
        assertEquals(1.5, YieldCalculator.skillXpPerDay(foraging, SkillId.HUSBANDRY, false, cfg), 1e-12)
        assertEquals(1.5, YieldCalculator.skillXpPerDay(foraging, SkillId.GRIT, false, cfg), 1e-12, "outdoor work teaches grit")
        assertEquals(1.0, YieldCalculator.skillXpPerDay(foraging, SkillId.THRIFT, false, cfg), 1e-12)
    }

    @Test
    fun `bible and schoolhouse boost xp and the second slot halves it`() {
        val s = base.copy(
            activeActivity = ActivityId.HAUL_CARGO,
            heirlooms = setOf(HeirloomId.FAMILY_BIBLE),
            ventures = mapOf(VentureId.SCHOOLHOUSE_FUND to 2),
        )
        val expected = 1.0 * 1.15 * (1.0 + 0.25 * 2)
        assertEquals(expected, YieldCalculator.skillXpPerDay(s, SkillId.COMMUNITY, false, cfg), 1e-12)
        assertEquals(expected * 0.5, YieldCalculator.skillXpPerDay(s, SkillId.COMMUNITY, true, cfg), 1e-12)
    }
}
