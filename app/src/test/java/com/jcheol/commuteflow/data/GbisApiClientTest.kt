package com.jcheol.commuteflow.data

import org.junit.Test

class GbisApiClientTest {
    @Test(expected = MissingApiKeyException::class)
    fun `rejects a blank key before opening a connection`() {
        GbisApiClient(serviceKey = " ").getArrivals("test-station")
    }
}
