# 本地 CMD

MSSH 支持本地 CMD 终端，用于在 Android 手机本机执行系统命令。

## 执行方式

本地命令通过 Android 系统 Shell 执行：

```text
/system/bin/sh -c 用户输入命令
```

示例：

```text
pwd
ls
ping -c 4 8.8.8.8
```

## 默认工作目录

默认工作目录是 App 内部文件目录：

```text
context.filesDir
```

在大多数设备上对应：

```text
/data/user/0/com.mssh/files
```

普通文件管理器通常无法直接访问该目录。

## cd 行为

本地 CMD 支持简单的 `cd` 命令，并在 App 内保存当前工作目录状态。

示例：

```text
cd ..
pwd
```

## 权限限制

本地 CMD 只能以当前 App 进程权限执行命令，不能绕过 Android 权限和 SELinux 限制。

因此：

- 不能访问无权限的系统目录。
- 不能执行需要 root 权限的命令。
- `ping` 是否可用取决于设备系统和网络权限。

## 停止命令

如果命令长时间运行，可以使用快捷键栏里的 `^C` 停止当前本地进程。
