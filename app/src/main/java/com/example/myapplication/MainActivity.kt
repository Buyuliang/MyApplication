package com.example.myapplication

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // enableEdgeToEdge() 需要 Android 11+ (API 30+)，Android 7.0不支持
        // 如果需要，可以使用条件判断：if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { enableEdgeToEdge() }
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MP4ToMP3Converter(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MP4ToMP3Converter(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isConverting by remember { mutableStateOf(false) }
    var conversionStatus by remember { mutableStateOf<String?>(null) }
    var outputFilePath by remember { mutableStateOf<String?>(null) }
    var ffmpegFile by remember { mutableStateOf<File?>(null) }
    
    // 文件选择器（需要先定义，因为permissionLauncher会使用它）
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedFileUri = it
            // 获取文件名
            context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        selectedFileName = cursor.getString(nameIndex)
                    }
                }
            }
            conversionStatus = null
            outputFilePath = null
        }
    }
    
    // 存储权限请求
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            // 权限授予后，如果还没有选择文件，自动打开文件选择器
            if (selectedFileUri == null) {
                filePickerLauncher.launch("video/mp4")
            }
        } else {
            Toast.makeText(context, "需要存储权限才能将文件保存到源文件目录", Toast.LENGTH_LONG).show()
        }
    }
    
    // 初始化FFmpeg
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            ffmpegFile = FFmpegHelper.setupFFmpeg(context)
            if (ffmpegFile == null) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "警告: 未找到FFmpeg二进制文件，请将ffmpeg文件放入assets/ffmpeg/目录",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "MP4转MP3转换器",
            style = MaterialTheme.typography.headlineMedium
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 文件选择按钮
        Button(
            onClick = {
                // 检查并请求存储权限
                val permissions = mutableListOf<String>()
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    // Android 9.0及以下需要WRITE_EXTERNAL_STORAGE
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                        != PackageManager.PERMISSION_GRANTED) {
                        permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                
                if (permissions.isNotEmpty()) {
                    permissionLauncher.launch(permissions.toTypedArray())
                } else {
                    filePickerLauncher.launch("video/mp4")
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("选择MP4文件")
        }
        
        // 如果权限未授予，显示提示
        LaunchedEffect(Unit) {
            val hasReadPermission = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            
            val hasWritePermission = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                ContextCompat.checkSelfPermission(
                    context, 
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            
            if (!hasReadPermission || !hasWritePermission) {
                // 自动请求权限
                val permissions = mutableListOf<String>()
                if (!hasReadPermission) {
                    permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                if (!hasWritePermission && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                if (permissions.isNotEmpty()) {
                    permissionLauncher.launch(permissions.toTypedArray())
                }
            }
        }
        
        // 显示选中的文件
        if (selectedFileName != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "已选择文件:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = selectedFileName ?: "",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        
        // 转换按钮
        Button(
            onClick = {
                if (selectedFileUri == null) {
                    Toast.makeText(context, "请先选择MP4文件", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                
                if (ffmpegFile == null || !ffmpegFile!!.exists()) {
                    Toast.makeText(context, "FFmpeg未准备好，请检查二进制文件", Toast.LENGTH_LONG).show()
                    return@Button
                }
                
                isConverting = true
                conversionStatus = "正在转换..."
                
                scope.launch {
                    try {
                        var outputFile: File? = null
                        val success = withContext(Dispatchers.IO) {
                            // 从URI复制文件到临时目录
                            val tempInputFile = copyUriToTempFile(context, selectedFileUri!!, selectedFileName ?: "input.mp4")
                            if (tempInputFile == null || !tempInputFile.exists()) {
                                false
                            } else {
                                // 生成输出文件名
                                val baseName = tempInputFile.nameWithoutExtension
                                val outputFileName = "$baseName.mp3"
                                
                                // 尝试获取原始文件的目录并保存到同一目录
                                outputFile = getOutputFileInSameDirectory(context, selectedFileUri!!, outputFileName)
                                
                                val result = if (outputFile != null) {
                                    // 直接转换到目标目录
                                    FFmpegHelper.convertMp4ToMp3(
                                        ffmpegFile!!.absolutePath,
                                        tempInputFile,
                                        outputFile
                                    )
                                } else {
                                    // 先转换到临时目录，再尝试复制到源文件目录
                                    val tempOutputFile = File(context.cacheDir, outputFileName)
                                    val convertResult = FFmpegHelper.convertMp4ToMp3(
                                        ffmpegFile!!.absolutePath,
                                        tempInputFile,
                                        tempOutputFile
                                    )
                                    
                                    if (convertResult && tempOutputFile.exists()) {
                                        // 尝试复制到源文件目录
                                        val copiedFile = copyToSourceDirectory(context, selectedFileUri!!, tempOutputFile, outputFileName)
                                        if (copiedFile != null && copiedFile.exists()) {
                                            outputFile = copiedFile
                                            tempOutputFile.delete() // 删除临时文件
                                        } else {
                                            outputFile = tempOutputFile // 使用临时文件
                                        }
                                    }
                                    
                                    convertResult
                                }
                                
                                // 清理临时输入文件
                                tempInputFile.delete()
                                
                                // 设置输出文件路径
                                if (result && outputFile != null && outputFile.exists()) {
                                    outputFilePath = outputFile.absolutePath
                                }
                                
                                result
                            }
                        }
                        
                        isConverting = false
                        if (success) {
                            conversionStatus = "转换成功！"
                            Toast.makeText(context, "转换完成！", Toast.LENGTH_SHORT).show()
                        } else {
                            conversionStatus = "转换失败"
                            outputFilePath = null
                            Toast.makeText(context, "转换失败，请检查日志", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        isConverting = false
                        conversionStatus = "转换出错: ${e.message}"
                        outputFilePath = null
                        Toast.makeText(context, "转换出错: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConverting && selectedFileUri != null && ffmpegFile != null
        ) {
            if (isConverting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("转换中...")
            } else {
                Text("开始转换")
            }
        }
        
        // 显示转换状态
        if (conversionStatus != null) {
            Text(
                text = conversionStatus!!,
                style = MaterialTheme.typography.bodyMedium,
                color = if (conversionStatus!!.contains("成功")) {
                    MaterialTheme.colorScheme.primary
                } else if (conversionStatus!!.contains("失败") || conversionStatus!!.contains("出错")) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
        
        // 显示输出文件路径
        if (outputFilePath != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "MP3文件已保存到:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = outputFilePath!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                openFileInFileManager(context, outputFilePath!!)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("打开文件管理器")
                        }
                        Button(
                            onClick = {
                                // 只复制文件名，不包含路径
                                val fileName = File(outputFilePath!!).name
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("文件名", fileName)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "已复制文件名: $fileName", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("复制文件名")
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 说明文字
        Text(
            text = "提示: 请将ffmpeg二进制文件放入\napp/src/main/assets/ffmpeg/目录",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 从URI复制文件到临时目录
 */
private fun copyUriToTempFile(context: android.content.Context, uri: Uri, fileName: String): File? {
    return try {
        val tempDir = File(context.cacheDir, "ffmpeg_temp")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        
        val tempFile = File(tempDir, fileName)
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        if (tempFile.exists()) {
            tempFile
        } else {
            null
        }
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Failed to copy URI to temp file", e)
        null
    }
}

/**
 * 获取输出文件路径（与源文件同一目录）
 * 返回null表示需要使用MediaStore API保存
 */
private fun getOutputFileInSameDirectory(
    context: android.content.Context,
    sourceUri: Uri,
    outputFileName: String
): File? {
    return try {
        android.util.Log.d("MainActivity", "尝试获取源文件目录: $sourceUri")
        
        // 方法1: 尝试从URI获取真实文件路径（适用于Android 9及以下）
        val sourceFile = getFileFromUri(context, sourceUri)
        if (sourceFile != null) {
            android.util.Log.d("MainActivity", "找到源文件: ${sourceFile.absolutePath}")
            val parentDir = sourceFile.parentFile
            if (parentDir != null && parentDir.exists()) {
                android.util.Log.d("MainActivity", "源文件目录: ${parentDir.absolutePath}, 可写: ${parentDir.canWrite()}")
                val outputFile = File(parentDir, outputFileName)
                
                // 尝试写入测试（更可靠的权限检查）
                try {
                    if (parentDir.canWrite() || parentDir.setWritable(true)) {
                        android.util.Log.d("MainActivity", "可以使用源文件目录: ${outputFile.absolutePath}")
                        return outputFile
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "无法写入源文件目录", e)
                }
            }
        }
        
        // 方法2: 对于content:// URI，尝试通过MediaStore获取源文件的实际路径
        if (sourceUri.scheme == "content") {
            // 将documents URI转换为标准MediaStore URI
            val mediaStoreUri = convertToMediaStoreUri(context, sourceUri) ?: sourceUri
            
            val projection = arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
            )
            
            context.contentResolver.query(mediaStoreUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    // 尝试获取DATA列（Android 9及以下）
                    val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataIndex >= 0) {
                        val dataPath = cursor.getString(dataIndex)
                        if (!dataPath.isNullOrEmpty()) {
                            android.util.Log.d("MainActivity", "MediaStore DATA路径: $dataPath")
                            val sourceFileObj = File(dataPath)
                            if (sourceFileObj.exists()) {
                                val parentDir = sourceFileObj.parentFile
                                if (parentDir != null && parentDir.exists()) {
                                    val outputFile = File(parentDir, outputFileName)
                                    android.util.Log.d("MainActivity", "使用DATA路径: ${outputFile.absolutePath}")
                                    return outputFile
                                }
                            }
                        }
                    }
                    
                    // 对于Android 10+，获取相对路径
                    val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    if (relativePathIndex >= 0) {
                        val relativePath = cursor.getString(relativePathIndex)
                        android.util.Log.d("MainActivity", "MediaStore相对路径: $relativePath")
                        // 返回null，让调用者使用MediaStore API保存
                    }
                }
            }
        }
        
        android.util.Log.w("MainActivity", "无法获取源文件目录，将使用MediaStore API")
        null
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Failed to get output file in same directory", e)
        null
    }
}

/**
 * 将文件复制到源文件目录
 */
private fun copyToSourceDirectory(
    context: android.content.Context,
    sourceUri: Uri,
    sourceFile: File,
    outputFileName: String
): File? {
    return try {
        android.util.Log.d("MainActivity", "尝试复制文件到源文件目录: $sourceUri")
        
        // 方法1: 尝试获取源文件目录并直接复制（适用于Android 9及以下）
        val sourceFileObj = getFileFromUri(context, sourceUri)
        if (sourceFileObj != null) {
            val parentDir = sourceFileObj.parentFile
            if (parentDir != null && parentDir.exists()) {
                val outputFile = File(parentDir, outputFileName)
                android.util.Log.d("MainActivity", "尝试复制到: ${outputFile.absolutePath}")
                
                try {
                    // 尝试设置目录可写
                    if (!parentDir.canWrite()) {
                        parentDir.setWritable(true)
                    }
                    
                    if (parentDir.canWrite() || parentDir.setWritable(true)) {
                        sourceFile.copyTo(outputFile, overwrite = true)
                        if (outputFile.exists()) {
                            android.util.Log.d("MainActivity", "成功复制到源文件目录: ${outputFile.absolutePath}")
                            return outputFile
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "直接复制失败，尝试MediaStore API", e)
                }
            }
        }
        
        // 方法2: 使用MediaStore API保存到源文件相同目录（适用于所有版本，特别是Android 10+）
        if (sourceUri.scheme == "content") {
            // 将documents URI转换为标准MediaStore URI
            val mediaStoreUri = convertToMediaStoreUri(context, sourceUri) ?: sourceUri
            
            // 获取源文件的相对路径和目录信息
            val projection = arrayOf(
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns._ID
            )
            
            context.contentResolver.query(mediaStoreUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    var relativePath: String? = null
                    
                    // 优先使用RELATIVE_PATH（Android 10+）
                    val relativePathIndex = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    if (relativePathIndex >= 0) {
                        relativePath = cursor.getString(relativePathIndex)
                    }
                    
                    // 如果没有相对路径，尝试从DATA路径提取
                    if (relativePath.isNullOrEmpty()) {
                        val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (dataIndex >= 0) {
                            val dataPath = cursor.getString(dataIndex)
                            if (!dataPath.isNullOrEmpty()) {
                                val file = File(dataPath)
                                val parent = file.parent
                                if (parent != null) {
                                    // 从完整路径提取相对路径
                                    val externalDir = Environment.getExternalStorageDirectory()
                                    if (externalDir != null && parent.startsWith(externalDir.absolutePath)) {
                                        relativePath = parent.substring(externalDir.absolutePath.length + 1)
                                        if (!relativePath.endsWith("/")) {
                                            relativePath += "/"
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    if (!relativePath.isNullOrEmpty()) {
                        android.util.Log.d("MainActivity", "使用源文件的相对路径保存: $relativePath")
                        
                        val contentValues = android.content.ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, outputFileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath) // 使用源文件的相对路径
                            }
                        }
                        
                        val outputUri = context.contentResolver.insert(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            contentValues
                        )
                        
                        if (outputUri != null) {
                            android.util.Log.d("MainActivity", "通过MediaStore创建文件: $outputUri")
                            context.contentResolver.openOutputStream(outputUri)?.use { output ->
                                sourceFile.inputStream().use { input ->
                                    input.copyTo(output)
                                }
                            }
                            
                            // 尝试获取文件路径用于显示
                            val savedFile = getFileFromUri(context, outputUri)
                            if (savedFile != null && savedFile.exists()) {
                                android.util.Log.d("MainActivity", "成功保存到: ${savedFile.absolutePath}")
                                return savedFile
                            } else {
                                // 即使无法获取路径，也返回一个表示成功的文件对象
                                android.util.Log.d("MainActivity", "文件已保存到MediaStore，路径: $relativePath$outputFileName")
                                // 返回URI对应的文件路径（可能为null，但转换已成功）
                                return getFileFromUri(context, outputUri) ?: sourceFile
                            }
                        } else {
                            android.util.Log.e("MainActivity", "MediaStore插入失败")
                        }
                    } else {
                        android.util.Log.w("MainActivity", "无法获取源文件的相对路径")
                    }
                }
            }
        }
        
        android.util.Log.w("MainActivity", "无法复制到源文件目录")
        null
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Failed to copy to source directory", e)
        e.printStackTrace()
        null
    }
}

/**
 * 在文件管理器中打开文件所在目录
 */
private fun openFileInFileManager(context: android.content.Context, filePath: String) {
    try {
        val file = File(filePath)
        val parentDir = file.parentFile
        
        if (parentDir == null || !parentDir.exists()) {
            Toast.makeText(context, "无法找到文件目录", Toast.LENGTH_SHORT).show()
            return
        }
        
        android.util.Log.d("MainActivity", "尝试打开文件管理器，目录：${parentDir.absolutePath}")
        
        // 方法1: 使用ACTION_SEND显示所有应用（包括文件管理器和其他应用）
        if (file.exists()) {
            try {
                val fileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Android 7.0+ 使用FileProvider
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } else {
                    // Android 7.0以下使用file:// URI
                    Uri.fromFile(file)
                }
                
                // 使用ACTION_SEND并设置通用类型，这样可以显示更多应用
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"  // 通用类型，显示所有应用
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    putExtra(Intent.EXTRA_TEXT, "文件路径：${file.absolutePath}\n目录：${parentDir.absolutePath}")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                // 创建选择器，显示所有应用
                try {
                    val chooserIntent = Intent.createChooser(sendIntent, "选择应用（显示所有应用）")
                    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooserIntent)
                    android.util.Log.d("MainActivity", "已打开应用选择器（显示所有应用）")
                    return
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "打开应用选择器失败", e)
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "打开文件失败", e)
            }
        }
        
        // 方法2: 尝试使用ACTION_VIEW打开文件（显示能处理音频的应用）
        if (file.exists()) {
            try {
                val fileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } else {
                    Uri.fromFile(file)
                }
                
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, "audio/mpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                try {
                    val chooserIntent = Intent.createChooser(intent, "选择应用打开文件")
                    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooserIntent)
                    android.util.Log.d("MainActivity", "已打开应用选择器")
                    return
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "打开应用选择器失败", e)
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "打开文件失败", e)
            }
        }
        
        // 方法2: 尝试使用MediaStore URI打开文件
        if (file.exists()) {
            try {
                val projection = arrayOf(MediaStore.MediaColumns._ID)
                val selection = "${MediaStore.MediaColumns.DATA} = ?"
                val selectionArgs = arrayOf(filePath)
                
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val id = cursor.getLong(idIndex)
                        val uri = Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "audio/mpeg")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val chooserIntent = Intent.createChooser(intent, "选择应用打开文件")
                        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooserIntent)
                        return
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "MediaStore方法失败", e)
            }
        }
        
        // 方法3: 尝试使用FileProvider打开目录（Android 7.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                // 使用FileProvider获取目录URI
                val dirUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    parentDir
                )
                
                // 尝试打开目录
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(dirUri, "resource/folder")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                // 尝试启动文件管理器
                val chooserIntent = Intent.createChooser(intent, "选择文件管理器")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooserIntent)
                return
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "FileProvider方法失败", e)
            }
        }
        
        // 方法2: 尝试使用MediaStore URI打开文件
        if (file.exists()) {
            try {
                val projection = arrayOf(MediaStore.MediaColumns._ID)
                val selection = "${MediaStore.MediaColumns.DATA} = ?"
                val selectionArgs = arrayOf(filePath)
                
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                        val id = cursor.getLong(idIndex)
                        val uri = Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "audio/mpeg")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val chooserIntent = Intent.createChooser(intent, "选择应用打开文件")
                        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooserIntent)
                        return
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "MediaStore方法失败", e)
            }
        }
        
        // 方法4: 显示文件路径提示
        Toast.makeText(
            context,
            "文件已保存到：\n${parentDir.absolutePath}\n\n请在文件管理器中手动打开此目录",
            Toast.LENGTH_LONG
        ).show()
        
        android.util.Log.d("MainActivity", "文件目录路径：${parentDir.absolutePath}")
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "打开文件管理器失败", e)
        val parentPath = try {
            File(filePath).parentFile?.absolutePath ?: "未知"
        } catch (ex: Exception) {
            "未知"
        }
        Toast.makeText(
            context,
            "无法自动打开文件管理器\n文件目录：$parentPath",
            Toast.LENGTH_LONG
        ).show()
    }
}

