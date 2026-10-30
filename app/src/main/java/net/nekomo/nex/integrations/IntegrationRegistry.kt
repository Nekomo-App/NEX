package net.nekomo.nex.integrations

import net.nekomo.nex.data.AppId

/** One adapter per supported app. Adding an integration = implement [IntegrationAdapter] and register it here. */
object IntegrationRegistry {
    val adapters: List<IntegrationAdapter> = listOf(
        NekomoAdapter(),
        MihonAdapter(),
        AniyomiAdapter(),
        CloudStreamAdapter(),
        ZangetsuAdapter(),
    )

    operator fun get(id: AppId): IntegrationAdapter = adapters.first { it.appId == id }
}
