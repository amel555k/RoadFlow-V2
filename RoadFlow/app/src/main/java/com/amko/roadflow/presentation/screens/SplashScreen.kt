package com.amko.roadflow.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amko.roadflow.domain.model.Canton
import com.amko.roadflow.presentation.components.AppDropdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
@Composable
fun SplashScreen(
    cantonList: List<Pair<Canton, String>>,
    cityToCanton: List<Pair<String, Canton>>,
    onSave: (Canton, String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current

    androidx.compose.runtime.DisposableEffect(Unit) {
        val window = (context as android.app.Activity).window
        val originalStatusBarColor = window.statusBarColor
        val originalLightStatusBars = androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars

        window.statusBarColor = 0xFF0E1A2B.toInt()
        androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false

        onDispose {
            window.statusBarColor = originalStatusBarColor
            androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = originalLightStatusBars
        }
    }

    var selectedCanton by remember { mutableStateOf<Canton?>(null) }
    var selectedCity by remember { mutableStateOf<String?>(null) }
    var isCantonDropdownOpen by remember { mutableStateOf(false) }
    var isCityDropdownOpen by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var termsAccepted by remember { mutableStateOf(false) }

    val cantonShakeOffset = remember { Animatable(0f) }
    val cityShakeOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun Animatable<Float, *>.shake() {
        val keyframes = listOf(0f, -20f, 20f, -14f, 14f, -8f, 8f, 0f)
        for (target in keyframes) {
            animateTo(target, animationSpec = tween(durationMillis = 40))
        }
    }

    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(0f) }
    val formAlpha = remember { Animatable(0f) }
    val formOffsetY = remember { Animatable(80f) }
    val density = LocalDensity.current
    var cantonRowWidth by remember { mutableStateOf(0.dp) }
    var cityRowWidth by remember { mutableStateOf(0.dp) }

    val fieldBackground = Color(0xFF0E1A2B)
    val fieldText = Color(0xFFE4ECF5)
    val fieldTextMuted = Color(0xFFE4ECF5).copy(alpha = 0.5f)
    val fieldArrow = Color(0xFFE4ECF5).copy(alpha = 0.6f)
    val buttonBackground = Color(0xFF0E1A2B)
    val buttonText = Color(0xFFFFFFFF)

    val cityList = remember(selectedCanton) {
        cityToCanton
            .filter { it.second == selectedCanton }
            .map { it.first }
            .distinct()
            .sorted()
    }

    LaunchedEffect(Unit) {
        titleAlpha.animateTo(1f, animationSpec = tween(durationMillis = 600))
        delay(500)
        titleOffsetY.animateTo(-750f, animationSpec = tween(durationMillis = 700, easing = EaseOutCubic))
        showTermsDialog = true
    }

    LaunchedEffect(termsAccepted) {
        if (termsAccepted) {
            launch {
                formAlpha.animateTo(1f, animationSpec = tween(durationMillis = 600))
            }
            formOffsetY.animateTo(0f, animationSpec = tween(durationMillis = 700, easing = EaseOutCubic))
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.amko.roadflow.R.drawable.splash_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )

        Text(
            text = "RoadFlow",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = titleAlpha.value
                    translationY = titleOffsetY.value
                }
        )

        if (isCantonDropdownOpen || isCityDropdownOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        isCantonDropdownOpen = false
                        isCityDropdownOpen = false
                    }
            )
        }

        if (termsAccepted) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(24.dp)
                    .graphicsLayer {
                        alpha = formAlpha.value
                        translationY = formOffsetY.value
                    }
            ) {
                Text(
                    text = "Odaberite vaš kanton",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = cantonShakeOffset.value }
                ) {
                    AppDropdown(
                        options = cantonList,
                        selectedLabel = cantonList.firstOrNull { it.first == selectedCanton }?.second ?: "",
                        selectedValue = selectedCanton,
                        placeholder = "Odaberite kanton",
                        expanded = isCantonDropdownOpen,
                        onExpandedChange = { opening ->
                            isCantonDropdownOpen = opening
                            if (opening) isCityDropdownOpen = false
                        },
                        onOptionSelected = { canton ->
                            if (canton != selectedCanton) {
                                selectedCity = null
                            }
                            selectedCanton = canton
                            isCantonDropdownOpen = false
                        },
                        fieldBackground = fieldBackground,
                        fieldText = fieldText,
                        fieldTextMuted = fieldTextMuted,
                        fieldArrow = fieldArrow
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Odaberite vaš grad",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { translationX = cityShakeOffset.value }
                ) {
                    AppDropdown(
                        options = cityList.map { it to it },
                        selectedLabel = selectedCity ?: "",
                        selectedValue = selectedCity,
                        placeholder = "Odaberite grad",
                        expanded = isCityDropdownOpen,
                        onExpandedChange = { opening ->
                            if (selectedCanton == null) {
                                coroutineScope.launch { cantonShakeOffset.shake() }
                            } else {
                                isCityDropdownOpen = opening
                                if (opening) isCantonDropdownOpen = false
                            }
                        },
                        onOptionSelected = { city ->
                            selectedCity = city
                            isCityDropdownOpen = false
                        },
                        fieldBackground = fieldBackground,
                        fieldText = fieldText,
                        fieldTextMuted = fieldTextMuted,
                        fieldArrow = fieldArrow
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = selectedCanton != null && selectedCity != null,
                    enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 300)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            val canton = selectedCanton
                            val city = selectedCity

                            if (canton != null && city != null) {
                                onSave(canton, city)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = buttonBackground),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .graphicsLayer {
                                alpha = formAlpha.value
                                translationY = -formOffsetY.value
                            }
                    ) {
                        Text(
                            text = "SAČUVAJ",
                            color = buttonText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = formAlpha.value
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "© 2026 Amel Kolasević sva prava zadržana | ",
                        fontSize = 11.sp,
                        color = Color(0xFFE4ECF5).copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Uslovi korištenja",
                        fontSize = 11.sp,
                        color = Color(0xFF7FB3FF),
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            showTermsDialog = true
                        }
                    )
                }
            }
        }

        if (showTermsDialog) {
            TermsDialog(
                readOnly = termsAccepted,
                onAccept = {
                    showTermsDialog = false
                    if (!termsAccepted) {
                        termsAccepted = true
                    }
                },
                onClose = {
                    showTermsDialog = false
                }
            )
        }
    }
}

