package com.example.meritzshortcut.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.meritzshortcut.data.Receipt
import com.example.meritzshortcut.service.MeritzAccessibilityService
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Theme Colors matching the modern design
private val BgColor = Color(0xFFF7F8FA)
private val TextPrimary = Color(0xFF191F28)
private val TextSecondary = Color(0xFF8B95A1)
private val CardBg = Color(0xFFFFFFFF)
private val ButtonPurpleBg = Color(0xFFF0F1FE)
private val PurplePrimary = Color(0xFF4F46E5)
private val PurpleBadgeBg = Color(0xFFEEF0FB)
private val MeritzRed = Color(0xFFE52528)
private val BorderSubtle = Color(0xFFECEFF2)

@Composable
fun HomeScreen(viewModel: ReceiptViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val pendingReceipts by viewModel.pendingReceipts.collectAsState()
    val completedReceipts by viewModel.completedReceipts.collectAsState()
    val previewReceipt by viewModel.previewReceipt.collectAsState()

    var isA11yEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isA11yEnabled = isAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        viewModel.onCameraCaptured(success)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onPhotosSelected(uris)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // 1. Top Header
                item {
                    Spacer(modifier = Modifier.statusBarsPadding())
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "실비 청구 도우미",
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "영수증을 저장하고 청구 여부를 관리해요",
                                fontSize = 15.sp,
                                color = TextSecondary
                            )
                        }

                        if (isA11yEnabled) {
                            Surface(
                                color = Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2E7D32))
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "자동 진입 켜짐",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }
                        }
                    }
                }

                // 1-1. 접근성 미활성화 시 원터치 가이드 카드
                if (!isA11yEnabled) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.FlashOn,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "메리츠 자동 청구 연결 설정",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF92400E)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "터치하여 [설치된 앱 > 메리츠 청구 도우미]를 켜주세요",
                                        fontSize = 12.sp,
                                        color = Color(0xFFB45309)
                                    )
                                }
                                Icon(
                                    Icons.Outlined.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFFD97706),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // 2. Tab Cards (청구 대기 vs 완료 보관함)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TabCard(
                            title = "청구 대기",
                            count = pendingReceipts.size,
                            icon = Icons.Outlined.Folder,
                            isSelected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            modifier = Modifier.weight(1f)
                        )

                        TabCard(
                            title = "완료 보관함",
                            count = completedReceipts.size,
                            icon = Icons.Outlined.Inventory2,
                            isSelected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 3. 영수증 추가 Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 1.dp, shape = RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Text(
                                text = "영수증 추가",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "영수증을 촬영하거나 갤러리에서 선택하세요.",
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(18.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ActionButton(
                                    text = "사진 촬영",
                                    icon = Icons.Outlined.PhotoCamera,
                                    onClick = {
                                        val uri = viewModel.prepareCameraCapture()
                                        cameraLauncher.launch(uri)
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                ActionButton(
                                    text = "갤러리 선택",
                                    icon = Icons.Outlined.Image,
                                    onClick = {
                                        galleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // 4. Section Header (미처리 영수증 vs 완료된 영수증)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedTabIndex == 0) "미처리 영수증" else "완료된 영수증",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${if (selectedTabIndex == 0) pendingReceipts.size else completedReceipts.size}건",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }
                }

                // 5. Receipt List or Empty State
                val currentList = if (selectedTabIndex == 0) pendingReceipts else completedReceipts

                if (currentList.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation = 1.dp, shape = RoundedCornerShape(20.dp)),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = CardBg)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 26.dp, horizontal = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (selectedTabIndex == 0) {
                                    ReceiptEmptyIllustration()
                                } else {
                                    ArchiveEmptyIllustration()
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = if (selectedTabIndex == 0) "아직 청구할 영수증이 없어요" else "완료된 영수증이 없습니다",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (selectedTabIndex == 0) "진료비/약제비 영수증을 추가해 보세요." else "청구를 마친 영수증이 이곳에 안전하게 보관됩니다.",
                                    fontSize = 14.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(currentList, key = { _, item -> item.id }) { index, receipt ->
                        ModernReceiptCard(
                            receipt = receipt,
                            index = index,
                            isPending = selectedTabIndex == 0,
                            onPreview = { viewModel.setPreviewReceipt(receipt) },
                            onRestore = { viewModel.revertToPending(receipt.id) },
                            onDelete = { viewModel.deleteReceipt(receipt.id) }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 6. Bottom Bar: 단일 레벨의 명확한 완료/실행 플로우
            if (selectedTabIndex == 0) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    color = BgColor
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.launchMeritzApp(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeritzRed),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "메리츠에서 청구하기",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (pendingReceipts.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { viewModel.markAllPendingCompleted() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFD4DAFA)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PurplePrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "대기 ${pendingReceipts.size}건 완료 처리",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Fullscreen Preview Dialog
    previewReceipt?.let { receipt ->
        Dialog(onDismissRequest = { viewModel.setPreviewReceipt(null) }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = CardBg
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (receipt.isCompleted) "완료된 영수증" else "청구 대기 영수증",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextPrimary
                        )
                        IconButton(onClick = { viewModel.setPreviewReceipt(null) }) {
                            Icon(Icons.Default.Close, contentDescription = "닫기", tint = TextSecondary)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    AsyncImage(
                        model = File(receipt.filePath),
                        contentDescription = "영수증 확대",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 450.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.deleteReceipt(receipt.id)
                                viewModel.setPreviewReceipt(null)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626))
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("완전 삭제")
                        }

                        if (!receipt.isCompleted) {
                            Button(
                                onClick = {
                                    viewModel.markReceiptCompleted(receipt.id)
                                    viewModel.setPreviewReceipt(null)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("이 영수증만 완료")
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.revertToPending(receipt.id)
                                    viewModel.setPreviewReceipt(null)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                            ) {
                                Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("대기로 복원")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabCard(
    title: String,
    count: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(58.dp)
            .clickable { onClick() }
            .shadow(
                elevation = if (isSelected) 2.dp else 0.dp,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CardBg else CardBg.copy(alpha = 0.7f)
        ),
        border = if (isSelected) BorderStroke(1.5.dp, Color(0xFFD4DAFA)) else BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) PurplePrimary else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) TextPrimary else TextSecondary
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (count > 0) PurpleBadgeBg else Color(0xFFF1F3F5))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$count",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (count > 0) PurplePrimary else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ButtonPurpleBg,
            contentColor = PurplePrimary
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PurplePrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = PurplePrimary
            )
        }
    }
}

@Composable
private fun ModernReceiptCard(
    receipt: Receipt,
    index: Int,
    isPending: Boolean,
    onPreview: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val dateString = remember(receipt.createdAt) {
        val sdf = SimpleDateFormat("MM.dd HH:mm", Locale.getDefault())
        sdf.format(Date(receipt.createdAt))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPreview() }
            .shadow(elevation = 1.dp, shape = RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = File(receipt.filePath),
                contentDescription = "영수증 썸네일",
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF1F3F5)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isPending) "영수증 #${index + 1}" else "보관된 영수증 #${index + 1}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dateString,
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            if (!isPending) {
                IconButton(
                    onClick = onRestore,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PurpleBadgeBg)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Restore,
                        contentDescription = "대기로 복원",
                        tint = PurplePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF9FAFB))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "삭제",
                    tint = Color(0xFF9CA3AF),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Custom illustration matching the receipt drawing in the design mockup:
 * A receipt shape with subtle lines, decorative sparkles, and a purple check badge.
 */
@Composable
private fun ReceiptEmptyIllustration() {
    Box(
        modifier = Modifier.size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val sparkleColor = Color(0xFFD2D7FB)
            drawLine(
                color = sparkleColor,
                start = Offset(w * 0.15f, h * 0.35f),
                end = Offset(w * 0.22f, h * 0.38f),
                strokeWidth = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                color = sparkleColor,
                start = Offset(w * 0.14f, h * 0.48f),
                end = Offset(w * 0.20f, h * 0.48f),
                strokeWidth = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                color = sparkleColor,
                start = Offset(w * 0.85f, h * 0.32f),
                end = Offset(w * 0.78f, h * 0.38f),
                strokeWidth = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            val receiptLeft = w * 0.28f
            val receiptTop = h * 0.18f
            val receiptWidth = w * 0.44f
            val receiptHeight = h * 0.62f
            val cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())

            drawRoundRect(
                color = Color(0xFFF8F9FE),
                topLeft = Offset(receiptLeft, receiptTop),
                size = Size(receiptWidth, receiptHeight),
                cornerRadius = cornerRadius
            )
            drawRoundRect(
                color = Color(0xFFD2D7FB),
                topLeft = Offset(receiptLeft, receiptTop),
                size = Size(receiptWidth, receiptHeight),
                cornerRadius = cornerRadius,
                style = Stroke(width = 2.5.dp.toPx())
            )

            val lineLeft = receiptLeft + receiptWidth * 0.22f
            val lineWidth = receiptWidth * 0.56f
            val lineStroke = 2.5.dp.toPx()

            drawLine(
                color = Color(0xFFB8C0F5),
                start = Offset(lineLeft, receiptTop + receiptHeight * 0.30f),
                end = Offset(lineLeft + lineWidth, receiptTop + receiptHeight * 0.30f),
                strokeWidth = lineStroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFB8C0F5),
                start = Offset(lineLeft, receiptTop + receiptHeight * 0.46f),
                end = Offset(lineLeft + lineWidth * 0.7f, receiptTop + receiptHeight * 0.46f),
                strokeWidth = lineStroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFB8C0F5),
                start = Offset(lineLeft, receiptTop + receiptHeight * 0.62f),
                end = Offset(lineLeft + lineWidth * 0.5f, receiptTop + receiptHeight * 0.62f),
                strokeWidth = lineStroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-14).dp, y = (-10).dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0xFF8177F6)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/**
 * Custom illustration for empty completed archive:
 * An archive box shape with soft lavender lines.
 */
@Composable
private fun ArchiveEmptyIllustration() {
    Box(
        modifier = Modifier.size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Sparkles
            val sparkleColor = Color(0xFFD2D7FB)
            drawLine(
                color = sparkleColor,
                start = Offset(w * 0.18f, h * 0.32f),
                end = Offset(w * 0.24f, h * 0.35f),
                strokeWidth = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            drawLine(
                color = sparkleColor,
                start = Offset(w * 0.82f, h * 0.32f),
                end = Offset(w * 0.76f, h * 0.35f),
                strokeWidth = 3.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            // Box Body
            val boxLeft = w * 0.24f
            val boxTop = h * 0.30f
            val boxWidth = w * 0.52f
            val boxHeight = h * 0.46f

            drawRoundRect(
                color = Color(0xFFF8F9FE),
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
            )
            drawRoundRect(
                color = Color(0xFFD2D7FB),
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxWidth, boxHeight),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Box Lid line
            drawLine(
                color = Color(0xFFB8C0F5),
                start = Offset(boxLeft, boxTop + boxHeight * 0.35f),
                end = Offset(boxLeft + boxWidth, boxTop + boxHeight * 0.35f),
                strokeWidth = 2.5.dp.toPx()
            )

            // Box Handle slot
            drawRoundRect(
                color = Color(0xFFD2D7FB),
                topLeft = Offset(boxLeft + boxWidth * 0.35f, boxTop + boxHeight * 0.55f),
                size = Size(boxWidth * 0.30f, 6.dp.toPx()),
                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
            )
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = "${context.packageName}/${MeritzAccessibilityService::class.java.canonicalName}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
}
