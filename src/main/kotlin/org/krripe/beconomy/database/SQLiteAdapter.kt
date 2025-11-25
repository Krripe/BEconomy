package org.krripe.beconomy.database

import org.krripe.beconomy.config.ConfigManager
import java.io.File
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*

class SQLiteAdapter(private val config: ConfigManager.DatabaseConfig) : DatabaseAdapter {
    private var connection: Connection? = null

    override fun connect(): Boolean {
        return try {
            val folder = File("config/beconomy")
            if (!folder.exists()) {
                folder.mkdirs()
            }

            val url = "jdbc:sqlite:config/beconomy/${config.databaseName}"
            connection = DriverManager.getConnection(url)

            connection?.createStatement()?.execute(
                """
                CREATE TABLE IF NOT EXISTS balances (
                    playerId TEXT NOT NULL,
                    currencyType TEXT NOT NULL,
                    balance REAL NOT NULL,
                    PRIMARY KEY (playerId, currencyType)
                )
                """
            )
            println("Connected to SQLite database at $url")
            true
        } catch (e: SQLException) {
            println("Failed to connect to SQLite: ${e.message}")
            false
        }
    }

    override fun disconnect(): Boolean {
        return try {
            connection?.close()
            println("SQLite connection closed.")
            true
        } catch (e: SQLException) {
            println("Failed to close SQLite connection: ${e.message}")
            false
        }
    }

    override fun getBalance(playerId: UUID, currencyType: String): BigDecimal {
        val query = "SELECT balance FROM balances WHERE playerId = ? AND currencyType = ?"
        connection?.prepareStatement(query).use { statement ->
            statement?.setString(1, playerId.toString())
            statement?.setString(2, currencyType)
            val resultSet = statement?.executeQuery()
            if (resultSet != null && resultSet.next()) {
                return resultSet.getBigDecimal("balance")
            }
        }
        return BigDecimal.ZERO
    }

    override fun setBalance(playerId: UUID, balance: BigDecimal, currencyType: String) {
        val query = """
            INSERT INTO balances (playerId, currencyType, balance) 
            VALUES (?, ?, ?) 
            ON CONFLICT(playerId, currencyType) DO UPDATE 
            SET balance = ?
        """
        connection?.prepareStatement(query).use { statement ->
            statement?.setString(1, playerId.toString())
            statement?.setString(2, currencyType)
            statement?.setBigDecimal(3, balance)
            statement?.setBigDecimal(4, balance)
            statement?.executeUpdate()
        }
    }

    override fun initializeUserData(playerId: UUID, currencyType: String) {
        val query = """
            INSERT OR IGNORE INTO balances (playerId, currencyType, balance)
            VALUES (?, ?, ?)
        """
        connection?.prepareStatement(query).use { statement ->
            statement?.setString(1, playerId.toString())
            statement?.setString(2, currencyType)
            statement?.setBigDecimal(3, BigDecimal.ZERO)
            statement?.executeUpdate()
        }
    }

    override fun hasUserData(playerId: UUID, currencyType: String): Boolean {
        val query = "SELECT 1 FROM balances WHERE playerId = ? AND currencyType = ?"
        connection?.prepareStatement(query).use { statement ->
            statement?.setString(1, playerId.toString())
            statement?.setString(2, currencyType)
            val resultSet = statement?.executeQuery()
            return resultSet?.next() == true
        }
    }

    override fun getAllBalances(currencyType: String): Map<UUID, BigDecimal> {
        val query = "SELECT playerId, balance FROM balances WHERE currencyType = ?"
        val result = mutableMapOf<UUID, BigDecimal>()

        connection?.prepareStatement(query).use { statement ->
            statement?.setString(1, currencyType)
            val resultSet = statement?.executeQuery()
            while (resultSet != null && resultSet.next()) {
                val playerId = UUID.fromString(resultSet.getString("playerId"))
                val balance = resultSet.getBigDecimal("balance")
                result[playerId] = balance
            }
        }

        return result
    }
}