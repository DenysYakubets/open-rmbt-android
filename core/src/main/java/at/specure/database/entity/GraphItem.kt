package at.specure.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import at.specure.database.Columns.TEST_UUID_PARENT_COLUMN
import at.specure.database.Tables

@Entity(tableName = Tables.TEST_GRAPH_ITEM)
data class GraphItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ForeignKey(entity = Test::class, parentColumns = [TEST_UUID_PARENT_COLUMN], childColumns = ["testUUID"], onDelete = ForeignKey.CASCADE)
    val testUUID: String,
    val progress: Int,
    val value: Long,
    val type: Int
) {
    companion object {
        const val GRAPH_ITEM_TYPE_DOWNLOAD = 1
        const val GRAPH_ITEM_TYPE_UPLOAD = 2
    }
}
