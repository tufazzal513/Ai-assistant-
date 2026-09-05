package com.example

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.ContactsContract
import org.json.JSONObject

class DeviceActions(private val context: Context) {
    fun openWhatsApp(): JSONObject {
        val intent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            JSONObject().put("success", true).put("action", "openWhatsApp")
        } else {
            JSONObject().put("success", false).put("error", "WhatsApp is not installed")
        }
    }

    fun openApp(appName: String): JSONObject {
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(0)
        var targetPackage: String? = null
        for (pkg in packages) {
            val name = pm.getApplicationLabel(pkg).toString()
            if (name.equals(appName, ignoreCase = true)) {
                targetPackage = pkg.packageName
                break
            }
        }
        
        if (targetPackage != null) {
            val intent = pm.getLaunchIntentForPackage(targetPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return JSONObject().put("success", true).put("action", "openApp").put("appName", appName)
            }
        }
        return JSONObject().put("success", false).put("error", "App '\$appName' not found or cannot be launched.")
    }

    fun makeCall(phoneNumber: String): JSONObject {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:\$phoneNumber")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return JSONObject().put("success", true).put("action", "makeCall")
    }

    fun callContact(contactName: String): JSONObject {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "\${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%\$contactName%")
        
        var matchesCount = 0
        var exactNumber: String? = null
        var matchNames = ""
        
        try {
            val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use {
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                
                while (it.moveToNext()) {
                    val name = it.getString(nameIndex)
                    val number = it.getString(numberIndex)
                    matchNames += "\$name, "
                    exactNumber = number
                    matchesCount++
                }
            }
        } catch (e: Exception) {
            return JSONObject().put("success", false).put("error", "Permission denied or contacts unavailable.")
        }
        
        return if (matchesCount == 1) {
            makeCall(exactNumber!!)
        } else if (matchesCount > 1) {
            JSONObject().put("success", false).put("error", "Multiple matching contacts found: \$matchNames")
        } else {
            JSONObject().put("success", false).put("error", "Contact not found.")
        }
    }

    fun toggleFlashlight(enable: Boolean): JSONObject {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, enable)
            JSONObject().put("success", true).put("action", "toggleFlashlight").put("state", if(enable) "on" else "off")
        } catch (e: Exception) {
            JSONObject().put("success", false).put("error", e.message)
        }
    }

    fun searchYouTube(query: String): JSONObject {
        val intent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage("com.google.android.youtube")
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            JSONObject().put("success", true).put("action", "searchYouTube")
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=\$query"))
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(webIntent)
            JSONObject().put("success", true).put("action", "searchYouTube_browser")
        }
    }
}
