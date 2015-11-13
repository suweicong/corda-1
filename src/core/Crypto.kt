package core

import com.google.common.io.BaseEncoding
import java.security.*

// "sealed" here means there can't be any subclasses other than the ones defined here.
sealed class SecureHash(bits: ByteArray) : OpaqueBytes(bits) {
    class SHA256(bits: ByteArray) : SecureHash(bits) {
        init { require(bits.size == 32) }
        override val signatureAlgorithmName: String get() = "SHA256withECDSA"
    }

    // Like static methods in Java, except the 'companion' is a singleton that can have state.
    companion object {
        fun parse(str: String) = BaseEncoding.base16().decode(str.toLowerCase()).let {
            when (it.size) {
                32 -> SecureHash.SHA256(it)
                else -> throw IllegalArgumentException("Provided string is not 32 bytes in base 16 (hex): $str")
            }
        }

        fun sha256(bits: ByteArray) = SHA256(MessageDigest.getInstance("SHA-256").digest(bits))
        fun sha256(str: String) = sha256(str.toByteArray())
    }

    abstract val signatureAlgorithmName: String

    // In future, maybe SHA3, truncated hashes etc.
}

/**
 * A wrapper around a digital signature. The covering field is a generic tag usable by whatever is interpreting the
 * signature. It isn't used currently, but experience from Bitcoin suggests such a feature is useful, especially when
 * building partially signed transactions.
 */
open class DigitalSignature(bits: ByteArray, val covering: Int = 0) : OpaqueBytes(bits) {

    /** A digital signature that identifies who the public key is owned by. */
    open class WithKey(val by: PublicKey, bits: ByteArray, covering: Int = 0) : DigitalSignature(bits, covering) {
        fun verifyWithECDSA(content: ByteArray) = by.verifyWithECDSA(content, this)
    }

    class LegallyIdentifiable(val signer: Institution, bits: ByteArray, covering: Int) : WithKey(signer.owningKey, bits, covering)

}

object NullPublicKey : PublicKey, Comparable<PublicKey> {
    override fun getAlgorithm() = "NULL"
    override fun getEncoded() = byteArrayOf(0)
    override fun getFormat() = "NULL"
    override fun compareTo(other: PublicKey): Int = if (other == NullPublicKey) 0 else -1
    override fun toString() = "NULL_KEY"
}

/** Utility to simplify the act of signing a byte array */
fun PrivateKey.signWithECDSA(bits: ByteArray, publicKey: PublicKey? = null): DigitalSignature {
    val signer = Signature.getInstance("SHA256withECDSA")
    signer.initSign(this)
    signer.update(bits)
    val sig = signer.sign()
    return if (publicKey == null) DigitalSignature(sig) else DigitalSignature.WithKey(publicKey, sig)
}

/** Utility to simplify the act of verifying a signature */
fun PublicKey.verifyWithECDSA(content: ByteArray, signature: DigitalSignature) {
    val verifier = Signature.getInstance("SHA256withECDSA")
    verifier.initVerify(this)
    verifier.update(content)
    if (verifier.verify(signature.bits) == false)
        throw SignatureException("Signature did not match")
}