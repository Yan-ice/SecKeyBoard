package com.example.seckeyboard.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale

object CertificateHelper {

    /**
     * 从输入流加载 X.509 证书
     */
    fun loadCertificateFromBytes(certBytes: ByteArray): X509Certificate {
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
    }

    /**
     * 验证证书签名（完整性）
     * @param cert 要验证的证书
     * @param issuerPublicKey 上级证书的公钥
     */
    fun verifySignature(cert: X509Certificate, issuerPublicKey: PublicKey): Boolean {
        return try {
            cert.verify(issuerPublicKey)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 从证书中提取公钥（PEM 格式）
     */
    fun getPublicKeyPem(cert: X509Certificate): String {
        val publicKey = cert.publicKey
        val base64 = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
    }

    /**
     * 提取证书基本信息
     */
    fun getBasicInfo(cert: X509Certificate): Map<String, String> {
        return mapOf(
            "Subject" to cert.subjectDN.toString(),
            "Issuer" to cert.issuerDN.toString(),
            "Valid From" to cert.notBefore.toString(),
            "Valid Until" to cert.notAfter.toString(),
            "Serial Number" to cert.serialNumber.toString(),
            "Signature Algorithm" to cert.sigAlgName
        )
    }

    fun printCertificateInfo(tag: String, cert: X509Certificate) {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            // 基本信息
            Log.i(tag, "Certificate Subject: ${cert.subjectDN}")
            Log.i(tag, "Certificate Issuer: ${cert.issuerDN}")
            Log.i(tag, "Serial Number: ${cert.serialNumber}")
            Log.i(tag, "Version: ${cert.version}")
            Log.i(tag, "Signature Algorithm: ${cert.sigAlgName}")

            // 有效期
            Log.i(tag, "Not Before: ${sdf.format(cert.notBefore)}")
            Log.i(tag, "Not After: ${sdf.format(cert.notAfter)}")

            // 公钥信息
            val pubKey = cert.publicKey
            Log.i(tag, "Public Key Algorithm: ${pubKey.algorithm}")
            Log.i(tag, "Public Key Format: ${pubKey.format}")
            Log.i(tag, "Public Key Encoded Length: ${pubKey.encoded.size} bytes")

            // 扩展信息（可选，标准扩展）
            val keyUsage = cert.keyUsage
            if (keyUsage != null) {
                Log.i(tag, "Key Usage: ${keyUsage.joinToString()}")
            }

            val extKeyUsage = cert.extendedKeyUsage
            if (extKeyUsage != null) {
                Log.i(tag, "Extended Key Usage: ${extKeyUsage.joinToString()}")
            }

            val san = cert.subjectAlternativeNames
            if (san != null) {
                Log.i(tag, "Subject Alternative Names:")
                san.forEach { Log.i(tag, "  $it") }
            }

            // 获取非关键扩展 OID
            val nonCriticalOids = cert.nonCriticalExtensionOIDs
            Log.i(tag, "Non-critical OIDs: $nonCriticalOids")
            // 获取关键扩展 OID
            val criticalOids = cert.criticalExtensionOIDs
            Log.i(tag, "Critical OIDs: $criticalOids")

            for (oid in nonCriticalOids.orEmpty() + criticalOids.orEmpty()) {
                val extValue = cert.getExtensionValue(oid)  // 返回 ASN.1 DER 编码的 OCTET STRING
                if (extValue != null && extValue.size > 2) {
                    // 跳过外层的 04 和长度字节
                    val inner = extValue.copyOfRange(2, extValue.size)

                    val parsed: String = java.math.BigInteger(inner).toString()

                    Log.i(tag, "Extension $oid parsed: $parsed")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to print certificate info", e)
        }
    }
}
