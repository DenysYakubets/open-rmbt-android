package at.specure.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import at.specure.database.Columns.TEST_UUID_PARENT_COLUMN
import at.specure.database.Tables.CELL_INFO

@Entity(tableName = CELL_INFO)
data class CellInfo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ForeignKey(entity = Test::class, parentColumns = [TEST_UUID_PARENT_COLUMN], childColumns = ["testUUID"], onDelete = ForeignKey.CASCADE)
    val testUUID: String?,
    val active: Boolean?,
    val uuid: String?,
    val channelNumber: Int?,
    val technology: String?,
    val registered: Boolean?,
    // another more for mobile cell
    val areaCode: Long?,
    val locationId: Int?,
    val mcc: Int?,
    val mnc: Int?,
    val primaryScramblingCode: Int?
)