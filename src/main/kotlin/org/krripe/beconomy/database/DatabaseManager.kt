package org.krripe.beconomy.database

import org.krripe.beconomy.config.ConfigManager

object DatabaseManager {
    private var adapter: DatabaseAdapter? = null

    fun initialize(config: ConfigManager.DatabaseConfig?): Boolean {
        if (config == null) {
            println("[BEconomy] Database config is null!")
            return false
        }
        println("Initializing database of type: ${config.type}")
        adapter = when (config.type.lowercase()) {
            "mysql" -> MySQLAdapter(config)
            "mongodb" -> MongoDBAdapter(config)
            "postgresql" -> PostgreSQLAdapter(config)
            "sqlite" -> SQLiteAdapter(config)
            else -> null
        }
        val connected = adapter?.connect() ?: false
        if (!connected) {
            println("[BEconomy] Failed to connect to database: ${config.type}")
        }
        return connected
    }

    fun getAdapter(): DatabaseAdapter? = adapter

    fun shutdown() {
        adapter?.disconnect()
    }
}
