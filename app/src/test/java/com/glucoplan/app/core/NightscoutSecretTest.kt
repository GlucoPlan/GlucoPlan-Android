package com.glucoplan.app.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NightscoutSecretTest {

    @Test
    fun `пустой секрет не отправляется`() {
        assertThat(hashNightscoutSecret("")).isNull()
        assertThat(hashNightscoutSecret("   ")).isNull()
    }

    @Test
    fun `сырой секрет хешируется SHA1`() {
        // SHA1("") не должен уходить на сервер — это как раз ломало чтение
        assertThat(hashNightscoutSecret("")).isNull()
        val hash = hashNightscoutSecret("mysecret")
        assertThat(hash).hasLength(40)
        assertThat(hash).isEqualTo("e9fe51f94eadabf54dbf2fbbd57188b9abee436e")
    }

    @Test
    fun `уже посчитанный SHA1 не хешируется повторно`() {
        val sha = "da39a3ee5e6b4b0d3255bfef95601890afd80709"
        assertThat(hashNightscoutSecret(sha)).isEqualTo(sha)
        assertThat(hashNightscoutSecret(sha.uppercase())).isEqualTo(sha)
    }
}
