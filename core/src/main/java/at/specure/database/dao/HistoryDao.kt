package at.specure.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import at.specure.database.Tables
import at.specure.database.entity.History

@Dao
interface HistoryDao {

    @Query("SELECT * from ${Tables.HISTORY} ORDER BY timeMillis DESC")
    fun getHistoryItems(): List<History>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(history: History)

    @Query("DELETE FROM ${Tables.HISTORY}")
    fun deleteAll(): Int
}