package com.strukai.thermal.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import com.strukai.thermal.model.PrinterDeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class BluetoothPrinterManager(private val context: Context) {

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private fun getAdapter(): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    @SuppressLint("MissingPermission")
    fun getPairedPrinters(): Result<List<PrinterDeviceInfo>> {
        return runCatching {
            val adapter = getAdapter()
                ?: throw IllegalStateException("Perangkat tidak mendukung Bluetooth.")
            if (!adapter.isEnabled) {
                throw IllegalStateException("Bluetooth belum aktif. Aktifkan Bluetooth terlebih dahulu.")
            }
            val bonded = adapter.bondedDevices.orEmpty()
            bonded.map { device ->
                PrinterDeviceInfo(
                    name = device.name?.ifBlank { "Perangkat Bluetooth" } ?: "Perangkat Bluetooth",
                    address = device.address
                )
            }.sortedBy { it.name.lowercase() }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun printBytes(deviceAddress: String, payload: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val adapter = getAdapter()
                    ?: throw IllegalStateException("Bluetooth tidak tersedia di perangkat ini.")
                if (!adapter.isEnabled) {
                    throw IllegalStateException("Bluetooth belum aktif.")
                }
                if (deviceAddress.isBlank()) {
                    throw IllegalStateException("Pilih printer Bluetooth terlebih dahulu.")
                }

                val device = adapter.getRemoteDevice(deviceAddress)
                runCatching { adapter.cancelDiscovery() }

                val socket = device.createRfcommSocketToServiceRecord(sppUuid)
                try {
                    socket.connect()
                    val output = socket.outputStream
                    val chunkSize = 512
                    var offset = 0
                    while (offset < payload.size) {
                        val length = minOf(chunkSize, payload.size - offset)
                        output.write(payload, offset, length)
                        output.flush()
                        offset += length
                        Thread.sleep(20)
                    }
                    Thread.sleep(150)
                } catch (e: IOException) {
                    throw IOException(
                        "Gagal terhubung ke printer (${device.name ?: deviceAddress}). Pastikan printer menyala dan sudah dipasangkan (paired).",
                        e
                    )
                } finally {
                    runCatching { socket.close() }
                }
            }
        }
}