@Composable
fun TermsDialog(
    readOnly: Boolean = false,
    onAccept: () -> Unit,
    onClose: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var hasScrolledToEnd by remember { mutableStateOf(readOnly) }

    LaunchedEffect(scrollState.value, scrollState.maxValue) {
        if (scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue - 4) {
            hasScrolledToEnd = true
        }
    }

    Dialog(
        onDismissRequest = { if (readOnly) onClose() },
        properties = DialogProperties(
            dismissOnBackPress = readOnly,
            dismissOnClickOutside = readOnly,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0E1A2B)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = "Uslovi korištenja aplikacije RoadFlow",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(20.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(start = 20.dp, end = 24.dp)
                    ) {
                        Text(
                            text = "Datum stupanja na snagu: 01.08.2026\nVerzija: 1.0",
                            fontSize = 12.sp,
                            color = Color(0xFFE4ECF5).copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        TermsSection(
                            title = "1. OPŠTE ODREDBE",
                            body = "RoadFlow (\"Aplikacija\") razvija Amel Kolasević . Instaliranjem, pristupom ili korištenjem Aplikacije prihvatate ove Uslove korištenja u cijelosti. Ako se ne slažete s ovim uslovima, nemojte koristiti Aplikaciju."
                        )
                        TermsSection(
                            title = "2. OPIS USLUGE",
                            body = "RoadFlow je informativna aplikacija namijenjena vozačima u Bosni i Hercegovini. Aplikacija prikazuje javno dostupne informacije o rasporedu mobilnih i stacionarnih radar sistema, prikazuje ih na mapi i može upozoriti korisnika prilikom vožnje kada se približava aktivnoj zoni radara.\n\nRoadFlow nije službena aplikacija državnih organa niti garantuje potpunu tačnost, ažurnost ili dostupnost prikazanih podataka."
                        )
                        TermsSection(
                            title = "3. DOZVOLE I PODACI KOJI SE KORISTE",
                            body = "Za pravilno funkcionisanje, Aplikacija može tražiti sljedeće dozvole:\n\n• Lokacija (precizna i približna) — za prikaz vaše pozicije na mapi, praćenje kretanja tokom vožnje i obavještavanje o blizini aktivnih radara.\n• Lokacija u pozadini — za rad upozorenja dok je aplikacija minimizirana ili ekran isključen, ukoliko korisnik to omogući.\n• Internet — za preuzimanje podataka o radarima, učitavanje mape i sinhronizaciju koordinata.\n• Obavještenja — za slanje upozorenja o radarima i prikaz statusa praćenja.\n• Vibracija — za haptičke signale prilikom upozorenja.\n\nPodaci o lokaciji obrađuju se na uređaju u svrhu funkcionalnosti aplikacije. RoadFlow ne prodaje vaše podatke trećim stranama."
                        )
                        TermsSection(
                            title = "4. ODGOVORNOST KORISNIKA",
                            body = "Korištenjem Aplikacije potvrđujete da:\n\n• Imate važeću vozačku dozvolu i vozite u skladu sa važećim saobraćajnim propisima.\n• Nećete koristiti Aplikaciju na način koji odvlači pažnju tokom vožnje.\n• Razumijete da su informacije o radarima informativnog karaktera i da je konačna odgovornost za poštivanje propisa isključivo na vama kao vozaču.\n• Provjeravate informacije i ne oslanjate se isključivo na Aplikaciju u kritičnim situacijama."
                        )
                        TermsSection(
                            title = "5. OGRANIČENJE ODGOVORNOSTI",
                            body = "Aplikacija se pruža \"takva kakva jeste\" (as is), bez garancija bilo koje vrste. Razvojni tim ne snosi odgovornost za:\n\n• Kašnjenje, greške ili nepotpunost podataka o radarima.\n• Prekide u radu usljed nedostatka internetske veze ili tehničkih problema.\n• Bilo kakvu štetu nastalu korištenjem ili nemogućnošću korištenja Aplikacije, uključujući saobraćajne prekršaje, kazne ili nesreće."
                        )
                        TermsSection(
                            title = "6. INTELEKTUALNO VLASNIŠTVO",
                            body = "Sav sadržaj, dizajn, kod i vizuelni identitet Aplikacije zaštićeni su autorskim pravima. Zabranjeno je kopiranje, modificiranje, distribucija ili komercijalno korištenje bez prethodne pisane saglasnosti vlasnika."
                        )
                        TermsSection(
                            title = "7. PROMJENE USLOVA",
                            body = "Razvojni tim zadržava pravo izmjene ovih Uslova korištenja u bilo kom trenutku. Nastavkom korištenja Aplikacije nakon objave izmjena smatra se da ste prihvatili nove uslove."
                        )
                        TermsSection(
                            title = "8. PREKID KORIŠTENJA",
                            body = "Možete prestati koristiti Aplikaciju u bilo kom trenutku deinstaliranjem sa uređaja. Razvojni tim zadržava pravo suspendovati ili ukinuti pristup Aplikaciji ukoliko se utvrdi zloupotreba."
                        )
                        TermsSection(
                            title = "9. PRIMJENJIVO PRAVO",
                            body = "Ovi Uslovi regulisani su zakonima Bosne i Hercegovine. Za sve sporove nadležan je sud prema sjedištu razvojnog tima, osim ako važeći propisi ne nalažu drugačije."
                        )
                        ContactSectionWithLink(
                            email = "amel.kolasevic@gmail.com",
                            privacyPolicyUrl = "https://sites.google.com/view/roadflow-privacy-policy"
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "© 2026 Amel Kolasević. Sva prava zadržana.",
                            fontSize = 12.sp,
                            color = Color(0xFFE4ECF5).copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    if (scrollState.maxValue > 0) {
                        val viewportHeight = scrollState.viewportSize.toFloat()
                        val contentHeight = viewportHeight + scrollState.maxValue.toFloat()
                        val thumbHeightFraction = (viewportHeight / contentHeight).coerceIn(0.05f, 1f)
                        val scrollFraction = scrollState.value.toFloat() / scrollState.maxValue.toFloat()

                        BoxWithConstraints(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp)
                                .width(4.dp)
                        ) {
                            val trackHeight = maxHeight
                            val thumbHeight = trackHeight * thumbHeightFraction
                            val thumbOffset = (trackHeight - thumbHeight) * scrollFraction

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(4.dp)
                                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .offset(y = thumbOffset)
                                    .height(thumbHeight)
                                    .width(4.dp)
                                    .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }

                if (readOnly) {
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "ZATVORI",
                            color = Color(0xFF0E1A2B),
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onAccept,
                        enabled = hasScrolledToEnd,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "POTVRDI",
                            color = if (hasScrolledToEnd) Color(0xFF0E1A2B) else Color(0xFF0E1A2B).copy(alpha = 0.4f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TermsSection(title: String, body: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = body,
        fontSize = 13.sp,
        color = Color(0xFFE4ECF5).copy(alpha = 0.85f)
    )
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun ContactSectionWithLink(email: String, privacyPolicyUrl: String) {
    val uriHandler = LocalUriHandler.current

    Text(
        text = "10. KONTAKT",
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Za pitanja u vezi s Uslovima korištenja:\nE-mail: $email",
        fontSize = 13.sp,
        color = Color(0xFFE4ECF5).copy(alpha = 0.85f)
    )
    Spacer(modifier = Modifier.height(8.dp))

    val annotatedText = buildAnnotatedString {
        append("Za detalje o obradi podataka pogledajte Politiku privatnosti dostupnu na: ")
        withStyle(
            style = SpanStyle(
                color = Color(0xFF7FB3FF),
                textDecoration = TextDecoration.Underline
            )
        ) {
            append(privacyPolicyUrl)
        }
    }

    Text(
        text = annotatedText,
        fontSize = 13.sp,
        color = Color(0xFFE4ECF5).copy(alpha = 0.85f),
        modifier = Modifier.clickable {
            uriHandler.openUri(privacyPolicyUrl)
        }
    )
    Spacer(modifier = Modifier.height(16.dp))
}