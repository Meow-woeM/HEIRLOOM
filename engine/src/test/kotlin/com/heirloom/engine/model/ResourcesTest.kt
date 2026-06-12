package com.heirloom.engine.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourcesTest {
    private val a = Resources(food = 10.0, materials = 20.0, money = 30.0, standing = 40.0)
    private val b = Resources(food = 1.0, materials = 2.0, money = 3.0, standing = 4.0)

    @Test
    fun `arithmetic operators work per component`() {
        assertEquals(Resources(11.0, 22.0, 33.0, 44.0), a + b)
        assertEquals(Resources(9.0, 18.0, 27.0, 36.0), a - b)
        assertEquals(Resources(20.0, 40.0, 60.0, 80.0), a * 2.0)
    }

    @Test
    fun `get and add address single resources`() {
        assertEquals(10.0, a[ResourceType.FOOD])
        assertEquals(40.0, a[ResourceType.STANDING])
        assertEquals(25.0, a.add(ResourceType.FOOD, 15.0).food)
        assertEquals(20.0, a.add(ResourceType.FOOD, 15.0).materials, "other components untouched")
    }

    @Test
    fun `covers requires every component`() {
        assertTrue(a.covers(b))
        assertTrue(a.covers(a))
        assertFalse(b.covers(a))
        assertFalse(a.covers(a.copy(standing = 40.1)))
    }

    @Test
    fun `coerceAtLeastZero clamps negatives only`() {
        val mixed = Resources(food = -5.0, materials = 3.0, money = -0.1, standing = 0.0)
        assertEquals(Resources(0.0, 3.0, 0.0, 0.0), mixed.coerceAtLeastZero())
    }
}
