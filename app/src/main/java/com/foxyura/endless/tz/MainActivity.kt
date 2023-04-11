package com.foxyura.endless.tz

import android.annotation.SuppressLint
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
import android.webkit.CookieSyncManager

import android.webkit.WebView
import android.webkit.WebViewClient
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
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.ktx.remoteConfig
import kotlinx.coroutines.delay
import java.util.*


class MainActivity : ComponentActivity() { var backEnabled = mutableStateOf(false) // false - начальная страница, true - другие страницы.
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState)
        val sharedPreference = SharedPreference(this)
        setContent { Navigation(sharedPreference) }
    }
    @Composable fun Navigation(sharedPreference: SharedPreference) { val navController = rememberNavController()
        NavHost(navController = navController, startDestination = "splash_screen") {
            composable("splash_screen") { SplashScreen(navController = navController,sharedPreference) } //Логотип
            composable("web") { ScreenWeb(sharedPreference.getValueString("url").toString()) } //Веб-ресурс
            composable("noInternet") { ScreenNotInternet() } //Нет интернета
            composable("zaglushka") { ScreenZaglushka() } //Заглушка при отсутствии ссылки && эмулятор
            composable("update") {val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig  // Получение удаленного экземпляра конфигурации
                val navigation = if (remoteConfig.getString("url") != ""&&!checkIsEmu()) {
                    sharedPreference.save("url", remoteConfig.getString("url")) //Сохранение ссылки
                    "web" } else "zaglushka"
                LaunchedEffect(key1 = true) { navController.navigate(navigation) }
            } // Для обновления, или получения ссылки на веб- ресурс. Либо заглушка.
        }
    }
    fun logica(sharedPreference: SharedPreference):String{ val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig  // Получение удаленного экземпляра конфигурации
        if (sharedPreference.getValueString("url") != null) { /*Есть ссылка на устройстве?*/
            if (checkForInternet(this)) return "web" /*Интернет есть -> Открытие Веб- ресурса */ else return "noInternet"   /*Нет интернета*/
        } else {
            var errorUpdate = false
            remoteConfig.fetchAndActivate().addOnCompleteListener(this) { task -> if (task.isSuccessful) { Log.d(TAG, "Обновлены параметры конфигурации: ${task.result}") } } // Обновление
            remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
                override fun onUpdate(configUpdate: ConfigUpdate) { Log.d(TAG, "Обновленные ключи: " + configUpdate.updatedKeys); }
                override fun onError(error: FirebaseRemoteConfigException) {
                    Log.w(TAG, "Ошибка обновления конфигурации с кодом: " + error.code, error)
                    errorUpdate = true
                }
            }) // Обновление ключей
            return if (errorUpdate) "update" else {
                Log.d(TAG, "Не удалось получить новые параметры")
                "noInternet"
            } //Если не удалось обновить параметры
        }
    } // Часть логики. Остальная в Navigation  ->  composable("update")
    @Composable fun SplashScreen(navController: NavController, sharedPreference: SharedPreference) { val navigation = logica(sharedPreference)
        LaunchedEffect(key1 = true) { delay(3600L)
            navController.navigate(navigation)
        }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text("Добро пожаловать")
        }
    } //Экран Логотипа
    @SuppressLint("SetJavaScriptEnabled") @Composable fun ScreenWeb(MyURL: String) {
        CookieManager.getInstance().setAcceptCookie(true)
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
                    }
                }
                loadUrl(MyURL)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
            }
        }, update = { webView = it })
    } // Веб- ресурс
    @Composable fun ScreenNotInternet() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Нет доступа к интернету.")
        }
    } // Экран отсутствия интернета
    private fun checkIsEmu(): Boolean {
        if (BuildConfig.DEBUG) return false // когда разработчик использует эту сборку на эмуляторе
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
    } // Проверка: Эмулятор?
    fun checkForInternet(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION") val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION") return networkInfo.isConnected
        }
    } //Проверка: Есть ли интернет?
    fun chekUpdateURL(remoteConfig: FirebaseRemoteConfig) {
        remoteConfig.fetchAndActivate().addOnCompleteListener(this) { task -> if (task.isSuccessful) { Log.d(TAG, "Обновлены параметры конфигурации: ${task.result}") } } // Обновление
        remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) { Log.d(TAG, "Обновленные ключи: " + configUpdate.updatedKeys); }
            override fun onError(error: FirebaseRemoteConfigException) { Log.w(TAG, "Ошибка обновления конфигурации с кодом: " + error.code, error) }
        }) // Обновление ключей
    } //Получение новых параметров из сервера
    override fun onBackPressed() { if (backEnabled.value == true) { super.onBackPressed() } } //Не даёт выйти из приложения, находясь на начальной странице.
    @Composable fun ScreenZaglushka() {
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
    } // Заглушка
}



