# 鲸娘桌宠 v2（自己编译版）

浮在屏幕上的**像素鲸娘**（22 张 GIF 动作），把「桌宠」和「DSH 语音入口」合成一个东西。

素材是网上公开的「鲸娘像素表情集」（BSD-3-Clause，作者 lhh010），许可声明见 `THIRD-PARTY-NOTICES.md`。

## 电脑上怎么跑起来（GitHub 云编译，4GB 电脑也能用）

1. 打开 github.com → 右上角 `+` → `New repository`，名字随便（如 `dsh-pet`）→ Create
2. 把 **这个文件夹里的所有内容**（含 `.github`）拖到 GitHub 网页的上传框里 → Commit changes
   - 注意别套一层文件夹：`app`、`build.gradle.kts`、`settings.gradle.kts`、`.github` 要在仓库根目录
3. 仓库顶部 `Actions` → 左栏 `Build DSH Pet APK` → `Run workflow`
4. 3–5 分钟后进这次运行 → 底部 `Artifacts` → 下载 `dsh-pet-debug-apk`（zip 里有 `app-debug.apk`）
5. 传到手机安装。**如果提示签名冲突，先卸载手机上的旧版「DSH 桌宠」再装**（悬浮窗权限要重新授予一次）

想用 Android Studio 也行：`File → Open` 选这个文件夹 → 插线 ▶ Run。

## 装好后

打开「鲸娘桌宠」→ ① 悬浮窗权限 ② 麦克风权限 ③ 启动桌宠。

## 手势

| 操作 | 效果 |
|---|---|
| 戳左脸 / 右脸 | 被戳的反应（12% 冒爱心） |
| 1 秒内连点 3 下 | 挠痒痒 |
| 双击 | 打开 DSH |
| 按住 0.55 秒 | 说话，识别完当任务发给 DSH |
| 拖动 | 换位置（位置会记住，下次原位出现） |
| 5 分钟没互动 | 呼呼大睡；戳一下会醒 |

## 换素材（不用重新编译）

把 GIF 放进这个目录，文件名 = 动作键，重启桌宠生效：

```
Android/data/com.dsh.pet/files/pet/
```

App 里有「导出内置素材」按钮，会把 22 张内置 GIF 拷到上面这个目录，照着文件名替换即可。

可用动作键：`idle` `idle-look` `idle-spout` `thinking` `typing` `music` `conducting`
`building` `error` `happy` `notification` `compacting` `carrying` `sleeping` `waking`
`poke-left` `poke-right` `tickle` `drag` `swim` `smile-hearts` `thumbs-up`

## 可能会遇到的小问题

| 现象 | 原因 / 解决 |
|---|---|
| 编译报 `compileSdk 35 not found` | 把 `app/build.gradle.kts` 里 `compileSdk = 35` 改成 34 |
| 桌宠消失 | 被省电杀了：设置里给「鲸娘桌宠」开「允许后台活动」 |
| 语音发过去空白 | DSH 对 `ACTION_SEND` 的处理可能不同，告诉我，我改成直接填 DSH 输入框 |
| 鲸娘糊 | 只在小尺寸下抽帧，属正常；想要更清晰可以改 `PetService.kt` 的 `PET_DP` 调大 |

## 代码结构

```
app/src/main/java/com/dsh/pet/
  MainActivity.kt   权限引导 + 启动/停止/导出素材
  PetService.kt     GIF 动画状态机、戳脸/双击/长按/拖动、前台通知
  VoiceActivity.kt  语音识别 → ACTION_SEND 给 DSH
app/src/main/res/raw/   22 张内置 GIF（idle.gif、poke_left.gif …）
```
