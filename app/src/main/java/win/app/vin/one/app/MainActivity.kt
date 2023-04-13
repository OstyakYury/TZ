package win.app.vin.one.app

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.*
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.google.accompanist.permissions.*
import com.google.firebase.ktx.BuildConfig
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.ktx.remoteConfig
import kotlinx.coroutines.delay
import win.app.vin.one.app.ui.theme.TZTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.Provider
import java.util.*
import kotlin.system.exitProcess


class MainActivity : ComponentActivity() { var backEnabled = mutableStateOf(false) // false - начальная страница, true - другие страницы.
    private var uploadMessage: ValueCallback<Uri>? = null
    private var uploadMessageAboveL: ValueCallback<Array<Uri>>? = null

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

            composable("web") {ScreenWeb(sharedPreference.getValueString("url").toString())
                } //Веб-ресурс
            composable("noInternet") { ScreenNotInternet() } //Нет интернета
            composable("zaglushka") { ScreenZaglushka() } //Заглушка при отсутствии ссылки && эмулятор
            composable("update") {


                val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig  // Получение удаленного экземпляра конфигурации
                val navigation = if (remoteConfig.getString("url") != ""&&!checkIsEmu()) {
                    sharedPreference.save("url", remoteConfig.getString("url")) //Сохранение ссылки
                    "web" } else "zaglushka"
                LaunchedEffect(key1 = true) { navController.navigate(navigation) }
            } // Для обновления, или получения ссылки на веб- ресурс. Либо заглушка.
        }
    }
    private fun requestPerms() {
        val perm = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this@MainActivity, perm, 123)
        }
    }

    fun logica(sharedPreference: SharedPreference):String{ val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig  // Получение удаленного экземпляра конфигурации
        if (sharedPreference.getValueString("url") != null) { /*Есть ссылка на устройстве?*/
            if (checkForInternet(this)) {Log.w(TAG, "Есть интернет")
                return "web"
            } /*Интернет есть -> Открытие Веб- ресурса */ else {Log.w(TAG, "Нет интернета")
                return "noInternet"
            }   /*Нет интернета*/
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
            Image(modifier = Modifier.border(BorderStroke(2.dp, Color(255, 153, 153)), RoundedCornerShape(25)).clip(RoundedCornerShape(25)), painter = painterResource(R.drawable.logo), contentDescription = "", contentScale = ContentScale.Fit)

        }
    } //Экран Логотипа
    @OptIn(ExperimentalComposeUiApi::class, ExperimentalPermissionsApi::class)
    @SuppressLint("SetJavaScriptEnabled", "NewApi") @Composable fun ScreenWeb(MyURL: String) {
        val cameraPermissionState = rememberPermissionState(android.Manifest.permission.CAMERA)
        val storagePermissionState = rememberPermissionState(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        LaunchedEffect(key1 = true) {
            if (storagePermissionState.status.isGranted) {
            } else {requestPerms() }
            if (cameraPermissionState.status.isGranted) {
            } else { requestPerms() }
        }
        val loaderDialogScreen = remember { mutableStateOf(false) }
        CookieManager.getInstance().setAcceptCookie(true)
        var webView: WebView? = null

        BackHandler(enabled = backEnabled.value) { webView?.goBack() }
        AndroidView(factory = {
            WebView(it).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                settings.allowFileAccess = true
                settings.loadsImagesAutomatically = true

                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true


                settings.databaseEnabled = true
                settings.useWideViewPort = true
                settings.javaScriptCanOpenWindowsAutomatically = true
                settings.mediaPlaybackRequiresUserGesture = true
                settings.pluginState = WebSettings.PluginState.ON

                settings.allowContentAccess = true

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ) /*Полноэкраннный режим*/
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        backEnabled.value = view.canGoBack()
                        super.onPageStarted(view, url, favicon)
                    }
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // hide dialog
                        loaderDialogScreen.value = false
                    }

                }
                webChromeClient = object : WebChromeClient() {
                    override fun onPermissionRequestCanceled(request: PermissionRequest?) { super.onPermissionRequestCanceled(request) }

                    override fun onPermissionRequest(request: PermissionRequest) { request.grant(request.resources) }
                    override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: WebChromeClient.FileChooserParams): Boolean {
                        uploadMessageAboveL = filePathCallback
                        openImageChooserActivity()
                        return true
                    }
                }

                setDownloadListener  { url, _, _, _, _ ->
                    Log.d(TAG, "Ссылка : ${url}")
                    if (url.startsWith("data:")) {  //when url is base64 encoded data
                        createAndSaveFileFromBase64Url(url)
                        return@setDownloadListener
                    }
                    url?.let {
                        try {
                            download(url)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error opening link", Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                }
                loadUrl(MyURL)
            }
        }, update = { webView = it })
    } // Веб- ресурс
    //Скачивание файлов и base64Url
    @SuppressLint("UnspecifiedImmutableFlag")
    fun createAndSaveFileFromBase64Url(url: String): String {



        val path: File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val filetype = url.substring(url.indexOf("/") + 1, url.indexOf(";"))
        val filename = System.currentTimeMillis().toString() + "." + filetype
        val file = File(path, filename)
        try {
            if (!path.exists()) path.mkdirs()
            if (!file.exists()) file.createNewFile()
            val base64EncodedString = url.substring(url.indexOf(",") + 1)
            val decodedBytes: ByteArray = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Base64.getDecoder().decode(base64EncodedString)
            } else {
                TODO("VERSION.SDK_INT < O")
            }
            val os: OutputStream = FileOutputStream(file)
            os.write(decodedBytes)
            os.close()

            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            val mimetype = url.substring(url.indexOf(":") + 1, url.indexOf("/"))

            //Сообщите сканеру мультимедиа о новом файле, чтобы он был немедленно доступен пользователю.
            MediaScannerConnection.scanFile(this, arrayOf<String>(file.toString()), null,
                object : MediaScannerConnection.OnScanCompletedListener {
                    override fun onScanCompleted(path: String, uri: Uri) {
                        Log.i("ExternalStorage", "Scanned $path:")
                        Log.i("ExternalStorage", "-> uri=$uri")
                        Log.i("ExternalStorage", "-> intent=${intent.setDataAndType(uri,"$mimetype")}")
                        intent.setData(uri)
                    }
                })
            intent.setType("$mimetype/*")


//            intent.setDataAndType(FileProvider.getUriForFile(this@MainActivity,   "provider", file), "$mimetype/*")

            //Set notification after download complete and add "click to view" action to that
            val flags = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE
                else -> FLAG_UPDATE_CURRENT
            }
            val mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val mChannel = NotificationChannel("my_channel_01", "Chanel", NotificationManager.IMPORTANCE_LOW)
            mChannel.description = "Descript chanel"
            mChannel.enableLights(true)
            mChannel.enableVibration(false)
            mChannel.vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
            mNotificationManager.createNotificationChannel(mChannel)

            val pIntent = PendingIntent.getActivity(this@MainActivity, 0, intent, flags)
            val notification: Notification =  NotificationCompat.Builder(applicationContext,
                applicationContext.getString(R.string.app_name))
                .setSmallIcon(R.drawable.logo)
                .setContentText("File download")
                .setContentTitle(filename)
                .setContentIntent(pIntent)
                .setChannelId("my_channel_01")
                .build()
            notification.flags = notification.flags or Notification.FLAG_AUTO_CANCEL

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(85851, notification)

        } catch (e: IOException) {
            Log.w("ExternalStorage", "Error writing file", e)
            Toast.makeText(applicationContext, "Error download", Toast.LENGTH_LONG).show()
        }
        return file.toString()
    }
    fun download(imageUrl:String){
        // Initialize download request
        val request = DownloadManager.Request(Uri.parse(imageUrl))
         // Установите описание запроса на загрузку
        request.setDescription("Downloading requested image....")
        // Разрешить сканирование
        request.allowScanningByMediaScanner()
        // Настройка уведомления о запросе загрузки
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        // Угадайте имя файла
        val fileName = URLUtil.guessFileName(imageUrl, null, null)
        // Установите целевое хранилище для загруженного файла
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

        // Задать заголовок запроса
        request.setTitle("Image Download : $fileName")
        // Получите услугу загрузки системы
        val dManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        // Наконец, запросите загрузку в службу загрузки системы
        dManager.enqueue(request)
    }
    // Выбор файлов
    private fun openImageChooserActivity() {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        startActivityForResult(Intent.createChooser(i, "Image Chooser"), FILE_CHOOSER_RESULT_CODE)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
//        super.onActivityResult(requestCode, resultCode, data)
//        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
//            if (null == uploadMessage && null == uploadMessageAboveL) return
//            val result = if (data == null || resultCode != Activity.RESULT_OK) null else data.data
//            if (uploadMessageAboveL != null) {
//                onActivityResultAboveL(requestCode, resultCode, data)
//            } else if (uploadMessage != null) {
//                uploadMessage!!.onReceiveValue(result)
//                uploadMessage = null
//            }
//        }
    }
    private fun onActivityResultAboveL(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (requestCode != FILE_CHOOSER_RESULT_CODE || uploadMessageAboveL == null)
            return
        var results: Array<Uri>? = null
        if (resultCode == Activity.RESULT_OK) {
            if (intent != null) {
                val dataString = intent.dataString
                val clipData = intent.clipData
                if (clipData != null) {
                    results = Array(clipData.itemCount){
                            i -> clipData.getItemAt(i).uri
                    }
                }
                if (dataString != null)
                    results = arrayOf(Uri.parse(dataString))
            }
        }
        uploadMessageAboveL!!.onReceiveValue(results)
        uploadMessageAboveL = null
    }
    companion object { private val FILE_CHOOSER_RESULT_CODE = 10000 }
    //------------
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




