# MSSH

MSSH 是一个面向手机竖屏使用的 Android SSH 命令行应用。目标是提供主机保存、交互式 SSH Shell、手机端终端输入，以及可开关的 Shell 输出日志记录能力。

## 项目规则

1. 修改任何代码、配置、资源或文档前，必须先更新本 README。
2. README 和 `docs` 下的所有项目文档必须使用中文。
3. App 自定义保存的结构化数据必须使用打包在 App 内的轻量数据库。本项目使用 Android SQLite，并通过 `SQLiteOpenHelper` 管理。
4. App UI 以手机竖屏为主，默认使用竖屏；主界面提供横版按钮，方便平板场景手动切换。

## 文档位置

- `docs/architecture.md`：代码架构和各包职责。
- `docs/data-model.md`：SQLite 表、仓库和持久化规则。
- `docs/ssh-flow.md`：SSH 连接、认证、PTY 和 Shell 生命周期。
- `docs/sftp-flow.md`：SFTP 连接、目录浏览和会话生命周期。
- `docs/ui-flow.md`：手机竖屏 UI 流程和页面职责。
- `docs/logging.md`：Shell 输出日志的开关、目录和命名规则。
- `docs/local-cmd.md`：本地 CMD 命令执行能力、限制和工作目录规则。
- `archive/README.md`：构建产物归档目录说明。

## 代码架构

- `app/src/main/java/com/mssh/app`：App 初始化和依赖容器。
- `app/src/main/java/com/mssh/data`：SQLite 数据库辅助类、模型和仓库。
- `app/src/main/java/com/mssh/domain`：业务模型和用例。
- `app/src/main/java/com/mssh/ssh`：SSH 连接和会话抽象。
- `app/src/main/java/com/mssh/sftp`：SFTP 连接、目录读取和文件列表模型。
- `app/src/main/java/com/mssh/localcmd`：Android 本地命令执行控制器。
- `app/src/main/java/com/mssh/terminal`：终端控制器、输出缓冲、输入处理和日志桥接。
- `app/src/main/java/com/mssh/logging`：Shell 输出日志文件创建、命名和写入生命周期。
- `app/src/main/java/com/mssh/ui`：竖屏 UI 页面、组件和主题。
- `app/src/main/res`：Android 资源，包括 MSSH 启动图标。

## 版本管理

项目使用 Git 管理。GitHub 仓库只保存可维护源码、Gradle 配置、Android 资源和中文文档。

不上传以下本地产物：

- `.gradle/`、`build/`、`app/build/` 等构建缓存。
- `gradle-*.zip` 等本地下载包。
- `archive/` 下的 APK 归档产物，保留 `archive/README.md` 说明文件。
- `.DS_Store`、日志、临时文件和本机 IDE 私有配置。

## 数据存储

MSSH 使用 Android SQLite 保存 App 自定义结构化数据：

- 已保存的 SSH 主机。
- 已保存连接的协议类型，例如 `SSH` 或 `SFTP`。
- 已保存的 SSH 密码密文。
- 是否保存密码的显式开关。
- 连接历史。
- SSH 日志元数据。
- 用户终端偏好。

SSH 密码只有在用户打开“保存密码”开关后，才使用 Android Keystore 生成的 AES 密钥加密后保存到 SQLite；关闭开关时不写入密码密文。

Shell 输出日志以文件形式保存到手机公共下载目录的 `mssh_log` 文件夹中，日志元数据可以写入 SQLite 进行索引。

## Shell 输出日志

终端页面右上角有一个 `log` 按钮。

- 第一次点击 `log`：开始记录 Shell 输出。
- 再次点击 `log`：停止记录并关闭日志文件。
- 日志目录：`/sdcard/Download/mssh_log`。
- 文件名规则：`yyyyMMdd-HHmmss-XXXXXXXX.log`，其中 `XXXXXXXX` 是 8 位大写字母和数字随机码。
- 默认只记录 Shell 输出，不记录用户输入，避免把密码、token 等敏感信息写入日志。

