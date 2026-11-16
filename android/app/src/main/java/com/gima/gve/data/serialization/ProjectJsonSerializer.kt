package com.gima.gve.data.serialization

import com.gima.gve.domain.model.TimelineConstants
import com.gima.gve.domain.model.TimelineProject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Deterministic JSON serializer for autosave and portable `.gve.json` project files.
 */
class ProjectJsonSerializer(
    private val json: Json = defaultJson
) {
    fun encode(project: TimelineProject): String {
        project.validateIntegrity()
        return json.encodeToString(TimelineProject.serializer(), project)
    }

    fun decode(rawJson: String): Result<TimelineProject> = runCatching {
        val project = json.decodeFromString(TimelineProject.serializer(), rawJson)
        if (project.schemaVersion > TimelineConstants.SCHEMA_VERSION) {
            throw SerializationException(
                "Unsupported project schemaVersion ${project.schemaVersion}. Max supported is ${TimelineConstants.SCHEMA_VERSION}."
            )
        }
        project.validateIntegrity()
        project
    }

    companion object {
        val defaultJson: Json = Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}
