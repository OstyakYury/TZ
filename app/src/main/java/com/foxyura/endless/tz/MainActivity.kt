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
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.foxyura.endless.tz.ui.theme.TZTheme
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.ktx.remoteConfig
import kotlinx.coroutines.delay
import java.util.*
import kotlin.system.exitProcess
import com.google.accompanist.navigation.animation.composable

class MainActivity : ComponentActivity() { var backEnabled = mutableStateOf(false) // false - начальная страница, true - другие страницы.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPreference = SharedPreference(this)
        setContent {
            TZTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(255,204,204)
                ) {
                    Box(Modifier.fillMaxSize()){
                        Navigation(sharedPreference)
                    }
                }
            }
        }
    }


    @OptIn(ExperimentalAnimationApi::class)
    @Composable fun Navigation(sharedPreference: SharedPreference) { val navController = rememberAnimatedNavController()
        AnimatedNavHost(navController = navController, startDestination = "splash_screen",
            enterTransition = { slideInHorizontally(initialOffsetX = { 5000 }) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -5000 }) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -5000 }) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { 5000 }) }
        ) {
            composable("splash_screen") { SplashScreen(navController = navController,sharedPreference) } //Логотип
            composable("web") {ScreenWeb(sharedPreference.getValueString("url").toString()) } //Веб-ресурс
            composable("noInternet") { ScreenNotInternet() } //Нет интернета
            composable("zaglushka",

            ) { ScreenZaglushka() } //Заглушка при отсутствии ссылки && эмулятор
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
            var errorUpdate = true
            remoteConfig.fetchAndActivate().addOnCompleteListener(this) { task -> if (task.isSuccessful) { Log.d(TAG, "Обновлены параметры конфигурации: ${task.result}") } } // Обновление
            remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
                override fun onUpdate(configUpdate: ConfigUpdate) { Log.d(TAG, "Обновленные ключи: " + configUpdate.updatedKeys); }
                override fun onError(error: FirebaseRemoteConfigException) {
                    Log.w(TAG, "Ошибка обновления конфигурации с кодом: " + error.code, error)
                    errorUpdate = false
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
            Text("LOGO", fontSize = 24.sp)
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
            Text(textAlign = TextAlign.Center, text = "A network connection is\nrequired to continue.")
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
        backEnabled.value = true
        BackHandler(true){
            moveTaskToBack(true);
            exitProcess(-1)
        }
        //!- текст @ - Заголовок $ - Ссылка на картинку
        val list = listOf<String>(
            "!@How to lose weight by the summer in 30 days",
            "#image1",
            "!Prevention of excess weight or an active struggle with the existing extra pounds is an important guarantee of the well-being of human health and his confidence in his own external attractiveness. With the approach of the beach season, effective weight loss measures become especially relevant for many of us. Is it possible to quickly return your body to slimness and beauty? Absolutely; if you adhere to the main principle in achieving this goal - a combination of proper, balanced nutrition with intensive sports training. Disciplined adherence to the recommendations below will allow you to put your figure in order in record time - and meet the long-awaited summer, as they say, in full combat full armament!",
            "!@Getting rid of excess weight: preparatory measures",
            "#image2",
            "!So, you have firmly decided at all costs to win over the ill-fated extra pounds. In this case, the first thing you should do is to contact the clinic for medical advice, for possible contraindications to your planned diet and upcoming sports loads. The ideal option is to visit not only a general practitioner, but also a qualified nutritionist on the eve of weight loss events. The specialist will get acquainted with your eating habits, conduct a psychological diagnosis, find out whether excess weight is a consequence of stress, and give the necessary professional recommendations.\n" +
                    "\n" +
                    "Whichever diet you choose, take into account the fact that any restriction in food intake entails a reduction in the intake of vitamins and minerals. Therefore, during your visit to the doctor, ask a specialist for advice in choosing the most suitable vitamin complex for you.\n" +
                    "\n" +
                    "To determine your type of metabolism for the subsequent accounting of current energy costs and the formation of an optimal diet for the period of the diet, you will be helped by contacting specialized Internet services. As for the calculation of calories consumed and consumed by you during each day, it can also be made in the future using the appropriate online calculator.\n" +
                    "\n" +
                    "So that the diet for weight loss does not seem boring and monotonous to you, develop an approximate menu for each day, forming a diet based on a wide range of healthy foods with low calorie content.\n" +
                    "\n" +
                    "And, finally, make a detailed plan of sports activities for yourself, and tune in to strict and unconditional adherence to this schedule. The combination of a balanced diet with active training is the most important condition for someone who is aimed at truly high results in the fight against extra pounds.",
            "!@Diet for weight loss: what should be excluded from the menu?",
            "#image3",
            "!To provide yourself with a healthy and healthy diet for weight loss, you will need, first of all, to refrain from eating a number of products. These are:\n" +
                    "- fried and fatty foods;\n" +
                    "- fast food, semi-finished products;\n" +
                    "- marinades, pickles, smoked meats;\n" +
                    "- hot sauces and mayonnaise;\n" +
                    "- alcohol, coffee;\n" +
                    "- packaged juices and sweet carbonated drinks;\n" +
                    "- white bread, muffins, confectionery;\n" +
                    "- foods and dishes containing a large number of calories.\n" +
                    "For the period of the diet, try to limit the consumption of potatoes, legumes, grapes, bananas and dried fruits as much as possible. The presence of salt and sugar in the diet should be minimized (the maximum permissible daily rate, taking into account the content of these products in ready meals, is 5 g and 25 g, respectively).",
            "!@Balanced nutrition for weight loss: recommended products",
            "#image4",
            "!The formation of a diet in the fight against excess weight is usually carried out on the basis of the following components:\n" +
                    "\n" +
                    "dietary varieties of meat - 150-200 g daily;\n" +
                    "lean fish and seafood - 300-400 g per week, divided into several doses;\n" +
                    "fruits, greens and vegetables - in aggregate, 700-800 g per day;\n" +
                    "cereals (cereals, whole grain bread, pasta) - a maximum of 400 g daily;\n" +
                    "low-fat dairy products - 150 g (drinks - up to 0.5 l) per day.",
            "!@About breakfasts, lunches, dinners and snacks",
            "#image5",
            "!When planning a menu for the day, do not forget to take into account the peculiarities of food intake, depending on the time of day. So, systematic neglect of breakfast can serve as a prerequisite for further overeating - and, as a result, lead to weight gain. Healthy morning dishes, for example, a small portion of vegetable omelet or oatmeal with crushed fruits, plus a glass of low-fat milk, will fully provide you with energy until the onset of a lunch meal.\n" +
                    "\n" +
                    "Lunch meals are best made light. One of the classic lunch options is a portion of vegetarian soup, boiled chicken breast and vegetable salad, seasoned with lemon juice or a small amount of vegetable oil. As a drink, green tea, freshly squeezed juice, unsweetened compote or rosehip broth are optimally suited.\n" +
                    "\n" +
                    "Food for dinner should also include different food groups. The complex of useful evening dishes may consist, for example, of a portion of vegetable soup with seafood, cottage cheese casserole with greens and vegetables, and a glass of fermented baked milk or low-fat kefir.\n" +
                    "\n" +
                    "To prevent or satisfy the feeling of hunger between meals, it is not forbidden to resort to the so-called snacks. Here, yogurt, fruits, vegetables, almonds, pistachios or a few slices of dark chocolate are optimal. At the same time, the main thing is to observe moderation, and make sure that the total daily energy value of products for snacks does not exceed 150-200 kilocalories.",
            "!@Effective workouts for weight loss",
            "#image6",
            "!Ideal sports training for weight loss are, among others, running, fitness or dancing, swimming, cycling, tennis, football, rowing, rollerblading and horse riding. However, since at the moment you are aimed at losing weight as quickly as possible, we advise you to turn to fitness, if desired, using any other sport as an effective addition to it.\n" +
                    "\n" +
                    "At the first visit to the fitness club, the instructor will draw up an individual training plan for you, aimed directly at weight correction. If you prefer to exercise on your own, a 30-minute workout program for a week may look like this:\n" +
                    "\n" +
                    "1st day: running in place, push-ups from the floor, twisting, exercises with dumbbells.\n" +
                    "Day 2: squats, leg lifts, exercises with dumbbells.\n" +
                    "3rd day: running in place, squats, pull-ups.\n" +
                    "4th day: swimming pool, cycling or playing tennis.\n" +
                    "Day 5: Exercise \"bicycle\", running in place, pulling up and twisting.\n" +
                    "Days 6 and 7: Light fitness without strength training.",
            "!@Rapid weight normalization: general recommendations",
            "#image7",
            "!Any nutrition program aimed at losing weight involves the implementation of systematic control over the intake of calories that have a direct impact on weight dynamics. To achieve a decrease in body weight, it is necessary to stably create a moderate calorie deficit, that is, to consume them in smaller quantities than they are consumed by the body. At the very beginning of the diet, daily calorie intake is recommended to be reduced to 1500. Each subsequent week, the number of calories is reduced by about 200 units, and by the time the diet is completed, it is approximately 900 kcal per day.\n" +
                    "\n" +
                    "In order for the fight against extra pounds to please you with truly impressive results, try to adhere to a number of useful recommendations when achieving the goal:\n" +
                    "\n" +
                    "Do not try to lose weight as quickly as possible, exhausting yourself with starvation. Poor nutrition slows down the metabolism and prevents the full process of losing weight.\n" +
                    "When forming a diet, give preference to food enriched with protein and fiber. Eat at least five different vegetables every day.\n" +
                    "Start the morning with a glass of warm water. Be sure to drink a glass of water before going to bed and a quarter of an hour before each meal. In general, the daily volume of water you consume (in its pure form) should be at least 2-2.5 liters.\n" +
                    "Carefully observe fractional nutrition, and never skip meals. Calculate a single volume of foods you consume according to the principle of \"two palms\": eat about as much food as you would fit in closed palms.\n" +
                    "Have breakfast no later than two hours after waking up, and have dinner no later than three hours before bedtime.\n" +
                    "Immediately after the meal, walk for at least a quarter of an hour.\n" +
                    "Do not abuse alcoholic beverages: they are not only high in calories, but also significantly stimulate appetite. The maximum that you can afford, if there is an objective reason for this, is a couple of glasses of dry wine.\n" +
                    "Move more Start the morning with exercise. Even if you are a very busy person, if you wish, you can probably carve out five to seven minutes to perform a short set of simple exercises.\n" +
                    "Find a like-minded person who will play sports with you. This significantly increases motivation. Perform exercises for weight loss regularly, without delaying until tomorrow.\n" +
                    "If your health condition allows, visit the sauna twice a week. Such activities will be of great benefit to you in the fight against excess weight, since high temperature acts not only on the muscle, but also on the adipose tissue of the body.\n" +
                    "Do not forget about your new useful habits after you achieve your goal. Make them an integral part of your life – and you will not have to spend time and effort to return beauty to your body, and health to your body!",
        )
        val mapImage = mapOf(
            "image1" to painterResource(R.drawable.page1image1),
            "image2" to painterResource(R.drawable.page1image2),
            "image3" to painterResource(R.drawable.page1image3),
            "image4" to painterResource(R.drawable.page1image4),
            "image5" to painterResource(R.drawable.page1image5),
            "image6" to painterResource(R.drawable.page1image6),
            "image7" to painterResource(R.drawable.page1image7),
        )
        @Composable fun screanColumnInfo(list:List<String>,mapImage:Map<String,Painter>){
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(5.dp)){ items(list, itemContent = { string->
                if (string!=null) {
                    when (string[0]) {
                        '!' -> {
                            val text = if (string[1] == '@') string.substring(2) else string.substring(1)
                            val sizeText = if (string[1] == '@') 24.sp else 16.sp
                            val colorText = Color.Black
                            val styleText = FontFamily.Default
                            val alignText = if (string[1] == '@') TextAlign.Center else TextAlign.Left
                            Text(modifier = Modifier.fillMaxWidth(), textAlign = alignText, text = text, fontSize = sizeText, color = colorText, fontFamily = styleText
                            )
                        }
                        '#' -> {
                            if (mapImage.filter { it.key == string.substring(1) }.isNotEmpty()) {
                                mapImage.filter { it.key == string.substring(1) }.forEach { KeyImage ->
                                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                        Image(modifier = Modifier.border(BorderStroke(2.dp, Color(255, 153, 153)), RoundedCornerShape(25)).clip(RoundedCornerShape(25)), painter = KeyImage.value, contentDescription = "", contentScale = ContentScale.Fit)
                                    }
                                }
                            }
                        }
                    }
                }
            })
            }
        }
        screanColumnInfo(list,mapImage)
    } // Заглушка
}



