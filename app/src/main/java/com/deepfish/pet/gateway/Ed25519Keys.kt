package com.deepfish.pet.gateway

import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.asn1.edec.EdECObjectIdentifiers
import java.security.SecureRandom

/** Ed25519 轻量 API 封装（直接使用 BouncyCastle，避免 R8 破坏 JCA Provider 注册）。 */
internal object Ed25519Keys {

    fun generate(): Pair<ByteArray, ByteArray> {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = generator.generateKeyPair()
        val privateKey = keyPair.private as Ed25519PrivateKeyParameters
        val publicKey = keyPair.public as Ed25519PublicKeyParameters
        return privateKey.encoded to publicKey.encoded
    }

    /** 把 32 字节原始私钥编码为 PKCS#8（算法标识 + OCTET STRING）。 */
    fun encodePkcs8(rawPrivate: ByteArray): ByteArray =
        PrivateKeyInfo(
            AlgorithmIdentifier(EdECObjectIdentifiers.id_Ed25519),
            DEROctetString(rawPrivate),
        ).encoded

    /** 用 PKCS#8 私钥对 payload 签名，返回 64 字节签名。 */
    fun signPkcs8(pkcs8: ByteArray, data: ByteArray): ByteArray {
        val pkInfo = PrivateKeyInfo.getInstance(pkcs8)
        val parsed = pkInfo.parsePrivateKey()
        val rawPrivate = DEROctetString.getInstance(parsed).octets
        val privateKey = Ed25519PrivateKeyParameters(rawPrivate, 0)
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }
}
