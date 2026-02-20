# FFmpeg 设置指南

## 概述

本应用使用FFmpeg将MP4视频文件转换为MP3音频文件。在使用应用之前，需要将FFmpeg二进制文件添加到项目中。

## 步骤1: 下载FFmpeg二进制文件

### 方法1: 使用FFmpeg-Kit（推荐）

1. 访问 [FFmpeg-Kit GitHub Releases](https://github.com/tanersener/ffmpeg-kit/releases)
2. 下载适用于Android的版本，例如：
   - `ffmpeg-kit-full-5.1.LTS.aar` (包含所有架构)
   - 或者下载特定架构的静态库版本

3. 如果下载的是AAR文件，需要提取其中的二进制文件：
   - 将AAR文件重命名为ZIP并解压
   - 在 `jni/` 目录下找到对应架构的 `libffmpeg.so` 文件
   - 重命名为 `ffmpeg`

### 方法2: 使用预编译的静态二进制

1. 访问 [FFmpeg官方下载页面](https://ffmpeg.org/download.html)
2. 查找Android平台的静态编译版本
3. 下载对应您目标架构的版本（arm64-v8a推荐）

## 步骤2: 放置二进制文件

将下载的FFmpeg二进制文件（命名为 `ffmpeg`，无扩展名）放置到以下目录：

```
app/src/main/assets/ffmpeg/ffmpeg
```

目录结构应该是：
```
app/
  src/
    main/
      assets/
        ffmpeg/
          ffmpeg          <- FFmpeg二进制文件（无扩展名）
          README.md       <- 说明文件（已存在）
```

## 步骤3: 验证文件

确保：
- 文件名为 `ffmpeg`（无扩展名）
- 文件具有可执行权限（在Linux/Mac上：`chmod +x ffmpeg`）
- 文件大小合理（通常几MB到几十MB）

## 支持的架构

应用支持以下Android架构：
- **arm64-v8a** (推荐，现代Android设备)
- **armeabi-v7a** (较老的ARM设备)
- **x86** (模拟器)
- **x86_64** (64位模拟器)

## 转换命令说明

应用使用以下FFmpeg命令进行转换：

```bash
ffmpeg -i input.mp4 -vn -ar 44100 -ab 192k -f mp3 -y output.mp3
```

参数说明：
- `-i input.mp4`: 输入MP4文件
- `-vn`: 移除视频流，只保留音频
- `-ar 44100`: 设置采样率为44.1kHz（CD标准）
- `-ab 192k`: 设置比特率为192kbps
- `-f mp3`: 指定输出格式为MP3
- `-y`: 自动覆盖已存在的输出文件

## 故障排除

### 问题1: 应用提示"未找到FFmpeg二进制文件"

**解决方案：**
- 检查文件是否存在于 `app/src/main/assets/ffmpeg/ffmpeg`
- 确认文件名正确（无扩展名）
- 重新构建应用

### 问题2: 转换失败

**可能原因：**
- FFmpeg二进制文件与设备架构不匹配
- 输入文件损坏或格式不支持
- 存储空间不足

**解决方案：**
- 确保使用正确的架构版本
- 检查输入文件是否有效
- 检查设备存储空间

### 问题3: 权限问题

应用需要以下权限：
- `READ_EXTERNAL_STORAGE` (Android 12及以下)
- `READ_MEDIA_VIDEO` (Android 13+)

首次运行时，应用会请求这些权限。

## 输出文件位置

转换后的MP3文件将保存在：
- 如果能够访问源文件目录：与源MP4文件同一目录
- 否则：应用的下载目录 (`/Android/data/com.example.myapplication/files/Download/`)

## 注意事项

1. **文件大小**: FFmpeg二进制文件较大，会增加APK大小
2. **首次运行**: 首次运行时，应用会将FFmpeg从assets复制到私有目录，可能需要几秒钟
3. **转换时间**: 转换时间取决于视频文件大小和设备性能
4. **存储空间**: 确保有足够的存储空间存放转换后的MP3文件
