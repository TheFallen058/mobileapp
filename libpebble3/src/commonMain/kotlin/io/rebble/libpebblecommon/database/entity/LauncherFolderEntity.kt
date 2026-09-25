package io.rebble.libpebblecommon.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A launcher folder.
 *
 * Folders are launcher presentation state, not apps: they are never installed on a watch, have no
 * UUID and carry no app metadata, so they live in their own table rather than in the locker.
 *
 * The id is what the watch stores next to each member's UUID, so renaming a folder never disturbs
 * its membership. Ids are allocated in [LAUNCHER_FOLDER_ID_RANGE] because the watch carries them
 * in a single byte with 0 reserved for "no folder".
 *
 * Folders with no members are kept here on purpose: a user who temporarily uninstalls every app in
 * a folder should not lose the folder. The watch is what hides them.
 */
@Entity
data class LauncherFolderEntity(
    @PrimaryKey val id: Int,
    val name: String,
)

val LAUNCHER_FOLDER_ID_RANGE = 1..255

/** Matches LAUNCHER_FOLDER_NAME_MAX_LENGTH in the firmware. */
const val LAUNCHER_FOLDER_NAME_MAX_LENGTH = 24

/** Matches LAUNCHER_FOLDER_MAX_COUNT in the firmware. */
const val LAUNCHER_FOLDER_MAX_COUNT = 16

/** Matches LAUNCHER_FOLDER_MAX_MEMBERS_TOTAL in the firmware. */
const val LAUNCHER_FOLDER_MAX_MEMBERS_TOTAL = 64

/**
 * Trims a user-entered folder name to what the watch can store. The limit is in bytes, and the
 * name has to stay valid UTF-8, so whole code points are dropped rather than bytes.
 */
fun String.toLauncherFolderName(): String {
    val trimmed = trim()
    if (trimmed.encodeToByteArray().size <= LAUNCHER_FOLDER_NAME_MAX_LENGTH) {
        return trimmed
    }
    var result = trimmed
    while (result.isNotEmpty() &&
        result.encodeToByteArray().size > LAUNCHER_FOLDER_NAME_MAX_LENGTH
    ) {
        result = result.dropLast(1)
    }
    return result
}
