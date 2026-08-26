package com.ad_glasses.ai.grounding

/** Tavily-native categories selected by GroundingIntentRouter. */
enum class TavilySearchTopic(val wire: String) {
    GENERAL("general"),
    NEWS("news"),
    FINANCE("finance"),
}

/** Tavily-native publication/update freshness windows selected by GroundingIntentRouter. */
enum class TavilyTimeRange(val wire: String) {
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year"),
}
