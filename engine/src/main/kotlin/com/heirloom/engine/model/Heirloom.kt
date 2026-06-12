package com.heirloom.engine.model

import kotlinx.serialization.Serializable

/**
 * One-time permanent unlocks. Earned by single-life feats, stack multiplicatively,
 * and survive both generation resets and Monument resets.
 * Effect magnitudes live in BalanceConfig; conditions in HeirloomChecker.
 */
@Serializable
enum class HeirloomId(val displayName: String, val flavor: String) {
    FATHERS_TOOLS("Father's Tools", "Worn smooth by two pairs of hands."),
    FAMILY_BIBLE("Family Bible", "Births and deaths in the margins."),
    MOTHERS_RECIPES("Mother's Recipes", "How to make a winter smaller."),
    PROVEN_DEED("Proven Deed", "The land is ours, on paper and in fact."),
    IRON_STOVE("Iron Stove", "The warm heart of the house."),
    RAILROAD_SHARES("Railroad Shares", "A piece of the thing that ate the distance."),
    HUNTING_RIFLE("Hunting Rifle", "Sighted true by a patient eye."),
    OXEN_YOKE("Oxen Yoke", "Pull steady and the miles pass."),
    QUILT_OF_MANY_HANDS("Quilt of Many Hands", "Every square a neighbor."),
    LEDGER_AND_QUILL("Ledger & Quill", "Waste nothing, owe no one."),
    PIONEERS_JOURNAL("Pioneer's Journal", "A long life, written small."),
    FOUNDERS_GAVEL("Founder's Gavel", "Order, order — this town will come to order."),
}
