package com.revenge.usbaudio.model

data class DacFormat(
    val interfaceNumber: Int,
    val altSetting: Int,
    val endpointAddress: Int,
    val syncEndpointAddress: Int? = null,
    val bitDepth: Int,
    val subslotBytes: Int,
    val channels: Int,
    val sampleRates: List<Int>,
)
