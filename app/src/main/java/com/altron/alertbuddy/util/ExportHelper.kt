package com.altron.alertbuddy.util


import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import com.altron.alertbuddy.data.Message
import com.altron.alertbuddy.data.Severity
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * ============================================================================
 * ExportHelper.kt - Alert Export Utilities
 * ============================================================================
 *
 * PURPOSE:
 * Provides functionality to export alerts to CSV and HTML formats
 * for reporting and audit purposes.
 *
 * FORMATS SUPPORTED:
 * - CSV: Comma-separated values for spreadsheet import
 * - HTML: Formatted report that can be printed as PDF
 *
 * ============================================================================
 */
object ExportHelper {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val fileNameDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * Export alerts to CSV format and share via intent.
     */
    fun exportToCsv(
        context: Context,
        alerts: List<Message>,
        title: String = "Alert Export"
    ): Boolean {
        try {
            val timestamp = fileNameDateFormat.format(Date())
            val fileName = "alert_export_$timestamp.csv"
            val file = createFile(context, fileName)

            PrintWriter(FileOutputStream(file)).use { writer ->
                // Header row
                writer.println("ID,Channel,Title,Message,Severity,Timestamp,Acknowledged,Acknowledged At")

                // Data rows
                alerts.forEach { alert ->
                    writer.println(
                        "${escapeCsv(alert.id)}," +
                                "${escapeCsv(alert.channelName)}," +
                                "${escapeCsv(alert.title)}," +
                                "${escapeCsv(alert.message)}," +
                                "${alert.severity.name}," +
                                "${dateFormat.format(Date(alert.timestamp))}," +
                                "${if (alert.isRead) "Yes" else "No"}," +
                                "${alert.acknowledgedAt?.let { dateFormat.format(Date(it)) } ?: "N/A"}"
                    )
                }
            }

            shareFile(context, file, "text/csv", "Export Alerts (CSV)")
            return true
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to export: ${e.message}", Toast.LENGTH_LONG).show()
            return false
        }
    }

    /**
     * Export alerts to HTML report format (can be printed as PDF).
     */
    fun exportToHtml(
        context: Context,
        alerts: List<Message>,
        title: String = "Alert Report"
    ): Boolean {
        try {
            val timestamp = fileNameDateFormat.format(Date())
            val fileName = "alert_report_$timestamp.html"
            val file = createFile(context, fileName)

            PrintWriter(FileOutputStream(file)).use { writer ->
                writer.println(generateHtmlReport(alerts, title))
            }

            shareFile(context, file, "text/html", "Export Alerts (Report)")
            return true
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to export: ${e.message}", Toast.LENGTH_LONG).show()
            return false
        }
    }

    private fun createFile(context: Context, fileName: String): File {
        val exportDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        return File(exportDir, fileName)
    }

    private fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, title))
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private fun generateHtmlReport(alerts: List<Message>, title: String): String {
        val generatedAt = dateFormat.format(Date())
        val criticalCount = alerts.count { it.severity == Severity.CRITICAL }
        val warningCount = alerts.count { it.severity == Severity.WARNING }
        val infoCount = alerts.count { it.severity == Severity.INFO }
        val acknowledgedCount = alerts.count { it.isRead }

        val alertRows = alerts.sortedByDescending { it.timestamp }.joinToString("\n") { alert ->
            val severityColor = when (alert.severity) {
                Severity.CRITICAL -> "#DC3545"
                Severity.WARNING -> "#FD7E14"
                Severity.INFO -> "#0D6EFD"
            }

            """
            <tr>
                <td><span class="severity" style="background-color: $severityColor">${alert.severity.name}</span></td>
                <td>${escapeHtml(alert.channelName)}</td>
                <td><strong>${escapeHtml(alert.title)}</strong><br><small>${escapeHtml(alert.message)}</small></td>
                <td>${dateFormat.format(Date(alert.timestamp))}</td>
                <td>${if (alert.isRead) "<span class='badge-success'>Acknowledged</span>" else "<span class='badge-warning'>Pending</span>"}</td>
            </tr>
            """.trimIndent()
        }

        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>$title - Alert Buddy</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; padding: 20px; color: #333; }
        .header { text-align: center; margin-bottom: 30px; border-bottom: 2px solid #4F46E5; padding-bottom: 20px; }
        .header h1 { color: #4F46E5; font-size: 24px; }
        .header .subtitle { color: #666; margin-top: 5px; }
        .summary { display: flex; justify-content: space-around; margin-bottom: 30px; flex-wrap: wrap; }
        .summary-card { background: #f8f9fa; border-radius: 8px; padding: 15px 25px; text-align: center; min-width: 120px; margin: 5px; }
        .summary-card .value { font-size: 28px; font-weight: bold; color: #4F46E5; }
        .summary-card .label { font-size: 12px; color: #666; text-transform: uppercase; }
        table { width: 100%; border-collapse: collapse; margin-top: 20px; }
        th { background: #4F46E5; color: white; padding: 12px 15px; text-align: left; font-size: 14px; }
        td { padding: 12px 15px; border-bottom: 1px solid #eee; font-size: 13px; vertical-align: top; }
        tr:hover { background: #f8f9fa; }
        .severity { color: white; padding: 3px 8px; border-radius: 4px; font-size: 11px; font-weight: bold; }
        .badge-success { background: #198754; color: white; padding: 3px 8px; border-radius: 4px; font-size: 11px; }
        .badge-warning { background: #DC3545; color: white; padding: 3px 8px; border-radius: 4px; font-size: 11px; }
        .footer { text-align: center; margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; color: #999; font-size: 12px; }
        @media print { 
            body { padding: 10px; } 
            .summary-card { page-break-inside: avoid; }
            tr { page-break-inside: avoid; }
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>Alert Buddy Report</h1>
        <p class="subtitle">$title - Generated on $generatedAt</p>
    </div>
    
    <div class="summary">
        <div class="summary-card">
            <div class="value">${alerts.size}</div>
            <div class="label">Total Alerts</div>
        </div>
        <div class="summary-card">
            <div class="value" style="color: #DC3545">$criticalCount</div>
            <div class="label">Critical</div>
        </div>
        <div class="summary-card">
            <div class="value" style="color: #FD7E14">$warningCount</div>
            <div class="label">Warning</div>
        </div>
        <div class="summary-card">
            <div class="value" style="color: #0D6EFD">$infoCount</div>
            <div class="label">Info</div>
        </div>
        <div class="summary-card">
            <div class="value" style="color: #198754">$acknowledgedCount</div>
            <div class="label">Acknowledged</div>
        </div>
    </div>
    
    <table>
        <thead>
            <tr>
                <th style="width: 100px">Severity</th>
                <th style="width: 150px">Channel</th>
                <th>Alert Details</th>
                <th style="width: 160px">Timestamp</th>
                <th style="width: 120px">Status</th>
            </tr>
        </thead>
        <tbody>
            $alertRows
        </tbody>
    </table>
    
    <div class="footer">
        <p>Alert Buddy - Altron Digital Infrastructure Alerting</p>
        <p>This report contains ${alerts.size} alerts. For compliance and audit purposes only.</p>
    </div>
</body>
</html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
