package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import io.rebble.libpebblecommon.database.entity.LAUNCHER_FOLDER_ID_RANGE
import io.rebble.libpebblecommon.database.entity.LAUNCHER_FOLDER_MAX_COUNT
import io.rebble.libpebblecommon.database.entity.LauncherFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LauncherFolderDao {
    @Query("SELECT * FROM LauncherFolderEntity ORDER BY id ASC")
    fun getAllFlow(): Flow<List<LauncherFolderEntity>>

    @Query("SELECT * FROM LauncherFolderEntity ORDER BY id ASC")
    suspend fun getAll(): List<LauncherFolderEntity>

    @Query("SELECT id FROM LauncherFolderEntity ORDER BY id ASC")
    suspend fun getAllIds(): List<Int>

    @Insert
    suspend fun insert(folder: LauncherFolderEntity)

    @Query("UPDATE LauncherFolderEntity SET name = :name WHERE id = :id")
    suspend fun rename(id: Int, name: String)

    @Query("DELETE FROM LauncherFolderEntity WHERE id = :id")
    suspend fun deleteById(id: Int)

    /**
     * Ids are reused so that a user who repeatedly creates and deletes folders does not run out
     * of the single byte the watch stores them in.
     *
     * @return the new folder's id, or null if the watch cannot hold another folder.
     */
    @Transaction
    suspend fun create(name: String): Int? {
        val used = getAllIds().toSet()
        if (used.size >= LAUNCHER_FOLDER_MAX_COUNT) {
            return null
        }
        val id = LAUNCHER_FOLDER_ID_RANGE.firstOrNull { it !in used } ?: return null
        insert(LauncherFolderEntity(id = id, name = name))
        return id
    }
}
