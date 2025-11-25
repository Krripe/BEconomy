package org.krripe.beconomy.database

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import org.bson.Document
import org.krripe.beconomy.config.ConfigManager
import java.math.BigDecimal
import java.util.UUID

class MongoDBAdapter(private val config: ConfigManager.DatabaseConfig) : DatabaseAdapter {
    private lateinit var client: MongoClient
    private lateinit var database: MongoDatabase

    override fun connect(): Boolean {
        return try {
            val connectionString = if (config.username != null && config.password != null) {
                "mongodb://${config.username}:${config.password}@${config.host}:${config.port}/${config.databaseName}?socketKeepAlive=true"
            } else {
                "mongodb://${config.host}:${config.port}/${config.databaseName}?socketKeepAlive=true"
            }
            client = MongoClients.create(connectionString)
            database = client.getDatabase(config.databaseName)
            initializeCollections()
            true
        } catch (e: Exception) {
            println("Failed to connect to MongoDB: ${e.message}")
            false
        }
    }

    override fun disconnect(): Boolean {
        return try {
            client.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureMongoConnectionValid() {
        try {
            database.runCommand(Document("ping", 1))
        } catch (e: Exception) {
            println("MongoDB connection lost, reconnecting: ${e.message}")
            connect()
        }
    }

    override fun getBalance(playerId: UUID, currencyType: String): BigDecimal {
        ensureMongoConnectionValid()
        val collection = database.getCollection("balances")
        val document = collection.find(Document("playerId", playerId.toString()).append("currencyType", currencyType)).first()
        return document?.getDouble("balance")?.toBigDecimal() ?: BigDecimal.ZERO
    }

    override fun setBalance(playerId: UUID, balance: BigDecimal, currencyType: String) {
        ensureMongoConnectionValid()
        val collection = database.getCollection("balances")
        val update = Document($$"$set", Document("balance", balance.toDouble()))
        collection.updateOne(Document("playerId", playerId.toString()).append("currencyType", currencyType), update)
    }

    override fun initializeUserData(playerId: UUID, currencyType: String) {
        ensureMongoConnectionValid()
        val collection = database.getCollection("balances")
        val existing = collection.find(Document("playerId", playerId.toString()).append("currencyType", currencyType)).first()
        if (existing == null) {
            collection.insertOne(
                Document("playerId", playerId.toString())
                    .append("currencyType", currencyType)
                    .append("balance", BigDecimal.ZERO.toDouble())
            )
        }
    }

    override fun hasUserData(playerId: UUID, currencyType: String): Boolean {
        ensureMongoConnectionValid()
        val collection = database.getCollection("balances")
        return collection.find(Document("playerId", playerId.toString()).append("currencyType", currencyType)).first() != null
    }

    override fun getAllBalances(currencyType: String): Map<UUID, BigDecimal> {
        ensureMongoConnectionValid()
        val result = mutableMapOf<UUID, BigDecimal>()
        val collection = database.getCollection("balances")

        val documents = collection.find(Document("currencyType", currencyType))
        for (doc in documents) {
            val playerId = UUID.fromString(doc.getString("playerId"))
            val balance = doc.getDouble("balance").toBigDecimal()
            result[playerId] = balance
        }

        return result
    }

    private fun initializeCollections() {
        ensureMongoConnectionValid()
        val validator = Document(
            $$"$jsonSchema", Document()
            .append("bsonType", "object")
            .append("required", listOf("playerId", "currencyType", "balance"))
            .append("properties", Document()
                .append("playerId", Document("bsonType", "string"))
                .append("currencyType", Document("bsonType", "string"))
                .append("balance", Document("bsonType", "double"))
            )
        )

        val command = Document("collMod", "balances")
            .append("validator", validator)
            .append("validationLevel", "moderate")

        try {
            database.runCommand(command)
        } catch (e: Exception) {
            println("Failed to set collection validator: ${e.message}")
        }
    }
}
