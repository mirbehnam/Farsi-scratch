package ir.behnamapps.fascratch.inappbilling.domain

data class TrainingVersionRule(val store: String, val min: Int, val max: Int, val enabled: Boolean, val message: String)

object ClientCommandPolicy {
    fun blockedMessage(maintenance: Boolean, maintenanceMessage: String, rules: List<TrainingVersionRule>, flavor: String, version: Int): String? {
        require(rules.size <= 30)
        rules.forEach { require(it.min > 0 && it.max >= it.min && it.store in listOf("all", "bazaar", "myket", "website") && it.message.isNotBlank()) }
        if (maintenance) return maintenanceMessage.take(200).also { require(it.isNotBlank()) }
        return rules.firstOrNull { it.enabled && it.store in listOf("all", flavor) && version in it.min..it.max }?.message?.take(200)
    }

    fun needsCacheClear(received: Long, applied: Long): Boolean = received > applied && received >= 0
}
