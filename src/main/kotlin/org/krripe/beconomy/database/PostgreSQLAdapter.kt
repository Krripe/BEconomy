package org.krripe.beconomy.database

import org.krripe.beconomy.config.ConfigManager
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*

class PostgreSQLAdapter(private val config: ConfigManager.DatabaseConfig) : DatabaseAdapter {
    private var connection: Connection? = null

    override fun connect(): Boolean {
        return try {
            val url = "jdbc:postgresql://${config.host}:${config.port}/${config.databaseName}?prepareThreshold=0"
            connection = DriverManager.getConnection(url, config.username, config.password)
            initializeTables()
            true
        } catch (e: SQLException) {
            println("Failed to connect to PostgreSQL: ${e.message}")
            false
        }
    }

    override fun disconnect(): Boolean {
        return try {
            connection?.close()
            true
        } catch (_: SQLException) {
            false
        }
    }

    private fun ensureConnectionValid() {
        try {
            if (connection == null || connection!!.isClosed || !connection!!.isValid(2)) {
                println("PostgreSQL connection lost or invalid. Reconnecting...")
                connect()
            }
        } catch (e: SQLException) {
            println("Error checking PostgreSQL connection: ${e.message}")
            connect()
        }
    }

    override fun getBalance(playerId: UUID, currencyType: String): BigDecimal {
        ensureConnectionValid()
        val query = """
            SELECT balance FROM balances WHERE playerId = ? AND currencyType = ?
        """
        connection?.prepareStatement(query).use { statement ->
            var resultSet: ResultSet? = null
            try {
                statement?.setObject(1, playerId)
                statement?.setString(2, currencyType)
                resultSet = statement?.executeQuery()
                if (resultSet != null && resultSet.next()) {
                    return resultSet.getBigDecimal("balance")
                }
            } finally {
                resultSet?.close()
            }
        }
        return BigDecimal.ZERO
    }

    override fun setBalance(playerId: UUID, balance: BigDecimal, currencyType: String) {
        ensureConnectionValid()
        val query = """
            INSERT INTO balances (playerId, currencyType, balance) 
            VALUES (?, ?, ?) 
            ON CONFLICT (playerId, currencyType) DO UPDATE 
            SET balance = EXCLUDED.balance
        """
        connection?.prepareStatement(query).use { statement ->
            statement?.setObject(1, playerId)
            statement?.setString(2, currencyType)
            statement?.setBigDecimal(3, balance)
            statement?.executeUpdate()
        }
    }

    override fun initializeUserData(playerId: UUID, currencyType: String) {
        ensureConnectionValid()
        val query = """
            INSERT INTO balances (playerId, currencyType, balance)
            VALUES (?, ?, ?)
            ON CONFLICT DO NOTHING
        """
        connection?.prepareStatement(query).use { statement ->
            statement?.setObject(1, playerId)
            statement?.setString(2, currencyType)
            statement?.setBigDecimal(3, BigDecimal.ZERO)
            statement?.executeUpdate()
        }
    }

    override fun hasUserData(playerId: UUID, currencyType: String): Boolean {
        ensureConnectionValid()
        val query = """
            SELECT 1 FROM balances WHERE playerId = ? AND currencyType = ?
        """
        connection?.prepareStatement(query).use { statement ->
            var resultSet: ResultSet? = null
            try {
                statement?.setObject(1, playerId)
                statement?.setString(2, currencyType)
                resultSet = statement?.executeQuery()
                return resultSet?.next() == true
            } finally {
                resultSet?.close()
            }
        }
    }

    override fun getAllBalances(currencyType: String): Map<UUID, BigDecimal> {
        ensureConnectionValid()
        val query = "SELECT playerId, balance FROM balances WHERE currencyType = ?"
        val result = mutableMapOf<UUID, BigDecimal>()

        connection?.prepareStatement(query).use { statement ->
            var resultSet: ResultSet? = null
            try {
                statement?.setString(1, currencyType)
                resultSet = statement?.executeQuery()
                while (resultSet != null && resultSet.next()) {
                    val playerId = UUID.fromString(resultSet.getString("playerId"))
                    val balance = resultSet.getBigDecimal("balance")
                    result[playerId] = balance
                }
            } finally {
                resultSet?.close()
            }
        }

        return result
    }

    private fun initializeTables() {
        ensureConnectionValid()
        val createTableQuery = """
            CREATE TABLE IF NOT EXISTS balances (
                id SERIAL PRIMARY KEY,
                playerId UUID NOT NULL,
                currencyType VARCHAR(255) NOT NULL,
                balance NUMERIC(20, 2) NOT NULL DEFAULT 0.00,
                UNIQUE (playerId, currencyType)
            );
        """
        connection?.prepareStatement(createTableQuery).use { statement ->
            statement?.executeUpdate()
        }
    }
}