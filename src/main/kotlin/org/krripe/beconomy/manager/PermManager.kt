package org.krripe.beconomy.manager

import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.server.network.ServerPlayerEntity

object PermManager {

    internal fun hasPermission(
        player: ServerPlayerEntity,
        permission: String,
        level: Int
    ): Boolean {
        return if (hasLuckPermsPermission(player, permission, level)) {
            true
        } else {
            player.hasPermissionLevel(level)
        }
    }

    private fun hasLuckPermsPermission(
        player: ServerPlayerEntity,
        permission: String,
        level: Int
    ): Boolean {
        return try {
            Permissions.check(player, permission, level)
        } catch (_: NoClassDefFoundError) {
            false
        }
    }
}