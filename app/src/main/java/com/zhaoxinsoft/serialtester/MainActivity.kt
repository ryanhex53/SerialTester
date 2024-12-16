package com.zhaoxinsoft.serialtester

import android.os.Bundle
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import com.zhaoxinsoft.serialtester.ui.theme.SerialTesterTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
      serialPort?.numDataBits = viewModel.dataBits.value?.toInt() ?: 8
      serialPort?.parity = viewModel.parity.value?.toInt() ?: SerialPort.NO_PARITY
      serialPort?.numStopBits = viewModel.stopBits.value?.toInt() ?: SerialPort.ONE_STOP_BIT
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
          val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
          viewModel.receivedData.add("[$timestamp] RX: $str")
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
      val sent = serialPort?.writeBytes(data, data.size.toLong())
      Log.d("M", "Sent data: ${data.joinToString(" ") { String.format("%02X", it) }}")
      if (sent == data.size) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        viewModel.receivedData.add("[$timestamp] TX: ${viewModel.data.value}")
      }
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

  @OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
  @Composable
  fun HomeScreen(viewModel: MainViewModel) {
    Scaffold(topBar = {
      TopAppBar(colors = topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        titleContentColor = MaterialTheme.colorScheme.primary,
      ), title = { Text("Serial Port Tester") })
    }) { pd ->
      Column(
        modifier = Modifier
          .padding(
            top = pd.calculateTopPadding() + 8.dp,
            bottom = pd.calculateBottomPadding() + 8.dp,
            start = pd.calculateStartPadding(LocalLayoutDirection.current) + 8.dp,
            end = pd.calculateEndPadding(LocalLayoutDirection.current) + 8.dp
          )
      ) {
        val selectedDevice by viewModel.selectedDevice
        var baudRate by viewModel.baudRate
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          DropDownList(
            modifier = Modifier.width(180.dp),
            values = viewModel.availablePorts.filterNotNull(),
            label = "Devices",
            selectedValue = selectedDevice,
            enabled = !viewModel.isConnected.value,
            onItemSelected = {
              viewModel.selectedDevice.value = it
            })
          TextField(
            modifier = Modifier.width(150.dp),
            value = baudRate?.toString() ?: "",
            label = {
              Text("Baud rate")
            },
            onValueChange = { newText ->
              baudRate =
                newText.takeIf { it.isNotEmpty() && it.all { char -> char.isDigit() } }?.toInt()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = VisualTransformation.None,
            enabled = !viewModel.isConnected.value
          )
          DropDownList(
            values = listOf("8", "7"),
            selectedValue = viewModel.dataBits.value?.toString() ?: "8",
            label = "Data bits",
            enabled = !viewModel.isConnected.value,
            modifier = Modifier.width(150.dp),
            onItemSelected = {
              viewModel.dataBits.value = it.toInt()
            }
          )
          DropDownList(
            modifier = Modifier.width(120.dp),
            values = listOf("0", "1", "2", "3", "4"),
            selectedValue = viewModel.parity.value?.toString() ?: "0",
            label = "Parity",
            enabled = !viewModel.isConnected.value,
            items = listOf("None", "Odd", "Even", "Mark", "Space"),
            onItemSelected = {
              viewModel.parity.value = it.toInt()
            }
          )
          DropDownList(
            modifier = Modifier.width(150.dp),
            values = listOf("1", "2", "3"),
            selectedValue = viewModel.stopBits.value?.toString() ?: "1",
            label = "Stop bits",
            enabled = !viewModel.isConnected.value,
            items = listOf("1", "1.5", "2"),
            onItemSelected = {
              viewModel.stopBits.value = it.toInt()
            },
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Hex", modifier = Modifier.clickable {
              viewModel.hexMode.value = !viewModel.hexMode.value
            })
            Checkbox(checked = viewModel.hexMode.value, onCheckedChange = {
              viewModel.hexMode.value = it
            })
            Text(text = "Echo", modifier = Modifier.clickable {
              viewModel.echoMode.value = !viewModel.echoMode.value
            })
            Checkbox(checked = viewModel.echoMode.value, onCheckedChange = {
              viewModel.echoMode.value = it
            })
          }
          if (viewModel.isConnected.value) {
            OutlinedButton(onClick = { disconnect() }) {
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
          TextField(
            modifier = Modifier.fillMaxWidth(),
            value = viewModel.data.value ?: "",
            label = {
              Text("Send data")
            },
            placeholder = {
              Text("Input data to send")
            },
            onValueChange = { newText ->
              viewModel.data.value = newText
            })
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            OutlinedButton(onClick = { clear() }) {
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
          item {
            // put static item here
          }
          items(viewModel.receivedData) { data ->
            Text(data)
          }
        }
      }
    }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun DropDownList(
    values: List<String>,
    selectedValue: String?,
    modifier: Modifier = Modifier,
    items: List<String>? = null,
    label: String? = null,
    enabled: Boolean? = true,
    onItemSelected: (String) -> Unit
  ) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(
      expanded = expanded,
      onExpandedChange = { if (enabled == true) expanded = !expanded },
      modifier = modifier,
    ) {
      TextField(
        value = if (selectedValue != null) values.indexOf(selectedValue)
          .let { items?.getOrNull(it) }
          ?: selectedValue else "",
        onValueChange = {},
        enabled = enabled == true,
        readOnly = true,
        label = { if (label != null) Text(label) },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        modifier = Modifier.menuAnchor()
      )
      ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
      ) {
        values.forEachIndexed { i, item ->
          DropdownMenuItem(
            text = { Text(items?.get(i) ?: item) },
            onClick = {
              onItemSelected(item)
              expanded = false
            }
          )
        }
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


