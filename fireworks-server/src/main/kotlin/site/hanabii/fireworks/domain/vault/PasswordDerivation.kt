package site.hanabii.fireworks.domain.vault

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object PasswordDerivation {

    private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256  // bits
    private const val SALT_LENGTH = 16  // bytes

    private val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
    private val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val DIGITS = "0123456789"
    private val SYMBOLS = "!@#\$%^&*()-_=+[]{};:,.<>?"

    private val secureRandom = SecureRandom()

    fun generateSalt(): ByteArray = ByteArray(SALT_LENGTH).apply { secureRandom.nextBytes(this) }

    fun deriveSeed(masterPassword: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(masterPassword.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        return factory.generateSecret(spec).encoded
    }

    fun derivePassword(seed: ByteArray, entry: PasswordEntry, counter: Int = entry.counter): String {
        val label = "${entry.website}|${entry.username}|$counter"
        val charset = buildCharset(entry)
        val minBytes = maxOf(32, entry.length * 2)
        val raw = expandEntropy(seed, label, minBytes)
        return encode(raw, charset, entry)
    }

    private fun buildCharset(entry: PasswordEntry): String {
        val sb = StringBuilder()
        if (entry.useLowercase) sb.append(LOWERCASE)
        if (entry.useUppercase) sb.append(UPPERCASE)
        if (entry.useDigits) sb.append(DIGITS)
        if (entry.useSymbols) sb.append(SYMBOLS)
        return sb.toString()
    }

    private fun expandEntropy(seed: ByteArray, label: String, minBytes: Int): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(seed, HMAC_ALGORITHM))
        val out = ByteArrayOutputStream()
        var i = 0
        while (out.size() < minBytes) {
            val hmac = mac.doFinal("$label#$i".toByteArray(Charsets.UTF_8))
            out.write(hmac)
            i++
        }
        return out.toByteArray()
    }

    private fun encode(raw: ByteArray, chars: String, entry: PasswordEntry): String {
        val n = chars.length.toBigInteger()
        var value = BigInteger(1, raw)  // positive magnitude
        val result = CharArray(entry.length)

        for (i in 0 until entry.length) {
            val divAndRem = value.divideAndRemainder(n)
            value = divAndRem[0]
            result[i] = chars[divAndRem[1].toInt()]
        }

        // 强制注入缺失的字符集
        val subsets = buildCharsetSubsets(entry)
        for (subset in subsets) {
            if (result.any { it in subset }) continue
            val pos = (value % entry.length.toBigInteger()).toInt()
            value = value / entry.length.toBigInteger()
            val charIdx = (value % subset.length.toBigInteger()).toInt()
            value = value / subset.length.toBigInteger()
            result[pos] = subset[charIdx]
        }

        return String(result)
    }

    private fun buildCharsetSubsets(entry: PasswordEntry): List<String> {
        val subsets = mutableListOf<String>()
        if (entry.useLowercase) subsets.add(LOWERCASE)
        if (entry.useUppercase) subsets.add(UPPERCASE)
        if (entry.useDigits) subsets.add(DIGITS)
        if (entry.useSymbols) subsets.add(SYMBOLS)
        return subsets
    }
}
