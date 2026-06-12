package com.heirloom.engine.model

import kotlinx.serialization.Serializable

@Serializable
enum class ResourceType(val displayName: String) {
    FOOD("Food"),
    MATERIALS("Materials"),
    MONEY("Money"),
    STANDING("Standing"),
}

/** Resource wallet. Amounts grow exponentially; display formatting lives in the UI layer. */
@Serializable
data class Resources(
    val food: Double = 0.0,
    val materials: Double = 0.0,
    val money: Double = 0.0,
    val standing: Double = 0.0,
) {
    operator fun get(type: ResourceType): Double = when (type) {
        ResourceType.FOOD -> food
        ResourceType.MATERIALS -> materials
        ResourceType.MONEY -> money
        ResourceType.STANDING -> standing
    }

    fun with(type: ResourceType, amount: Double): Resources = when (type) {
        ResourceType.FOOD -> copy(food = amount)
        ResourceType.MATERIALS -> copy(materials = amount)
        ResourceType.MONEY -> copy(money = amount)
        ResourceType.STANDING -> copy(standing = amount)
    }

    fun add(type: ResourceType, amount: Double): Resources = with(type, this[type] + amount)

    operator fun plus(other: Resources): Resources = Resources(
        food + other.food, materials + other.materials, money + other.money, standing + other.standing,
    )

    operator fun minus(other: Resources): Resources = Resources(
        food - other.food, materials - other.materials, money - other.money, standing - other.standing,
    )

    operator fun times(factor: Double): Resources = Resources(
        food * factor, materials * factor, money * factor, standing * factor,
    )

    fun covers(cost: Resources): Boolean =
        food >= cost.food && materials >= cost.materials && money >= cost.money && standing >= cost.standing

    fun coerceAtLeastZero(): Resources = Resources(
        food.coerceAtLeast(0.0), materials.coerceAtLeast(0.0),
        money.coerceAtLeast(0.0), standing.coerceAtLeast(0.0),
    )

    val isZero: Boolean get() = food == 0.0 && materials == 0.0 && money == 0.0 && standing == 0.0

    companion object {
        val ZERO = Resources()
    }
}
