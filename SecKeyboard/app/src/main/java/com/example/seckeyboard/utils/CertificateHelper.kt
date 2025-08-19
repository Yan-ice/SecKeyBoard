package com.example.seckeyboard.utils

import android.util.Base64
import java.io.InputStream
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

object X509CertificateHelper {

    /**
     * 从输入流加载 X.509 证书
     */
    fun loadCertificate(input: InputStream): X509Certificate {
        val cf = CertificateFactory.getInstance("X.509")
        return cf.generateCertificate(input) as X509Certificate
    }

    /**
     * 验证证书有效期
     */
    fun checkValidity(cert: X509Certificate): Boolean {
        return try {
            cert.checkValidity()
            true
        } catch (e: Exception) {
            false
        }
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
}
