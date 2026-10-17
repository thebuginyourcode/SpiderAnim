package com.spideranim.model

import org.json.JSONObject
import java.io.File

data class ProjectInfo(val id: Long, val name: String, val width: Int, val height: Int, val modified: Long)

/** One JSON file per project, plus a small info file so the gallery doesn't parse every project. */
class ProjectStore(private val dir: File) {
    init {
        dir.mkdirs()
    }

    private fun file(id: Long) = File(dir, "$id.json")
    private fun info(id: Long) = File(dir, "$id.info")
    fun thumbnail(id: Long) = File(dir, "$id.png")

    fun list(): List<ProjectInfo> = dir.listFiles { f -> f.name.endsWith(".info") }.orEmpty().mapNotNull { f ->
        runCatching {
            val o = JSONObject(f.readText())
            ProjectInfo(o.getLong("id"), o.getString("name"), o.getInt("width"), o.getInt("height"), o.getLong("modified"))
        }.getOrNull()
    }.sortedByDescending { it.modified }

    fun load(id: Long): Project? = runCatching { ProjectJson.read(file(id).readText()) }.getOrNull()

    fun save(p: Project) {
        write(file(p.id), ProjectJson.write(p))
        write(info(p.id), JSONObject().apply {
            put("id", p.id); put("name", p.name); put("width", p.width); put("height", p.height)
            put("modified", System.currentTimeMillis())
        }.toString())
    }

    fun delete(id: Long) {
        file(id).delete()
        info(id).delete()
        thumbnail(id).delete()
    }

    private fun write(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
    }
}
