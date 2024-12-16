package com.zhaoxinsoft.serialtester

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
  val availablePorts = mutableListOf<String?>(null)
  val isConnected = mutableStateOf(true)
  val selectedDevice = mutableStateOf<String?>(null)
  val baudRate = mutableStateOf<Number?>(9600)
  val dataBits = mutableStateOf<Number?>(8)
  val parity = mutableStateOf<Number?>(0)
  val stopBits = mutableStateOf<Number?>(1)
  val data = mutableStateOf<String?>("01 03 00 02 00 01 25 CA")
  val hexMode = mutableStateOf(true)
  val echoMode = mutableStateOf(true)
  val receivedData = mutableStateListOf<String>()
}