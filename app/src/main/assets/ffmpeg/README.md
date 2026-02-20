# FFmpeg 二进制文件存放目录

请将FFmpeg二进制文件放在此目录下，文件名为 `ffmpeg`（无扩展名）。

## 如何获取FFmpeg二进制文件

1. 访问 https://github.com/tanersener/ffmpeg-kit/releases
2. 下载适用于Android的FFmpeg静态库版本
3. 解压后找到 `ffmpeg` 二进制文件
4. 将 `ffmpeg` 文件复制到此目录（`app/src/main/assets/ffmpeg/ffmpeg`）

## 注意事项

- 确保下载的FFmpeg版本与您的目标Android架构匹配（arm64-v8a, armeabi-v7a, x86, x86_64）
- 二进制文件需要是可执行格式
- 文件大小通常在几MB到几十MB之间

## 支持的架构

- arm64-v8a (推荐，现代Android设备)
- armeabi-v7a (较老的ARM设备)
- x86 (模拟器)
- x86_64 (64位模拟器)
