package com.deepfish.pet.gateway

import android.content.Context
import android.util.Base64
import java.security.MessageDigest

/**
 * 持久化的 Ed25519 设备身份，用于向 openclaw Gateway 注册本机 node。
 * 协议与官方 Android node 一致（V3 签名 payload）。
 */
data class NodeIdentity(
    val deviceId: String,
    val publicKeyRawBase64: String,
    val privateKeyPkcs8Base64: String,
)

object NodeIdentityStore {

    private const val KEY_PRIVATE = "gateway_ed25519_private"
    private const val KEY_PUBLIC = "gateway_ed25519_public"
    private const val KEY_DEVICE_ID = "gateway_device_id"

    @Volatile private var cached: NodeIdentity? = null

    @Synchronized
    fun loadOrCreate(context: Context): NodeIdentity {
        cached?.let { return it }
        val prefs = context.applicationContext.getSharedPreferences("deepfish_pet", Context.MODE_PRIVATE)
        val storedPrivate = prefs.getString(KEY_PRIVATE, null)
        val storedPublic = prefs.getString(KEY_PUBLIC, null)
        val storedId = prefs.getString(KEY_DEVICE_ID, null)
        if (storedPrivate != null && storedPublic != null && storedId != null) {
            return NodeIdentity(storedId, storedPublic, storedPrivate).also { cached = it }
        }
        val fresh = generate()
        prefs.edit()
            .putString(KEY_PRIVATE, fresh.privateKeyPkcs8Base64)
            .putString(KEY_PUBLIC, fresh.publicKeyRawBase64)
            .putString(KEY_DEVICE_ID, fresh.deviceId)
            .apply()
        cached = fresh
        return fresh
    }

    private fun generate(): NodeIdentity {
        val (rawPrivate, rawPublic) = Ed25519Keys.generate()
        val privatePkcs8 = Ed25519Keys.encodePkcs8(rawPrivate)
        return NodeIdentity(
            deviceId = sha256Hex(rawPublic),
            publicKeyRawBase64 = Base64.encodeToString(rawPublic, Base64.NO_WRAP),
            privateKeyPkcs8Base64 = Base64.encodeToString(privatePkcs8, Base64.NO_WRAP),
        )
    }

    /** 构建 Gateway 验证的 V3 认证 payload。 */
    fun buildV3AuthPayload(
        deviceId: String,
        clientId: String,
        clientMode: String,
        role: String,
        scopes: List<String>,
        signedAtMs: Long,
        token: String?,
        nonce: String,
        platform: String?,
        deviceFamily: String?,
    ): String = listOf(
        "v3",
        deviceId,
        clientId,
        clientMode,
        role,
        scopes.joinToString(","),
        signedAtMs.toString(),
        token.orEmpty(),
        nonce,
        normalizeMetadataField(platform),
        normalizeMetadataField(deviceFamily),
    ).joinToString("|")

    /** 用持久化的 Ed25519 私钥签名，返回无填充 URL-safe base64。 */
    fun signPayload(payload: String, identity: NodeIdentity): String? = try {
        val privateBytes = Base64.decode(identity.privateKeyPkcs8Base64, Base64.DEFAULT)
        val signature = Ed25519Keys.signPkcs8(privateBytes, payload.toByteArray(Charsets.UTF_8))
        base64UrlEncode(signature)
    } catch (e: Throwable) {
        android.util.Log.e("NodeIdentity", "signPayload failed: ${e.message}")
        null
    }

    /** 返回无填充 URL-safe base64 编码的原始公钥。 */
    fun publicKeyBase64Url(identity: NodeIdentity): String? = try {
        val raw = Base64.decode(identity.publicKeyRawBase64, Base64.DEFAULT)
        base64UrlEncode(raw)
    } catch (_: Throwable) {
        null
    }

    private fun normalizeMetadataField(value: String?): String {
        val trimmed = value?.trim().orEmpty()
        val out = StringBuilder(trimmed.length)
        for (ch in trimmed) {
            out.append(if (ch in 'A'..'Z') (ch.code + 32).toChar() else ch)
        }
        return out.toString()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun base64UrlEncode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP).trimEnd('=')
}
