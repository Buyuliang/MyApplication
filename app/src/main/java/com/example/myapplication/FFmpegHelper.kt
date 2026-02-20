package com.example.myapplication

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object FFmpegHelper {
    private const val TAG = "FFmpegHelper"
    private const val FFMPEG_BINARY_NAME = "ffmpeg"
    private const val ASSETS_DIR = "ffmpeg"
    
    /**
     * 从assets复制ffmpeg二进制文件到应用私有目录
     */
    fun setupFFmpeg(context: Context): File? {
        try {
            val ffmpegDir = File(context.filesDir, "ffmpeg")
            if (!ffmpegDir.exists()) {
                ffmpegDir.mkdirs()
            }
            
            val ffmpegFile = File(ffmpegDir, FFMPEG_BINARY_NAME)
            
            // 如果文件已存在且可执行，直接返回
            if (ffmpegFile.exists() && ffmpegFile.canExecute()) {
                return ffmpegFile
            }
            
            // 从assets复制文件
            try {
                val inputStream = context.assets.open("$ASSETS_DIR/$FFMPEG_BINARY_NAME")
                val outputStream = FileOutputStream(ffmpegFile)
                
                inputStream.copyTo(outputStream)
                inputStream.close()
                outputStream.close()
                
                // 设置执行权限
                ffmpegFile.setExecutable(true)
                ffmpegFile.setReadable(true)
                
                Log.d(TAG, "FFmpeg binary copied successfully to: ${ffmpegFile.absolutePath}")
                return ffmpegFile
            } catch (e: IOException) {
                Log.e(TAG, "Failed to copy FFmpeg from assets", e)
                // 如果assets中没有，尝试使用系统PATH中的ffmpeg
                return tryFindSystemFFmpeg()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup FFmpeg", e)
            return tryFindSystemFFmpeg()
        }
    }
    
    /**
     * 尝试在系统PATH中查找ffmpeg
     */
    private fun tryFindSystemFFmpeg(): File? {
        val systemPaths = arrayOf(
            "/system/bin/ffmpeg",
            "/system/xbin/ffmpeg",
            "/data/local/tmp/ffmpeg"
        )
        
        for (path in systemPaths) {
            val file = File(path)
            if (file.exists() && file.canExecute()) {
                Log.d(TAG, "Found system FFmpeg at: $path")
                return file
            }
        }
        
        Log.w(TAG, "FFmpeg not found in system paths")
        return null
    }
    
    /**
     * 将MP4文件转换为MP3
     * 
     * 采样率说明：
     * - 44100 Hz (44.1kHz) 是CD音频标准采样率
     * - 根据奈奎斯特定理：采样率需要至少是最高频率的2倍
     * - 人类听觉范围：20Hz - 20kHz
     * - 44.1kHz = 22050Hz × 2，可以完美重现20kHz以下的音频
     * - 这是音频行业广泛采用的标准，兼容性好，音质优秀
     * 
     * 比特率说明：
     * - 192kbps 是MP3的高质量比特率
     * - 常见选择：128kbps(标准)、192kbps(高质量)、256kbps(超高质量)、320kbps(最高质量)
     * - 192kbps在文件大小和音质之间取得良好平衡
     * 
     * @param ffmpegPath FFmpeg二进制文件路径
     * @param inputFile 输入的MP4文件
     * @param outputFile 输出的MP3文件
     * @param sampleRate 采样率，默认44100Hz（CD标准）。可选值：22050, 44100, 48000
     * @param bitrate 比特率，默认192kbps（高质量）。可选值：128k, 192k, 256k, 320k
     * @return 转换是否成功
     */
    fun convertMp4ToMp3(
        ffmpegPath: String,
        inputFile: File,
        outputFile: File,
        sampleRate: Int = 44100,
        bitrate: String = "192k"
    ): Boolean {
        if (!inputFile.exists()) {
            Log.e(TAG, "Input file does not exist: ${inputFile.absolutePath}")
            return false
        }
        
        try {
            // 构建ffmpeg命令
            // -i: 输入文件
            // -vn: 移除视频流，只保留音频
            // -ar: 采样率（Audio Sample Rate）
            //   44100 Hz = CD标准，可完美重现20kHz以下音频（人类听觉上限）
            //   48000 Hz = DVD/视频标准
            //   22050 Hz = 低质量，但文件更小
            // -ab: 音频比特率（Audio Bitrate）
            //   192k = 高质量，文件大小和音质平衡
            //   128k = 标准质量
            //   256k/320k = 超高质量，但文件更大
            // -acodec libmp3lame: 使用LAME MP3编码器（可选，FFmpeg会自动选择）
            // -f mp3: 指定输出格式为MP3
            // -y: 自动覆盖输出文件
            val command = arrayOf(
                ffmpegPath,
                "-i", inputFile.absolutePath,
                "-vn",  // 移除视频流
                "-ar", sampleRate.toString(),  // 采样率
                "-ab", bitrate,  // 比特率
                "-f", "mp3",  // 输出格式
                "-y",  // 覆盖输出文件
                outputFile.absolutePath
            )
            
            Log.d(TAG, "FFmpeg command: ${command.joinToString(" ")}")
            Log.d(TAG, "Sample rate: ${sampleRate}Hz (CD标准: 44100Hz, 可重现0-${sampleRate/2}Hz音频)")
            Log.d(TAG, "Bitrate: $bitrate (高质量: 192k, 标准: 128k, 超高质量: 256k/320k)")
            
            Log.d(TAG, "Executing command: ${command.joinToString(" ")}")
            
            val process = ProcessBuilder(command.toList())
                .redirectErrorStream(true)
                .start()
            
            // 读取输出（用于调试）
            val output = process.inputStream.bufferedReader().readText()
            Log.d(TAG, "FFmpeg output: $output")
            
            val exitCode = process.waitFor()
            
            if (exitCode == 0 && outputFile.exists()) {
                Log.d(TAG, "Conversion successful: ${outputFile.absolutePath}")
                return true
            } else {
                Log.e(TAG, "Conversion failed with exit code: $exitCode")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during conversion", e)
            return false
        }
    }
}
