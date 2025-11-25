package org.krripe.beconomy.database

import org.krripe.beconomy.config.ConfigManager
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID

class MySQLAdapter(private val config: ConfigManager.DatabaseConfig) : DatabaseAdapter {
    private var connection: Connection? = null

    override fun connect(): Boolean {
        return try {
            val url = "jdbc:mysql://${config.host}:${config.port}/${config.databaseName}" +
                    "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true" +
                    "&autoReconnect=true&testOnBorrow=true"

            println("Attempting to connect to MySQL database with URL: $url")
            connection = DriverManager.getConnection(url, config.username, config.password)
            initializeTables()

            println("Successfully connected to MySQL database at ${config.host}:${config.port}.")
            true
        } catch (e: SQLException) {
            println("Failed to connect to MySQL database: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    override fun disconnect(): Boolean {
        return try {
            connection?.close()
            println("Disconnected from MySQL database successfully.")
            true
        } catch (e: SQLException) {
            println("Failed to disconnect from MySQL database: ${e.message}")
            false
        }
    }

    override fun getBalance(playerId: UUID, currencyType: String): BigDecimal {
        val query = "SELECT balance FROM balances WHERE playerId = ? AND currencyType = ?"
        return executeQuery(query, playerId, currencyType) { resultSet ->
            if (resultSet.next()) resultSet.getBigDecimal("balance") else BigDecimal.ZERO
        } ?: BigDecimal.ZERO
    }

    override fun setBalance(playerId: UUID, balance: BigDecimal, currencyType: String) {
        val query = """
            INSERT INTO balances (playerId, currencyType, balance)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE balance = VALUES(balance)
        """
        executeUpdate(query, playerId, currencyType, balance)
    }

    override fun initializeUserData(playerId: UUID, currencyType: String) {
        val query = "INSERT IGNORE INTO balances (playerId, currencyType, balance) VALUES (?, ?, ?)"
        executeUpdate(query, playerId, currencyType, BigDecimal.ZERO)
    }

    override fun hasUserData(playerId: UUID, currencyType: String): Boolean {
        val query = "SELECT 1 FROM balances WHERE playerId = ? AND currencyType = ?"
        return executeQuery(query, playerId, currencyType) { resultSet -> resultSet.next() } ?: false
    }

    override fun getAllBalances(currencyType: String): Map<UUID, BigDecimal> {
        val query = "SELECT playerId, balance FROM balances WHERE currencyType = ?"
        val balances = mutableMapOf<UUID, BigDecimal>()
        executeQueryForAll(query, currencyType) { resultSet ->
            while (resultSet.next()) {
                val playerId = UUID.fromString(resultSet.getString("playerId"))
                val balance = resultSet.getBigDecimal("balance")
                balances[playerId] = balance
            }
        }
        return balances
    }

    private fun initializeTables() {
        val createTableQuery = """
            CREATE TABLE IF NOT EXISTS balances (
                id INT AUTO_INCREMENT PRIMARY KEY,
                playerId VARCHAR(36) NOT NULL,
                currencyType VARCHAR(255) NOT NULL,
                balance DECIMAL(20, 2) NOT NULL DEFAULT 0.00,
                UNIQUE KEY unique_player_currency (playerId, currencyType)
            );
        """
        executeUpdate(createTableQuery)
    }

    private fun ensureConnectionValid() {
        try {
            if (connection == null || connection!!.isClosed || !connection!!.isValid(2)) {
                println("Connection to MySQL is closed or invalid, reconnecting...")
                connect()
            }
        } catch (e: Exception) {
            println("Error while checking MySQL connection: ${e.message}")
            connect()
        }
    }

    private fun <T> executeQuery(
        query: String,
        playerId: UUID,
        currencyType: String,
        block: (ResultSet) -> T
    ): T? {
        return try {
            ensureConnectionValid()
            connection?.prepareStatement(query).use { statement ->
                statement?.setString(1, playerId.toString())
                statement?.setString(2, currencyType)
                statement?.executeQuery()?.use(block)
            }
        } catch (e: SQLException) {
            println("Query execution failed: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun executeQueryForAll(query: String, currencyType: String, block: (ResultSet) -> Unit) {
        try {
            ensureConnectionValid()
            connection?.prepareStatement(query).use { statement ->
                statement?.setString(1, currencyType)
                statement?.executeQuery()?.use(block)
            }
        } catch (e: SQLException) {
            println("Query execution failed: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun executeUpdate(query: String, playerId: UUID? = null, currencyType: String? = null, balance: BigDecimal? = null) {
        try {
            ensureConnectionValid()
            connection?.prepareStatement(query).use { statement ->
                var paramIndex = 1
                if (playerId != null) {
                    statement?.setString(paramIndex++, playerId.toString())
                }
                if (currencyType != null) {
                    statement?.setString(paramIndex++, currencyType)
                }
                if (balance != null) {
                    statement?.setBigDecimal(paramIndex++, balance)
                }
                statement?.executeUpdate()
            }
        } catch (e: SQLException) {
            println("Update execution failed: ${e.message}")
            e.printStackTrace()
        }
    }
}