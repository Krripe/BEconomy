package org.krripe.beconomy.database

import java.math.BigDecimal
import java.util.UUID

interface DatabaseAdapter {
    fun connect(): Boolean
    fun disconnect(): Boolean
    fun getBalance(playerId: UUID, currencyType: String): BigDecimal
    fun setBalance(playerId: UUID, balance: BigDecimal, currencyType: String)
    fun initializeUserData(playerId: UUID, currencyType: String)
    fun hasUserData(playerId: UUID, currencyType: String): Boolean
    fun getAllBalances(currencyType: String): Map<UUID, BigDecimal>
}