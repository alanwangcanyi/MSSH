# 架构说明

MSSH 当前采用单 Android App 模块结构，并在包名层面划分职责。首版先保持工程简单，不提前拆成多个 Gradle 模块。

## 包结构

- `com.mssh.app`：App 级初始化和依赖容器。
- `com.mssh.data`：通过 `SQLiteOpenHelper` 实现 SQLite 持久化。
- `com.mssh.domain`：纯业务模型和用例。
- `com.mssh.localcmd`：Android 本地命令执行控制器。
- `com.mssh.ssh`：SSH 客户端和会话接口及实现。
- `com.mssh.sftp`：SFTP 客户端、远程目录读取和文件列表模型。
- `com.mssh.terminal`：终端状态、输入、输出缓冲和日志转发。
- `com.mssh.logging`：Shell 输出日志文件。
- `com.mssh.ui`：手机竖屏 UI。

## 依赖方向

UI 依赖业务用例。业务层依赖仓库接口。数据、SSH 和日志包提供具体实现。

终端控制器负责协调 SSH Shell 输出和可选日志记录。日志模块不直接持有或管理 SSH 会话。

## 首版实现边界

首版提供可运行的 App 骨架和本地持久化能力。SSH 行为通过可替换的客户端和会话抽象暴露，后续可以替换或增强真实 SSH 协议库，而不需要重写 UI 和存储层。
