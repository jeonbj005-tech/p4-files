package com.polestar4.drivefilemanager

const val WAKE_STATUS_FILE_NAME = "P4FilesWakeStatus.json"

internal fun latestWakeStatusFile(files: List<DriveApi.DriveFile>): DriveApi.DriveFile? =
    files.asSequence().filter { it.name == WAKE_STATUS_FILE_NAME }.maxByOrNull { it.modified }
