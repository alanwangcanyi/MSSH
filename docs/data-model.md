# 数据模型

MSSH 使用 Android SQLite，并通过 `SQLiteOpenHelper` 保存 App 自定义结构化数据。

## 表结构

## ssh_hosts

保存 SSH 主机配置。

- `id`：自增主键。
- `name`：展示名称。
- `hostname`：主机名或 IP 地址。
- `protocol`：连接协议，当前支持 `SSH` 和 `SFTP`，默认 `SSH`。
- `port`：SSH 端口。
- `username`：登录用户名。
- `authType`：认证类型，首版为 `PASSWORD`。
- `savePassword`：是否保存密码，必须由用户显式开启。
- `passwordSecret`：使用 Android Keystore 加密后的密码密文，可为空。
- `createdAt`：创建时间，毫秒时间戳。
- `updatedAt`：更新时间，毫秒时间戳。

密码不以明文保存。保存密码时，先使用 Android Keystore 生成的 AES 密钥加密，再把密文写入 SQLite。

保存密码必须由用户显式开启。关闭保存密码时，`savePassword` 写为 `0`，`passwordSecret` 写为空字符串。旧版本数据库升级到新版本时，已有 `passwordSecret` 的记录会自动把 `savePassword` 标记为 `1`，避免用户原有保存密码能力丢失。

## connection_history

保存连接尝试和结果。

- `id`：自增主键。
- `hostId`：可选主机引用。
- `hostname`：连接使用的主机名。
- `username`：连接使用的用户名。
- `connectedAt`：连接时间，毫秒时间戳。
- `status`：`CONNECTED`、`FAILED` 或 `DISCONNECTED`。

## ssh_logs

保存 Shell 输出日志文件的元数据。

- `id`：自增主键。
- `hostId`：可选主机引用。
- `fileName`：日志文件名。
- `absolutePath`：App 内部文件绝对路径。
- `startedAt`：开始记录时间，毫秒时间戳。
- `endedAt`：结束记录时间，可为空。

## terminal_preferences

保存终端显示偏好。

- `id`：固定单例 id。
- `fontSizeSp`：终端字体大小。
- `darkTheme`：是否使用深色主题。
- `showShortcutBar`：是否显示快捷键栏。
