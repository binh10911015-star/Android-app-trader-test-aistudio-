package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.TradePosition
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(position: TradePosition): Long

    @Update
    suspend fun update(position: TradePosition)

    @Query("SELECT * FROM trade_positions WHERE status = 'OPEN' ORDER BY entryTime DESC")
    fun getOpenPositions(): Flow<List<TradePosition>>

    @Query("SELECT * FROM trade_positions WHERE status = 'OPEN'")
    suspend fun getOpenPositionsList(): List<TradePosition>

    @Query("SELECT * FROM trade_positions WHERE status = 'CLOSED' ORDER BY exitTime DESC")
    fun getClosedPositions(): Flow<List<TradePosition>>

    @Query("SELECT * FROM trade_positions ORDER BY entryTime DESC")
    fun getAllPositions(): Flow<List<TradePosition>>

    @Query("SELECT COUNT(*) FROM trade_positions WHERE symbol = :symbol AND status = 'OPEN'")
    suspend fun countOpenForSymbol(symbol: String): Int

    @Query("SELECT * FROM trade_positions WHERE id = :id LIMIT 1")
    suspend fun getPositionById(id: Long): TradePosition?

    @Delete
    suspend fun delete(position: TradePosition)

    @Query("DELETE FROM trade_positions")
    suspend fun clearAll()
}