## App 标识

- App 名称：`MSSH`。
- 启动图标：黑色小显示器背景，中间是淡黄色 `MSSH` 字样。
- 图标字体风格：圆润、稍微可爱一点。

## 首版功能范围

1. SSH 主机列表。
2. 新增、编辑、删除 SSH 主机。
3. 基于密码的 SSH Shell 会话。
4. 竖屏终端页面。
5. 终端页面右上角 `log` 开关。
6. SQLite 持久化。
7. MSSH 启动图标。
8. 本地 CMD 终端，可在手机 App 沙箱内运行 `pwd`、`ls`、`ping` 等 Android 系统命令。
9. SSH 终端和本地 CMD 终端发送命令后，命令输入框必须继续保持焦点，方便连续输入。
10. 已保存连接支持保存 SSH 密码，密码默认不可见，可通过眼睛斜线小图标切换可见状态；密码可见时显示无斜线眼睛小图标。
11. 新增连接默认协议为 `SSH`，并保留自定义端口；主机列表显示协议。
12. SFTP 代码和文档暂时保留，但入口和连接功能当前隐藏。
13. 终端输出区域不得直接显示 ANSI 控制序列、颜色码、括号粘贴模式控制码等乱码。
14. SSH 终端必须使用有状态的 VT 屏幕解析，支持清屏、光标移动、擦除行、替代屏幕等基础能力，尽量让 `ll`、`vi`、`vim`、`nano` 等命令接近电脑终端体验。
15. SSH 终端普通 Shell 模式下，输入内容必须停留在底部输入框，点击 `send` 或软键盘发送后才提交到远端；进入 `vi`、`vim`、`nano` 等替代屏幕程序后，输入框才作为原始键盘输入桥接。
16. 新增/编辑连接表单中，每个配置项必须有独立标题，不能只把标题放在输入框 hint 里。
17. 主界面显示横版/竖版切换按钮，供平板场景使用；该按钮只在主界面显示，终端和其他页面不显示。
18. SSH 终端的快捷键栏和物理键盘 Tab/左右键必须在普通 Shell 与替代屏幕中都生效；普通 Shell 中若输入框已有未发送文本，先同步文本再发送快捷键。
19. 新增/编辑连接表单不能使用系统默认白底配浅色文字；表单背景使用灰色或深色，字段标题颜色要与背景有足够色差。
20. SSH 终端 PTY 尺寸必须根据当前终端显示区域和字体动态计算，并在布局变化时同步到远端。
21. 保存密码必须有显式开关；关闭保存密码时，连接配置不写入密码密文。
22. SSH 主机密钥必须写入 App 私有 `known_hosts` 文件，不能长期使用完全跳过主机密钥检查的配置。
23. 即使 SFTP 入口暂时隐藏，SFTP 连接代码也必须沿用 SSH 的主机密钥校验规则，不能保留跳过校验的实现。
24. 终端 UI 的可见行列数由显示区域、内边距和等宽字体测量得出；同一尺寸不重复向远端发送 resize。
25. 上传 GitHub 时只提交源码、配置和中文文档；本地 Gradle 缓存、Gradle zip、APK 归档产物、系统文件和临时构建目录必须忽略。

## SSH 实现

MSSH 使用 JSch 在 App 内直接建立 SSH 和 SFTP 连接，不依赖 Android 系统中的 `ssh`、`sftp` 或 `sshpass` 命令。

SSH 主机密钥保存到 App 私有文件 `known_hosts`。首次连接未知主机时当前版本自动接受并写入，后续连接使用该文件进行校验；后续应优化为弹窗展示指纹后由用户确认。

## 构建要求

- JDK 17 或更高版本。
- Android SDK，包含 compile SDK 34。
- Gradle 或 Android Studio 的 Gradle 支持。
- 首次构建需要访问 Maven 仓库，用于下载 Android Gradle Plugin 和 JSch 依赖。
