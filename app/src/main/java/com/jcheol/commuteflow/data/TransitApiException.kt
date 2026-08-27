package com.jcheol.commuteflow.data

import java.io.IOException

class MissingProviderKeyException(
    val providerName: String,
    val propertyName: String,
) : IllegalStateException("$providerName API 키가 없습니다: $propertyName")

class TransitApiException(
    val userMessage: String,
) : IOException(userMessage)