/**
 * 将documents URI转换为标准MediaStore URI
 * 例如: content://com.android.providers.media.documents/document/video%3A2563897
 * 转换为: content://media/external/video/media/2563897
 */
private fun convertToMediaStoreUri(context: android.content.Context, uri: Uri): Uri? {
    return try {
        if (uri.authority == "com.android.providers.media.documents") {
            val docId = android.net.Uri.decode(uri.lastPathSegment ?: "")
            android.util.Log.d("MainActivity", "解析documents URI, docId: $docId")
            
            when {
                docId.startsWith("video:") -> {
                    val videoId = docId.substringAfter("video:")
                    android.util.Log.d("MainActivity", "视频ID: $videoId")
                    Uri.parse("content://media/external/video/media/$videoId")
                }
                docId.startsWith("audio:") -> {
                    val audioId = docId.substringAfter("audio:")
                    android.util.Log.d("MainActivity", "音频ID: $audioId")
                    Uri.parse("content://media/external/audio/media/$audioId")
                }
                docId.startsWith("image:") -> {
                    val imageId = docId.substringAfter("image:")
                    android.util.Log.d("MainActivity", "图片ID: $imageId")
                    Uri.parse("content://media/external/images/media/$imageId")
                }
                else -> {
                    android.util.Log.w("MainActivity", "未知的docId格式: $docId")
                    null
                }
            }
        } else {
            null
        }
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "转换MediaStore URI失败", e)
        null
    }
}

