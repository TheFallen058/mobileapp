package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.database.entity.LAUNCHER_FOLDER_NAME_MAX_LENGTH
import io.rebble.libpebblecommon.database.entity.toLauncherFolderName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

private const val PACKET_HEADER_SIZE = 4
private const val COMMAND_SIZE = 1
private const val CONFIG_HEADER_SIZE = 4
private const val NAME_BUFFER_SIZE = 25
private const val UUID_SIZE = 16

internal class LauncherFoldersTest {
    private val games = Uuid.parse("6fd11a17-4e3b-4b0b-9b52-4f7a7b4ff4a1")
    private val puzzle = Uuid.parse("d2a0f9a1-1b38-4d38-8f97-9c39b1f3a2b4")

    /** The bytes after the protocol header and the command byte are what the watch stores. */
    private fun payload(packet: LauncherFoldersRequest): UByteArray =
        packet.serialize().drop(PACKET_HEADER_SIZE + COMMAND_SIZE).toUByteArray()

    @Test
    fun emptyConfigurationClearsFoldersOnTheWatch() {
        val bytes = payload(LauncherFoldersRequest(emptyList()))

        assertEquals(CONFIG_HEADER_SIZE, bytes.size)
        assertEquals(LAUNCHER_FOLDER_LAYOUT_VERSION, bytes[0])
        assertEquals(0u, bytes[1])
        assertEquals(0u, bytes[2])
        assertEquals(0u, bytes[3])
    }

    @Test
    fun serializesFolderRecords() {
        val folders = listOf(
            LauncherFolderSync(id = 3, name = "Games", members = listOf(games, puzzle)),
            LauncherFolderSync(id = 7, name = "Navigation", members = emptyList()),
        )
        val bytes = payload(LauncherFoldersRequest(folders))

        val gamesRecordSize = 2 + NAME_BUFFER_SIZE + (2 * UUID_SIZE)
        val navigationRecordSize = 2 + NAME_BUFFER_SIZE
        val dataSize = gamesRecordSize + navigationRecordSize
        assertEquals(CONFIG_HEADER_SIZE + dataSize, bytes.size)

        assertEquals(LAUNCHER_FOLDER_LAYOUT_VERSION, bytes[0])
        assertEquals(2u, bytes[1])
        // data_size is little-endian.
        assertEquals((dataSize and 0xff).toUByte(), bytes[2])
        assertEquals((dataSize shr 8).toUByte(), bytes[3])

        val games = bytes.drop(CONFIG_HEADER_SIZE)
        assertEquals(3u, games[0])
        assertEquals(2u, games[1])
        assertEquals("Games", games.drop(2).take(NAME_BUFFER_SIZE).nameString())

        val navigation = bytes.drop(CONFIG_HEADER_SIZE + gamesRecordSize)
        assertEquals(7u, navigation[0])
        // An empty folder is still sent: the phone keeps it so a temporarily uninstalled app does
        // not lose its grouping, and the watch is what hides it.
        assertEquals(0u, navigation[1])
        assertEquals("Navigation", navigation.drop(2).take(NAME_BUFFER_SIZE).nameString())
    }

    @Test
    fun namesAreNulTerminatedWithinTheirField() {
        val name = "x".repeat(LAUNCHER_FOLDER_NAME_MAX_LENGTH)
        val bytes = payload(
            LauncherFoldersRequest(listOf(LauncherFolderSync(1, name, listOf(games))))
        )

        val nameField = bytes.drop(CONFIG_HEADER_SIZE + 2).take(NAME_BUFFER_SIZE)
        assertEquals(name, nameField.nameString())
        assertTrue(nameField.contains(0u.toUByte()), "name must be NUL-terminated")
    }

    @Test
    fun overlongNamesAreTrimmedToWholeCodePoints() {
        // Four bytes each, so the limit falls inside a code point if bytes were cut blindly.
        val name = "\uD83C\uDFAE".repeat(10).toLauncherFolderName()

        assertTrue(name.encodeToByteArray().size <= LAUNCHER_FOLDER_NAME_MAX_LENGTH)
        assertEquals("\uD83C\uDFAE".repeat(6), name)
    }

    @Test
    fun namesWithinTheLimitAreUntouched() {
        assertEquals("Games", "  Games  ".toLauncherFolderName())
        assertEquals("", "   ".toLauncherFolderName())
    }

    private fun List<UByte>.nameString(): String =
        takeWhile { it != 0u.toUByte() }.toUByteArray().toByteArray().decodeToString()
}
