package com.example.minimalistnotebook.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.minimalistnotebook.R
import com.example.minimalistnotebook.data.local.WordEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    wordList: List<WordEntity>,
    onNavigateToAdd: () -> Unit,
    onNavigateToList: () -> Unit,
    onNavigateToReview: () -> Unit = {}
) {
    var currentRealTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            currentRealTime = System.currentTimeMillis()
        }
    }

    val totalWords = wordList.size
    val toReviewCount = wordList.count { it.level < 9 && it.nextReviewTime <= currentRealTime }

    val premiumGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFF6ED), Color(0xFFEBE0D3))
    )
    val looseLeafColor = Color(0xFFF9F6F0)
    val deepBurgundy = Color(0xFF3C120A)
    val warmOrange = Color(0xFFD65A31)
    val deepText = Color(0xFF2C1E16)

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val centerOffset = -(screenWidth * 0.26f)

    var isSplashFinished by remember { mutableStateOf(false) }

    val claspOffsetX = remember { Animatable(0f) }
    val coverRotationY = remember { Animatable(0f) }
    val baseAlpha = remember { Animatable(1f) }

    val paperOffsetX = remember { Animatable(centerOffset.value) }
    val paperWobble = remember { Animatable(-15f) }

    val contentAlpha = remember { Animatable(0f) }
    val splashBgAlpha = remember { Animatable(1f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(150)

        claspOffsetX.animateTo(30f, tween(250, easing = FastOutSlowInEasing))
        coverRotationY.animateTo(-100f, tween(500, easing = LinearOutSlowInEasing))

        launch { baseAlpha.animateTo(0f, tween(400)) }
        launch { paperOffsetX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow)) }
        launch { paperWobble.animateTo(2f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
        launch { contentAlpha.animateTo(1f, tween(600)) }
        launch { splashBgAlpha.animateTo(0f, tween(600)) }

        delay(400)
        textAlpha.animateTo(1f, tween(500))

        delay(500)
        isSplashFinished = true // 动画彻底结束标志位
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ==========================================
        // 底部真实 UI 层
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(premiumGradient)
                .alpha(contentAlpha.value)
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            Text("Minimalist", fontSize = 48.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = deepText, lineHeight = 52.sp, letterSpacing = (-1).sp)
            Text("Notebook", fontSize = 48.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = deepText.copy(alpha = 0.8f), lineHeight = 52.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text("How tiny words create\nremarkable fluency", fontSize = 18.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = warmOrange, lineHeight = 26.sp)
            Spacer(modifier = Modifier.height(24.dp))

            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.fillMaxWidth(0.55f).aspectRatio(1f).align(Alignment.CenterStart).drawBehind {
                    val lightRadius = size.width * 0.85f
                    drawCircle(Brush.radialGradient(listOf(warmOrange.copy(alpha = 0.35f), warmOrange.copy(alpha = 0.15f), warmOrange.copy(alpha = 0.05f), Color.Transparent), center = center, radius = lightRadius), radius = lightRadius, center = center)
                }, contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$totalWords", fontSize = 64.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif, color = deepText)
                        Text(text = "Total Words", fontSize = 14.sp, fontFamily = FontFamily.Serif, color = deepText.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                    }
                }

                // 🌟 修复 1：底层纸张组件加入统一的 padding(8.dp)，完美对齐动画尺寸
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.48f)
                        .aspectRatio(1f)
                        .align(Alignment.CenterEnd)
                        .graphicsLayer { transformOrigin = TransformOrigin(0.5f, 0.2f); rotationZ = paperWobble.value },
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        Box(modifier = Modifier.matchParentSize().padding(start = 4.dp, top = 4.dp).background(deepBurgundy.copy(alpha = 0.12f), LooseLeafPaperShape()))
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(end = 4.dp, bottom = 4.dp)
                                .background(looseLeafColor, LooseLeafPaperShape())
                                .border(1.dp, deepText.copy(alpha = 0.08f), LooseLeafPaperShape())
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(top = 28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = "$toReviewCount", fontSize = 52.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = if (toReviewCount > 0) warmOrange else deepText)
                                Text(text = "To Review", fontSize = 14.sp, fontFamily = FontFamily.Serif, color = deepText.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        PremiumPillButton("Start Review", { if (toReviewCount > 0) onNavigateToReview() }, true, toReviewCount > 0, deepBurgundy)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(modifier = Modifier.size(64.dp).background(deepBurgundy, CircleShape).clickable { onNavigateToAdd() }, contentAlignment = Alignment.Center) {
                        Text("+", color = Color.White, fontSize = 32.sp, fontFamily = FontFamily.Serif, modifier = Modifier.offset(y = (-2).dp))
                    }
                }
                Spacer(modifier = Modifier.height(36.dp))
                Text("View My Word List", fontSize = 15.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = deepText.copy(alpha = 0.5f), modifier = Modifier.clickable { onNavigateToList() }.padding(8.dp))
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // ==========================================
        // 动画遮罩层与 3D 动画层 (用完即焚)
        // ==========================================
        // 🌟 修复 2：将整个 3D 动画图层包裹在判断内，动画结束后彻底销毁该层防穿模
        if (!isSplashFinished) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFDFBF7)).alpha(splashBgAlpha.value).zIndex(5f))

            Column(modifier = Modifier.fillMaxSize().padding(24.dp).zIndex(10f)) {
                Spacer(modifier = Modifier.height(40.dp))
                Text("Minimalist", fontSize = 48.sp, color = Color.Transparent, lineHeight = 52.sp, letterSpacing = (-1).sp)
                Text("Notebook", fontSize = 48.sp, color = Color.Transparent, lineHeight = 52.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("How tiny words create\nremarkable fluency", fontSize = 18.sp, color = Color.Transparent, lineHeight = 26.sp)
                Spacer(modifier = Modifier.height(24.dp))

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.48f)
                            .align(Alignment.CenterEnd)
                            .offset(x = paperOffsetX.value.dp)
                            .graphicsLayer { transformOrigin = TransformOrigin(0.5f, 0.2f); rotationZ = paperWobble.value },
                        contentAlignment = Alignment.Center
                    ) {
                        if (baseAlpha.value > 0.01f) {
                            Image(painter = painterResource(id = R.drawable.book_base), contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(1f).alpha(baseAlpha.value))
                        }

                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(8.dp)) {
                            Box(modifier = Modifier.matchParentSize().padding(start = 4.dp, top = 4.dp).background(deepBurgundy.copy(alpha = 0.12f), LooseLeafPaperShape()))
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .padding(end = 4.dp, bottom = 4.dp)
                                    .background(looseLeafColor, LooseLeafPaperShape())
                                    .border(1.dp, deepText.copy(alpha = 0.08f), LooseLeafPaperShape())
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(top = 28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(textAlpha.value)) {
                                        Text(text = "$toReviewCount", fontSize = 52.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = if (toReviewCount > 0) warmOrange else deepText)
                                        Text(text = "To Review", fontSize = 14.sp, fontFamily = FontFamily.Serif, color = deepText.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        if (coverRotationY.value > -90f) {
                            Image(
                                painter = painterResource(id = R.drawable.book_cover),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f).graphicsLayer {
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    rotationY = coverRotationY.value
                                    cameraDistance = 12f * density
                                }
                            )
                        }

                        if (baseAlpha.value > 0.01f) {
                            Image(
                                painter = painterResource(id = R.drawable.book_clasp),
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxWidth(0.3f)
                                    .offset(x = claspOffsetX.value.dp)
                                    .alpha(baseAlpha.value)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f).height(64.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Box(modifier = Modifier.size(64.dp))
                    }
                    Spacer(modifier = Modifier.height(36.dp))
                    Text("View My Word List", fontSize = 15.sp, color = Color.Transparent, modifier = Modifier.padding(8.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
fun PremiumPillButton(text: String, onClick: () -> Unit, isPrimary: Boolean = false, enabled: Boolean = true, baseColor: Color) {
    val bgColor = if (!enabled) baseColor.copy(alpha = 0.1f) else if (isPrimary) baseColor else Color.Transparent
    val textColor = if (!enabled) baseColor.copy(alpha = 0.3f) else if (isPrimary) Color.White else baseColor
    val borderModifier = if (isPrimary || !enabled) Modifier else Modifier.border(1.5.dp, baseColor, CircleShape)

    Box(modifier = Modifier.fillMaxWidth().height(64.dp).then(borderModifier).background(bgColor, CircleShape).clickable(enabled = enabled) { onClick() }.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 18.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = textColor)
    }
}

class LooseLeafPaperShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            val cornerRadius = density.run { 12.dp.toPx() }
            addRoundRect(androidx.compose.ui.geometry.RoundRect(rect = androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), topLeft = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius), topRight = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius), bottomLeft = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius), bottomRight = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)))
            val holeRadius = density.run { 4.5.dp.toPx() }
            val holeMarginTop = density.run { 14.dp.toPx() }
            val numHoles = 3
            val spacing = size.width / numHoles
            for (i in 0 until numHoles) {
                val centerX = (i * spacing) + (spacing / 2)
                addOval(androidx.compose.ui.geometry.Rect(left = centerX - holeRadius, top = holeMarginTop - holeRadius, right = centerX + holeRadius, bottom = holeMarginTop + holeRadius))
            }
            fillType = PathFillType.EvenOdd
        }
        return Outline.Generic(path)
    }
}