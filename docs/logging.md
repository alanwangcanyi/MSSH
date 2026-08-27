# Shell 输出日志

终端页面右上角提供 `log` 按钮。

## 开关行为

- 第一次点击开始记录 Shell 输出。
- 第二次点击停止记录并关闭文件。
- 按钮会显示当前是否正在记录。

## 目录

日志保存在手机公共下载目录：

```text
/sdcard/Download/mssh_log
```

Android 10 及以上通过系统 MediaStore 写入 `Download/mssh_log`。Android 9 及以下通过外部存储文件路径写入，并需要外部存储写入权限。

## 文件命名

格式：

```text
yyyyMMdd-HHmmss-XXXXXXXX.log
```

`XXXXXXXX` 是 8 位大写字母和数字随机码。

示例：

```text
20260602-165530-A8K3P9QZ.log
```

## 记录内容

默认只记录 Shell 输出，不记录用户输入。
