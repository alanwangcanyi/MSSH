# SSH 流程

## 连接流程

1. 用户选择已保存主机，或输入快速连接信息。
2. UI 调用连接用例。
3. 用例校验主机名、端口、用户名和认证信息。
4. JSch SSH 客户端创建 SSH 会话。
5. 会话申请 PTY Shell。
6. Shell 输出转发给终端控制器。
7. 终端控制器更新 UI 状态，并在开启日志时把输出转发给日志记录器。

## 会话生命周期

- `Idle`：没有活动连接。
- `Connecting`：正在进行 SSH 握手和认证。
- `Connected`：交互式 Shell 已连接。
- `Disconnecting`：会话正在关闭。
- `Disconnected`：Shell 已关闭。
- `Failed`：连接或认证失败。

## PTY 默认值

- 终端类型：`xterm-256color`。
- 尺寸：根据竖屏终端视口更新。
- 编码：UTF-8。

## 安全说明

默认不把用户输入写入 Shell 输出日志。

保存的 SSH 密码使用 Android Keystore 加密后写入 SQLite。MSSH 不使用 `sshpass`，也不依赖系统 `ssh` 命令。

SSH 主机密钥保存到 App 私有 `known_hosts` 文件。当前版本首次连接未知主机会自动接受并写入，后续连接使用该文件校验；后续应增加指纹确认弹窗。

SSH Shell 建立后，终端控制器会把当前 UI 测得的列数和行数同步到 PTY。布局变化时只在行列数真正改变后再次同步，减少无意义 resize 对交互式程序的影响。
