package org.krripe.beconomy.manager

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.nio.file.Files
import java.nio.file.Paths

object DepositBlockManager {

    data class DepositBlock(
        val dimension: String,
        val x: Int,
        val y: Int,
        val z: Int,
        var currencyType: String
    )

    private data class DepositBlockData(
        val blocks: MutableList<DepositBlock> = mutableListOf()
    )

    private val file = Paths.get("config", "beconomy", "data", "deposit_blocks.json").toFile()

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    private val blocks =
        mutableMapOf<Pair<String, BlockPos>, String>()

    fun load() {
        file.parentFile.mkdirs()
        blocks.clear()

        if (!file.exists()) {
            save()
            return
        }

        val data =
            try {
                val json =
                    JsonParser.parseString(
                        Files.readString(
                            file.toPath()
                        )
                    )

                gson.fromJson(
                    json,
                    DepositBlockData::class.java
                )
            } catch (e: Exception) {
                println(
                    "[BEconomy] Failed to load deposit_blocks.json: ${e.message}"
                )

                return
            }

        data.blocks.forEach {
            blocks[
                it.dimension to
                        BlockPos(
                            it.x,
                            it.y,
                            it.z
                        )
            ] = it.currencyType
        }
    }

    fun reload() {
        load()
    }

    fun save() {
        file.parentFile.mkdirs()

        val data =
            DepositBlockData(
                blocks.map {
                        (key, currencyType) ->

                    DepositBlock(
                        dimension = key.first,
                        x = key.second.x,
                        y = key.second.y,
                        z = key.second.z,
                        currencyType = currencyType
                    )
                }.sortedWith(
                    compareBy(
                        { it.dimension },
                        { it.x },
                        { it.y },
                        { it.z }
                    )
                ).toMutableList()
            )

        runCatching {
            file.writeText(
                gson.toJson(
                    data
                )
            )
        }.onFailure {
            println(
                "[BEconomy] Failed to save deposit_blocks.json: ${it.message}"
            )
        }
    }

    fun set(
        world: ServerWorld,
        pos: BlockPos,
        currencyType: String
    ) {
        blocks[
            getKey(
                world,
                pos
            )
        ] = currencyType

        save()
    }

    fun remove(
        world: ServerWorld,
        pos: BlockPos
    ): String? {
        val removed =
            blocks.remove(
                getKey(
                    world,
                    pos
                )
            )

        if (removed != null) {
            save()
        }

        return removed
    }

    fun get(
        world: World,
        pos: BlockPos
    ): String? {
        if (world !is ServerWorld) {
            return null
        }

        return blocks[
            getKey(
                world,
                pos
            )
        ]
    }

    fun isDepositBlock(
        world: World,
        pos: BlockPos
    ): Boolean {
        return get(
            world,
            pos
        ) != null
    }

    fun renameCurrency(
        oldCurrencyType: String,
        newCurrencyType: String
    ) {
        var changed = false

        blocks.entries.forEach {
            if (
                it.value.equals(
                    oldCurrencyType,
                    ignoreCase = true
                )
            ) {
                it.setValue(
                    newCurrencyType
                )

                changed = true
            }
        }

        if (changed) {
            save()
        }
    }

    fun getAll():
            Map<Pair<String, BlockPos>, String> {
        return blocks.toMap()
    }

    private fun getKey(
        world: ServerWorld,
        pos: BlockPos
    ): Pair<String, BlockPos> {
        val dimension =
            world.registryKey.value
                .toString()

        return dimension to
                pos.toImmutable()
    }
}