/**
 * 从URI获取实际文件（如果可能）
 */
private fun getFileFromUri(context: android.content.Context, uri: Uri): File? {
    return try {
        // 尝试直接获取文件路径
        var filePath: String? = null
        
        // 如果是file:// URI
        if (uri.scheme == "file") {
            filePath = uri.path
        }
        // 如果是content:// URI，尝试获取实际路径
        else if (uri.scheme == "content") {
            // 将documents URI转换为标准MediaStore URI
            val mediaStoreUri = convertToMediaStoreUri(context, uri) ?: uri
            
            // Android 10+ (API 29+) 使用Scoped Storage，DATA列可能不可用
            // 尝试多种方法获取路径
            val cursor = context.contentResolver.query(mediaStoreUri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    // 方法1: 尝试获取DATA列（Android 9及以下）
                    val dataIndex = it.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataIndex >= 0) {
                        filePath = it.getString(dataIndex)
                    }
                    
                    // 方法2: 如果DATA不可用，尝试从DISPLAY_NAME和相对路径构建
                    if (filePath.isNullOrEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val displayNameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        val relativePathIndex = it.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                        
                        if (displayNameIndex >= 0) {
                            val displayName = it.getString(displayNameIndex)
                            val relativePath = if (relativePathIndex >= 0) {
                                it.getString(relativePathIndex)
                            } else {
                                Environment.DIRECTORY_MUSIC
                            }
                            
                            // 构建完整路径
                            val externalDir = Environment.getExternalStorageDirectory()
                            if (externalDir != null && displayName != null) {
                                filePath = File(externalDir, "$relativePath/$displayName").absolutePath
                            }
                        }
                    }
                }
            }
        }
        
        if (filePath != null) {
            val file = File(filePath)
            if (file.exists() && file.parentFile != null && file.parentFile.exists()) {
                file
            } else {
                null
            }
        } else {
            null
        }
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Failed to get file from URI", e)
        null
    }
}
