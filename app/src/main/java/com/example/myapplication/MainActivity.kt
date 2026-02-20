package com.example.myapplication

import android.Manifest
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
 */
private fun getOutputFileInSameDirectory(
    context: android.content.Context,
    sourceUri: Uri,
    outputFileName: String
): File? {
    return try {
        // 方法1: 尝试从URI获取真实文件路径
        val sourceFile = getFileFromUri(context, sourceUri)
        if (sourceFile != null && sourceFile.parentFile != null && sourceFile.parentFile.exists()) {
            val outputFile = File(sourceFile.parentFile, outputFileName)
            // 检查是否有写入权限
            if (sourceFile.parentFile.canWrite()) {
                return outputFile
            }
        }
        
        // 方法2: 使用DocumentFile API (Android 5.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val documentFile = DocumentFile.fromSingleUri(context, sourceUri)
            if (documentFile != null && documentFile.exists()) {
                val parentUri = documentFile.parentFile?.uri
                if (parentUri != null) {
                    // 尝试通过parent URI创建文件
                    val parentDoc = DocumentFile.fromTreeUri(context, parentUri)
                    if (parentDoc != null && parentDoc.canWrite()) {
                        // 使用MediaStore API保存文件
                        val contentValues = android.content.ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, outputFileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
                            }
                        }
                        
                        val outputUri = context.contentResolver.insert(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            contentValues
                        )
                        
                        if (outputUri != null) {
                            // 返回一个临时文件路径用于FFmpeg，稍后会通过URI写入
                            return File(context.cacheDir, outputFileName)
                        }
                    }
                }
            }
        }
        
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
        // 尝试获取源文件目录
        val sourceFileObj = getFileFromUri(context, sourceUri)
        if (sourceFileObj != null && sourceFileObj.parentFile != null && sourceFileObj.parentFile.exists()) {
            val outputFile = File(sourceFileObj.parentFile, outputFileName)
            if (sourceFileObj.parentFile.canWrite()) {
                sourceFile.copyTo(outputFile, overwrite = true)
                return outputFile
            }
        }
        
        // 使用MediaStore API保存
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, outputFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
            }
            
            val outputUri = context.contentResolver.insert(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            
            if (outputUri != null) {
                context.contentResolver.openOutputStream(outputUri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                
                // 尝试获取文件路径
                return getFileFromUri(context, outputUri)
            }
        }
        
        null
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Failed to copy to source directory", e)
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
            // Android 10+ (API 29+) 使用Scoped Storage，DATA列可能不可用
            // 尝试多种方法获取路径
            val cursor = context.contentResolver.query(uri, null, null, null, null)
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
