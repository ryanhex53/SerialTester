package com.zhaoxinsoft.serialtester

import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import com.zhaoxinsoft.serialtester.ui.theme.SerialTesterTheme

class MainActivity : ComponentActivity() {
  private val viewModel: MainViewModel by viewModels()
  private var serialPort: SerialPort? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // discover all available serial ports
    val ports = SerialPort.getCommPorts()
    viewModel.availablePorts.clear()
    viewModel.availablePorts.addAll(ports.map { it.systemPortName })

    setContent {
      SerialTesterTheme {
        // A surface container using the 'background' color from the theme
        Surface(
          modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
        ) {
          HomeScreen(viewModel)
        }
      }
    }
  }

  private fun connect() {
    try {
      serialPort?.closePort()
      serialPort = SerialPort.getCommPort(viewModel.selectedDevice.value)
      serialPort?.numDataBits = 8
      serialPort?.parity = SerialPort.NO_PARITY
      serialPort?.numStopBits = 1
      serialPort?.baudRate = viewModel.baudRate.value?.toInt() ?: 9600
      serialPort?.addDataListener(object : SerialPortDataListener {
        override fun getListeningEvents(): Int {
          return SerialPort.LISTENING_EVENT_DATA_RECEIVED
        }

        override fun serialEvent(event: SerialPortEvent) {
          if (event.eventType != SerialPort.LISTENING_EVENT_DATA_RECEIVED) return
          val newData = event.receivedData
          val str = if (viewModel.hexMode.value) {
            newData.joinToString(" ") { String.format("%02X", it) }
          } else {
            newData.toString(Charsets.UTF_8)
          }
          viewModel.receivedData.add(str)
          Log.d("M", "Received data: $str")
        }
      })
      serialPort?.openPort()
      viewModel.isConnected.value = true
    } catch (e: Exception) {
      Toast.makeText(this, "Failed to connect to port", Toast.LENGTH_SHORT).show()
      Log.e("M", "Failed to connect to port", e)
    }
  }

  private fun send() {
    // close soft keyboard
    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    // send data
    val data = if (viewModel.hexMode.value) {
      viewModel.data.value?.split(" ")?.map { it.toInt(16).toByte() }?.toByteArray()
    } else {
      viewModel.data.value?.toByteArray()
    }
    if (data != null) {
      if (data.isEmpty()) return
      serialPort?.writeBytes(data, data.size.toLong())
      Log.d("M", "Sent data: ${data.joinToString(" ") { String.format("%02X", it) }}")
    }
  }

  private fun disconnect() {
    serialPort?.closePort()
    serialPort?.removeDataListener()
    viewModel.receivedData.clear()
    viewModel.isConnected.value = false
  }

  private fun clear() {
//    viewModel.data.value = null
    viewModel.receivedData.clear()
  }

  override fun onStop() {
    super.onStop()
    if (serialPort?.isOpen == true) {
      serialPort?.closePort()
      serialPort?.removeDataListener()
    }
  }

  @Composable
  fun HomeScreen(viewModel: MainViewModel) {
    Column(
      modifier = Modifier.padding(horizontal = 8.dp)
    ) {
      val selectedDevice by viewModel.selectedDevice
      DeviceSelector(
        devices = viewModel.availablePorts.filterNotNull(), onDeviceSelected = {
          viewModel.selectedDevice.value = it
        }, enabled = !viewModel.isConnected.value, selectedDevice = selectedDevice
      )
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        var baudRate by viewModel.baudRate
        TextField(
          modifier = Modifier
            .weight(1f)
            .padding(end = 8.dp),
          value = baudRate?.toString() ?: "",
          label = {
            Text("Baud rate")
          },
          placeholder = {
            Text("Input Baud rate here")
          },
          onValueChange = { newText ->
            if (newText.all { it.isDigit() }) {
              baudRate = newText.toInt()
            }
          },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          visualTransformation = VisualTransformation.None,
          enabled = !viewModel.isConnected.value
        )
        Text(text = "Hex mode")
        Checkbox(checked = viewModel.hexMode.value, onCheckedChange = {
          viewModel.hexMode.value = it
        })
        if (viewModel.isConnected.value) {
          Button(onClick = { disconnect() }) {
            Text("Disconnect")
          }
        } else {
          Button(enabled = selectedDevice != null && baudRate != null, onClick = {
            connect()
          }) {
            Text("Connect")
          }
        }
      }
      if (viewModel.isConnected.value) {
        TextField(modifier = Modifier.fillMaxWidth(), value = viewModel.data.value ?: "", label = {
          Text("Send data")
        }, placeholder = {
          Text("Input data to send")
        }, onValueChange = { newText ->
          viewModel.data.value = newText
        })
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Button(onClick = { clear() }) {
            Text("Clear")
          }
          Button(enabled = viewModel.data.value?.isNotEmpty() == true, onClick = { send() }) {
            Text("Send")
          }
        }
      }
      LazyVerticalGrid(
        columns = GridCells.Fixed(1), modifier = Modifier.fillMaxWidth(),
      ) {
        items(viewModel.receivedData) { data ->
          Text(data)
        }
      }
    }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun DeviceSelector(
    devices: List<String>,
    selectedDevice: String? = null,
    enabled: Boolean = true,
    onDeviceSelected: (String) -> Unit
  ) {
    var selected by rememberSaveable { mutableStateOf(selectedDevice) }
    LazyVerticalGrid(
      columns = GridCells.Fixed(2), modifier = Modifier.fillMaxWidth(),
    ) {
      items(devices) { device ->
        FilterChip(
          modifier = Modifier.padding(horizontal = 8.dp),
          onClick = { selected = device; onDeviceSelected(device) },
          label = {
            Text(device)
          },
          selected = selected == device,
          enabled = enabled,
          leadingIcon = if (selected == device) {
            {
              Icon(
                imageVector = Icons.Filled.Done,
                contentDescription = "Done icon",
                modifier = Modifier.size(24.dp)
              )
            }
          } else {
            null
          },
        )
      }
    }
  }

  @Preview(showBackground = true)
  @Composable
  fun PreviewHomeScreen() {
    val viewModelDemo = MainViewModel()
    viewModelDemo.availablePorts.addAll(
      listOf(
        "ttyS0", "ttyS1", "ttyS3", "ttyS4"
      )
    )
    SerialTesterTheme {
      // A surface container using the 'background' color from the theme
      Surface(
        modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
      ) {
        HomeScreen(viewModelDemo)
      }
    }
  }
}


