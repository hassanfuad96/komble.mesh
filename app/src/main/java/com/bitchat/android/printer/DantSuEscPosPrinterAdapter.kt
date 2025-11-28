package com.bitchat.android.printer

import android.content.Context
import android.util.Log
import com.bitchat.android.mesh.BluetoothPermissionManager
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.connection.tcp.TcpConnection

/**
 * Lightweight adapter around DantSu ESC/POS Printer library to print simple receipts.
 * This focuses on Bluetooth printing; a TCP example is also provided.
 */
object DantSuEscPosPrinterAdapter {

    private const val TAG = "DantSuEscPosPrinterAdapter"

    /**
     * Prints a sample receipt via the first paired Bluetooth printer.
     * Returns true on success, false otherwise.
     */
    fun printSampleBluetooth(context: Context): Boolean {
        try {
            if (!BluetoothPermissionManager(context).hasBluetoothPermissions()) {
                Log.w(TAG, "Bluetooth permissions not granted; cannot print.")
                return false
            }

            val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                ?: run {
                    Log.w(TAG, "No paired Bluetooth ESC/POS printer found")
                    return false
                }

            // Common defaults: 203 dpi, 58mm paper => 48f characters per line
            val printer = EscPosPrinter(printerConnection, 203, 48f, 32)

            val text = StringBuilder().apply {
                append("[C]<b>KomBLE.mesh</b>\n")
                append("[C]------------------------------\n")
                append("[L]Item A[L]\n[R]2.50€\n")
                append("[L]Item B[L]\n[R]1.20€\n")
                append("[C]------------------------------\n")
                append("[R]<b>Total: 3.70€</b>\n")
                append("[C]\n")
                append("[C]Thank you!\n")
            }.toString()

            printer.printFormattedText(text)
            // Advance paper a few lines
            printer.printFormattedText("[C]\n[C]\n[C]\n")
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "Bluetooth print failed", t)
            return false
        }
    }

    /**
     * Prints a simple receipt over TCP to an ESC/POS printer.
     * Example: IP 192.168.0.90, port 9100 (JetDirect).
     */
    fun printSampleTcp(host: String, port: Int = 9100): Boolean {
        return try {
            val connection = TcpConnection(host, port)
            val printer = EscPosPrinter(connection, 203, 48f, 32)
            val text = StringBuilder().apply {
                append("[C]<b>KomBLE.mesh</b>\n")
                append("[C]TCP ESC/POS sample\n")
                append("[C]------------------------------\n")
                append("[L]Item X[L]\n[R]10.00€\n")
                append("[C]------------------------------\n")
                append("[R]<b>Total: 10.00€</b>\n")
                append("[C]\n")
            }.toString()
            printer.printFormattedText(text)
            // Advance paper a few lines
            printer.printFormattedText("[C]\n[C]\n[C]\n")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "TCP print failed", t)
            false
        }
    }
}