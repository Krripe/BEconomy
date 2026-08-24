package org.krripe.beconomy

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import org.krripe.beconomy.api.BEconomy
import org.krripe.beconomy.config.BarConfigManager
import org.krripe.beconomy.config.ConfigManager
import org.krripe.beconomy.config.DepositConfigManager
import org.krripe.beconomy.config.MessageManager
import org.krripe.beconomy.database.DatabaseManager
import org.krripe.beconomy.integration.BEconomyGTSBridge
import org.krripe.beconomy.manager.BarManager
import org.krripe.beconomy.manager.CmdManager
import org.krripe.beconomy.manager.DepositBlockManager
import org.krripe.beconomy.manager.EcoManager
import org.krripe.beconomy.utils.PlaceholderUtil
import org.pokesplash.gts.api.economy.GtsEconomyProvider
import org.pokesplash.gts.enumeration.Priority
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.HitResult
import org.krripe.beconomy.manager.DepositManager

class Beconomy : ModInitializer {

    override fun onInitialize() {
        println("Initializing BEconomy...")

        if (FabricLoader.getInstance().isModLoaded("gts")) {
            GtsEconomyProvider.putEconomy(
                Priority.HIGH,
                BEconomyGTSBridge()
            )

            println("[BEconomy] Successfully integrated with GTS!")
        }

        val config = ConfigManager.loadConfig()
        MessageManager.load()
        BarConfigManager.load()
        DepositConfigManager.loadAll(
            config.economy
        )

        if (config.databaseSave) {
            println("[BEconomy] Attempting to initialize database...")

            val databaseInitialized =
                DatabaseManager.initialize(
                    config.database
                )

            if (databaseInitialized) {
                println(
                    "[BEconomy] Database connection established (${config.database.type})."
                )
            } else {
                println(
                    "[BEconomy] Database connection failed. Falling back to localSave."
                )
            }
        }

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            BEconomy.initialize(server)
            PlaceholderUtil.registerPlaceholders()
            DepositBlockManager.load()
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            BarManager.tick(server)
        }

        UseBlockCallback.EVENT.register(
            UseBlockCallback {
                    player,
                    world,
                    hand,
                    hit ->

                if (world.isClient) {
                    return@UseBlockCallback ActionResult.PASS
                }

                if (
                    hand !=
                    Hand.MAIN_HAND
                ) {
                    return@UseBlockCallback ActionResult.PASS
                }

                if (
                    hit.type !=
                    HitResult.Type.BLOCK
                ) {
                    return@UseBlockCallback ActionResult.PASS
                }

                val serverPlayer =
                    player as?
                            ServerPlayerEntity
                        ?: return@UseBlockCallback ActionResult.PASS

                val currencyType =
                    DepositBlockManager.get(
                        world,
                        hit.blockPos
                    ) ?: return@UseBlockCallback ActionResult.PASS

                val opened =
                    DepositManager.openDepositMenu(
                        serverPlayer,
                        currencyType
                    )

                if (opened) {
                    ActionResult.SUCCESS
                } else {
                    ActionResult.FAIL
                }
            }
        )

        AttackBlockCallback.EVENT.register(
            AttackBlockCallback {
                    _,
                    world,
                    _,
                    pos,
                    _ ->

                if (world.isClient) {
                    return@AttackBlockCallback ActionResult.PASS
                }

                if (
                    !DepositBlockManager.isDepositBlock(
                        world,
                        pos
                    )
                ) {
                    return@AttackBlockCallback ActionResult.PASS
                }

                ActionResult.SUCCESS
            }
        )

        CommandRegistrationCallback.EVENT.register {
                dispatcher,
                _,
                _ ->
            CmdManager.registerCommands(
                dispatcher
            )
        }

        ServerPlayConnectionEvents.JOIN.register {
                handler,
                _,
                _ ->
            val player = handler.player

            ConfigManager.initializeUserData(
                player.uuid
            )

            EcoManager.initializePlayerData(
                player
            )

            BarManager.updatePlayer(
                player
            )
        }

        ServerLifecycleEvents.SERVER_STOPPED.register {
            DepositBlockManager.save()
            DatabaseManager.shutdown()

            println(
                "[BEconomy] Database connection closed."
            )
        }

        println(
            "BEconomy successfully initialized!"
        )
    }
}