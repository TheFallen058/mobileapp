package io.rebble.libpebblecommon.packets

import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.*
import io.rebble.libpebblecommon.util.Endian
import kotlin.uuid.Uuid

class AppReorderResult() :
    PebblePacket(ProtocolEndpoint.APP_REORDER) {
    /**
     * Result code. See [AppOrderResultCode].
     */
    val status = SUByte(m)

}

sealed class AppReorderOutgoingPacket(type: AppReorderType) :
    PebblePacket(ProtocolEndpoint.APP_REORDER) {

    /**
     * Packet type. See [AppReorderType].
     */
    val command = SUByte(m, type.value)
}

enum class AppOrderResultCode(val value: UByte) {
    SUCCESS(0x01u),
    FAILED(0x02u),
    INVALID(0x03u),
    RETRY(0x04u);

    companion object {
        fun fromByte(value: UByte): AppOrderResultCode {
            return values().firstOrNull { it.value == value } ?: error("Unknown result: $value")
        }
    }
}


/**
 * Packet sent from the watch when user opens an app that is not in the watch storage.
 */
class AppReorderRequest(
    appList: List<Uuid>
) : AppReorderOutgoingPacket(AppReorderType.REORDER_APPS) {
    val appCount = SByte(m, appList.size.toByte())

    val appList = SFixedList<SUUID>(
        mapper = m,
        count = appList.size,
        default = appList.map { SUUID(StructMapper(), it) },
        itemFactory = {
            SUUID(
                StructMapper()
            )
        })
}

/**
 * Launcher folder configuration.
 *
 * Sent in addition to [AppReorderRequest], never instead of it: folders are launcher metadata, so
 * a watch that does not understand this message still shows every app in the flat order above,
 * just ungrouped. A folder's position in the launcher comes from where its first member sits in
 * that same flat order, which is why nothing about ordering is repeated here.
 */
class LauncherFoldersRequest(
    folders: List<LauncherFolderSync>
) : AppReorderOutgoingPacket(AppReorderType.LAUNCHER_FOLDERS) {
    val version = SUByte(m, LAUNCHER_FOLDER_LAYOUT_VERSION)

    val folderCount = SUByte(m, folders.size.toUByte())

    /** Size of the records that follow; the watch stores this blob verbatim. */
    val dataSize = SUShort(m, folders.sumOf { it.encodedSize }.toUShort(), Endian.Little)

    val folderList = SFixedList(
        mapper = m,
        count = folders.size,
        default = folders.map { LauncherFolderRecord(it) },
        itemFactory = { LauncherFolderRecord() },
    )
}

data class LauncherFolderSync(
    val id: Int,
    val name: String,
    val members: List<Uuid>,
) {
    val encodedSize: Int
        get() = FOLDER_RECORD_HEADER_SIZE + (members.size * UUID_SIZE)
}

class LauncherFolderRecord(folder: LauncherFolderSync? = null) : StructMappable() {
    val folderId = SUByte(m, (folder?.id ?: 0).toUByte())

    val memberCount = SUByte(m, (folder?.members?.size ?: 0).toUByte())

    /** Fixed width and NUL-terminated, so the watch can walk records without a length table. */
    val name = SFixedString(m, LAUNCHER_FOLDER_NAME_BUFFER_SIZE, folder?.name ?: "")

    val members = SFixedList(
        mapper = m,
        count = folder?.members?.size ?: 0,
        default = folder?.members.orEmpty().map { LauncherFolderMember(it) },
        itemFactory = { LauncherFolderMember() },
    )
}

class LauncherFolderMember(uuid: Uuid = Uuid.NIL) : StructMappable() {
    val uuid = SUUID(m, uuid)
}

const val LAUNCHER_FOLDER_LAYOUT_VERSION: UByte = 1u

/** One byte more than the longest name, for the terminator. */
private const val LAUNCHER_FOLDER_NAME_BUFFER_SIZE = 25
private const val UUID_SIZE = 16
private const val FOLDER_RECORD_HEADER_SIZE = 2 + LAUNCHER_FOLDER_NAME_BUFFER_SIZE

enum class AppReorderType(val value: UByte) {
    REORDER_APPS(0x01u),
    LAUNCHER_FOLDERS(0x02u),
}


fun appReorderIncomingRegister() {
    PacketRegistry.register(
        ProtocolEndpoint.APP_REORDER,
    ) { AppReorderResult() }
}