package win.app.vin.one.app


import android.content.Context
import android.content.SharedPreferences

class SharedPreference(context: Context) {
    val sharedPref: SharedPreferences = context.getSharedPreferences("kotlincodes", Context.MODE_PRIVATE)
    fun save(KEY_NAME: String, text: String) {
        val editor: SharedPreferences.Editor = sharedPref.edit()
        editor.putString(KEY_NAME, text)
        editor.apply()
    }
    fun getValueString(KEY_NAME: String): String? { return sharedPref.getString(KEY_NAME, null) }
}