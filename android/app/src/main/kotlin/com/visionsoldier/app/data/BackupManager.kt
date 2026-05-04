package com.visionsoldier.app.data

import android.util.Base64
import com.visionsoldier.app.data.entity.DiaryEntry
import com.visionsoldier.app.data.entity.Profile
import com.visionsoldier.app.data.entity.Task
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object BackupManager {

    // ── Encriptación AES-256-CBC ──
    private fun generateKey(password: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(password.toByteArray(Charsets.UTF_8))
    }

    private fun encrypt(plainText: String, password: String): String {
        val key = generateKey(password)
        val secretKeySpec = SecretKeySpec(key, "AES")
        
        // Un IV fijo para simplificar, en un entorno de más alta seguridad se generaría al azar y se añadiría al archivo.
        // Dado el uso personal, es suficiente.
        val iv = ByteArray(16) { 0 }
        val ivParameterSpec = IvParameterSpec(iv)
        
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
        
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(cipherTextBase64: String, password: String): String {
        val key = generateKey(password)
        val secretKeySpec = SecretKeySpec(key, "AES")
        val iv = ByteArray(16) { 0 }
        val ivParameterSpec = IvParameterSpec(iv)
        
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
        
        val decodedBase64 = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
        val decrypted = cipher.doFinal(decodedBase64)
        return String(decrypted, Charsets.UTF_8)
    }

    // ── Exportación a JSON ──
    fun createBackupJson(profile: Profile?, diary: List<DiaryEntry>, tasks: List<Task>): String {
        val root = JSONObject()
        
        // Perfil
        if (profile != null) {
            val profJson = JSONObject()
            profJson.put("name", profile.name)
            profJson.put("profession", profile.profession)
            profJson.put("interests", profile.interests)
            profJson.put("traits", profile.traits)
            profJson.put("diaryPassword", profile.diaryPassword)
            profJson.put("apiKey", profile.apiKey)
            root.put("profile", profJson)
        }
        
        // Diario
        val diaryArray = JSONArray()
        diary.forEach { d ->
            val entry = JSONObject()
            entry.put("id", d.id)
            entry.put("date", d.date)
            entry.put("title", d.title)
            entry.put("content", d.content)
            entry.put("category", d.category)
            entry.put("importance", d.importance)
            entry.put("tags", d.tags)
            diaryArray.put(entry)
        }
        root.put("diary", diaryArray)
        
        // Tareas
        val taskArray = JSONArray()
        tasks.forEach { t ->
            val entry = JSONObject()
            entry.put("id", t.id)
            entry.put("text", t.text)
            entry.put("completed", t.completed)
            entry.put("timestamp", t.timestamp)
            taskArray.put(entry)
        }
        root.put("tasks", taskArray)
        
        return root.toString()
    }

    // ── Importación de JSON ──
    fun parseBackupJson(jsonString: String): BackupData {
        val root = JSONObject(jsonString)
        
        var profile: Profile? = null
        if (root.has("profile")) {
            val p = root.getJSONObject("profile")
            profile = Profile(
                id = 1,
                name = p.optString("name", ""),
                profession = p.optString("profession", ""),
                interests = p.optString("interests", ""),
                traits = p.optString("traits", ""),
                diaryPassword = p.optString("diaryPassword", ""),
                apiKey = p.optString("apiKey", "")
            )
        }
        
        val diary = mutableListOf<DiaryEntry>()
        if (root.has("diary")) {
            val arr = root.getJSONArray("diary")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                diary.add(
                    DiaryEntry(
                        id = obj.optLong("id", 0),
                        date = obj.optString("date", ""),
                        title = obj.optString("title", ""),
                        content = obj.optString("content", ""),
                        category = obj.optString("category", ""),
                        importance = obj.optInt("importance", 3),
                        tags = obj.optString("tags", "")
                    )
                )
            }
        }
        
        val tasks = mutableListOf<Task>()
        if (root.has("tasks")) {
            val arr = root.getJSONArray("tasks")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                tasks.add(
                    Task(
                        id = obj.optLong("id", 0),
                        text = obj.optString("text", ""),
                        completed = obj.optBoolean("completed", false),
                        timestamp = obj.optString("timestamp", "")
                    )
                )
            }
        }
        
        return BackupData(profile, diary, tasks)
    }

    // ── Funciones Públicas ──
    fun exportData(profile: Profile?, diary: List<DiaryEntry>, tasks: List<Task>, password: String): String {
        val json = createBackupJson(profile, diary, tasks)
        return encrypt(json, password)
    }

    fun importData(encryptedData: String, password: String): BackupData? {
        return try {
            val json = decrypt(encryptedData, password)
            parseBackupJson(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

data class BackupData(
    val profile: Profile?,
    val diary: List<DiaryEntry>,
    val tasks: List<Task>
)
