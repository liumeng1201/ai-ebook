# Ebook Android App — 版本管理与更新系统 实施计划

## 一、目标

1. **统一内容同步** — 将所有内容构建脚本整合为单一 `sync_assets.py`
2. **版本号体系** — App 版本用 Git 提交计数，内容版本用子模块 SHA
3. **GitHub 双轨分发** — App 更新（APK）+ 内容更新（Zip），均通过 GitHub Release 自动发布
4. **Android 更新检查** — 设置页展示版本信息 + 手动检查 + 启动时静默检查
5. **CI/CD 自动化** — 合并到 master 即触发构建与发布

---

## 二、version.json 设计

构建时由 `sync_assets.py` 自动生成到 `ebook/app/src/main/assets/version.json`：

```json
{
  "app_version_code": 42,
  "app_version_name": "1.0.42",
  "app_commit_sha": "abc1234",
  "books": [
    {
      "id": "claude-code",
      "name": "Claude Code 指南",
      "json_file": "claude-code.json",
      "source_repo": "ai-coding-guide",
      "content_sha": "d6cefce"
    },
    {
      "id": "codex",
      "name": "Codex 指南",
      "json_file": "codex.json",
      "source_repo": "ai-coding-guide",
      "content_sha": "d6cefce"
    },
    {
      "id": "easy-vibe",
      "name": "小白代码",
      "json_file": "easy-vibe.json",
      "source_repo": "easy-vibe",
      "content_sha": "0123354"
    },
    {
      "id": "vibe-vibe",
      "name": "Vibe 编码",
      "json_file": "vibe-vibe.json",
      "source_repo": "vibe-vibe",
      "content_sha": "f2e121d"
    }
  ],
  "generated_at": "2026-07-07T12:00:00Z"
}
```

**App 版本号获取方式：**
- `version_code` = `git rev-list --count HEAD`（总提交次数，纯数字递增）
- `version_name` = `1.0.<version_code>`（如 `1.0.42`）
- `app_commit_sha` = `git rev-parse --short HEAD`

**内容版本号获取方式：**
- 各书 `content_sha` = `git -C <submodule_path> rev-parse --short HEAD`

---

## 三、sync_assets.py — 统一内容同步脚本

### 3.1 位置

`data-preprocess/sync_assets.py`

### 3.2 职责

1. 自动检测项目根目录（不硬编码路径，Windows / Linux 通用）
2. 收集版本信息（git 命令）
3. 同步 4 本电子书内容到 `ebook/app/src/main/assets/`
4. 生成 `version.json`
5. 每本书独立清理自己的子目录，互不干扰

### 3.3 执行流程（伪代码）

```
main()
├─ detect_base_dir()          → 从脚本位置推算项目根目录
│
├─ collect_versions()
│   ├─ 运行 git rev-list --count HEAD
│   ├─ 运行 git rev-parse --short HEAD
│   ├─ 运行各子模块 git rev-parse --short HEAD
│   └─ 返回 VersionInfo 对象
│
├─ sync_claude_code()
│   ├─ clean_dir("claude-code")          # 仅清理自己的目录
│   ├─ 遍历 hardcoded CLAUDE_CODE_PARTS
│   │   ├─ 拷贝 .md 从 ai-coding-guide/claude-code/
│   │   └─ 记录到 JSON 结构
│   ├─ 拷贝 assets/ 图片目录
│   └─ 写入 claude-code.json
│
├─ sync_codex()
│   ├─ clean_dir("codex")                # 仅清理自己的目录
│   ├─ 遍历 hardcoded CODEX_PARTS
│   │   ├─ 拷贝 .md 从 ai-coding-guide/codex/
│   │   └─ 记录到 JSON 结构
│   ├─ 拷贝 assets/ 图片目录
│   └─ 写入 codex.json
│
├─ sync_easy_vibe()
│   ├─ clean_dir("easy-vibe")            # 仅清理自己的目录
│   ├─ 读取 data-preprocess/easy-vibe.json
│   ├─ 拷贝 JSON → assets/easy-vibe.json
│   ├─ 遍历 herf → 拷贝 .md + images/
│   └─ 统计
│
├─ sync_vibe_vibe()
│   ├─ clean_dir("vibe-vibe")            # 仅清理自己的目录
│   ├─ 读取 data-preprocess/vibe-vibe.json
│   ├─ 拷贝 JSON → assets/vibe-vibe.json
│   ├─ 遍历 herf → 拷贝 .md + images/
│   └─ 统计
│
├─ generate_version_json()               # 写入 version.json
│
└─ print_summary()                       # 汇总统计
```

### 3.4 clean_dir() 实现要点

```python
def clean_dir(dirname):
    """仅删除 assets/<dirname>/ 下的文件，保留目录结构再重建"""
    target = os.path.join(ASSETS_DIR, dirname)
    if os.path.exists(target):
        shutil.rmtree(target)
    os.makedirs(target)
```

**不再清理整个 assets 目录**。仅针对每个 book 自己的子目录清理，不影响其他 book 的文件。

### 3.5 硬编码的目录结构

claude-code 和 codex 的章节结构 **继续硬编码在脚本中**（从 `build_ai_guide_assets.py` 迁移过来的 `CLAUDE_CODE_PARTS` 和 `CODEX_PARTS`）。这两个 JSON 不是从外部文件读取，而是由 Python 代码动态构建。

### 3.6 easy-vibe / vibe-vibe JSON 处理

JSON 源文件（`data-preprocess/easy-vibe.json`、`vibe-vibe.json`）**保持不变，不修改原始 JSON**。`update_herf.py` 和 `fix_herf_relative.py` 的路径转换逻辑不再需要（源 JSON 已维护为相对路径形态）。

### 3.7 路径兼容性

```python
import os, sys

def get_base_dir():
    """自动获取项目根目录（脚本所在仓库的根）"""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    return os.path.dirname(script_dir)  # data-preprocess/ 的上一级

BASE_DIR = get_base_dir()
ASSETS_DIR = os.path.join(BASE_DIR, "ebook", "app", "src", "main", "assets")
```

所有路径操作使用 `os.path.join()` + `os.sep`，不硬编码 `\` 或 `/`。

---

## 四、文件清理计划

### 4.1 删除的文件

| 文件 | 原因 |
|------|------|
| `data-preprocess/build_ai_guide_assets.py` | 功能合并到 sync_assets.py |
| `data-preprocess/build_ebook_assets.py` | 功能合并到 sync_assets.py |
| `data-preprocess/update_herf.py` | 已完成使命，不再需要 |
| `data-preprocess/fix_herf_relative.py` | 已完成使命，不再需要 |
| `data-preprocess/easy-vibe.html` | 历史遗留，不再使用 |
| `data-preprocess/vibe-vibe.html` | 历史遗留，不再使用 |
| `data-preprocess/easy-vibe.backup.json` | 历史备份，不再需要 |
| `data-preprocess/vibe-vibe.backup.json` | 历史备份，不再需要 |

### 4.2 保留的文件

| 文件 | 原因 |
|------|------|
| `data-preprocess/easy-vibe.json` | easy-vibe 目录源头 |
| `data-preprocess/vibe-vibe.json` | vibe-vibe 目录源头 |
| `data-preprocess/sync_assets.py` | 🆕 统一构建脚本 |

---

## 五、GitHub Actions

### 5.1 内容自动发布 (content-release.yml)

```yaml
name: Content Release

on:
  push:
    branches: [master]
    paths:
      - 'data-preprocess/easy-vibe.json'
      - 'data-preprocess/vibe-vibe.json'
      - 'data-preprocess/sync_assets.py'
      - 'ai-coding-guide/**'
      - 'easy-vibe/**'
      - 'vibe-vibe/**'
      - 'CodexGuide/**'
      - '.github/workflows/content-release.yml'
  workflow_dispatch:  # 也支持手动触发

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive
          fetch-depth: 0  # 需要完整 git 历史来计数

      - name: Sync assets
        run: python data-preprocess/sync_assets.py

      - name: Package content bundle
        run: |
          cd ebook/app/src/main/assets
          zip -r content-bundle.zip . -x ".gitkeep"
          mv content-bundle.zip $GITHUB_WORKSPACE/

      - name: Create content release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: content-${{ github.run_number }}
          name: "📖 内容更新 #${{ github.run_number }}"
          body: |
            ## 变更详情
            ${{ github.event.head_commit.message }}
            
            ## 内容版本
            See `version.json` in the bundle.
          files: content-bundle.zip
```

### 5.2 App 自动发布 (app-release.yml)

```yaml
name: App Release

on:
  push:
    branches: [master]
    paths:
      - 'ebook/app/src/**'
      - 'ebook/app/build.gradle'
      - 'ebook/build.gradle'
      - 'ebook/settings.gradle'
      - 'ebook/gradle/**'
      - '.github/workflows/app-release.yml'
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          submodules: recursive
          fetch-depth: 0

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Sync assets
        run: python data-preprocess/sync_assets.py

      - name: Get version
        id: version
        run: |
          VERSION_CODE=$(git rev-list --count HEAD)
          VERSION_NAME="1.0.$VERSION_CODE"
          echo "version_code=$VERSION_CODE" >> $GITHUB_OUTPUT
          echo "version_name=$VERSION_NAME" >> $GITHUB_OUTPUT

      - name: Build APK
        run: |
          cd ebook
          ./gradlew assembleRelease \
            -PversionCode=${{ steps.version.outputs.version_code }} \
            -PversionName=${{ steps.version.outputs.version_name }}

      - name: Create app release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: v${{ steps.version.outputs.version_name }}
          name: "📱 App v${{ steps.version.outputs.version_name }}"
          body: |
            ## 此版本包含
            
            - 最新内容更新（见 content-${{ github.run_number }}）
            - App 代码变更
            
            ## 安装
            1. 下载 APK
            2. 允许安装未知来源应用
            3. 安装后打开即可
          files: ebook/app/build/outputs/apk/release/app-release.apk
```

### 5.3 Tag 命名空间隔离

| 类型 | Tag 格式 | 示例 |
|------|---------|------|
| App Release | `v1.0.<N>` | `v1.0.42` |
| Content Release | `content-<N>` | `content-13` |

两者使用不同的前缀，不会冲突。

---

## 六、Android 端改动

### 6.1 AndroidManifest.xml

```xml
<!-- 新增权限 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />

<!-- 新增 FileProvider（用于安装 APK） -->
<application>
    ...
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.fileprovider"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_paths" />
    </provider>
</application>
```

### 6.2 res/xml/file_paths.xml（新增）

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="apk" path="." />
</paths>
```

### 6.3 build.gradle — 动态版本号

```groovy
android {
    defaultConfig {
        applicationId 'com.ebook.reader'
        minSdk 29
        targetSdk 34
        versionCode project.hasProperty('versionCode') 
            ? project.versionCode.toInteger() 
            : 1
        versionName project.hasProperty('versionName') 
            ? project.versionName 
            : '1.0-dev'
    }
}
```

本地构建时 `versionCode=1, versionName='1.0-dev'`，CI 构建时通过 `-P` 传入实际版本号。

### 6.4 VersionInfo.java（新增）

```java
package com.ebook.reader.util;

/**
 * version.json 的数据模型
 */
public class VersionInfo {
    public int appVersionCode;
    public String appVersionName;
    public String appCommitSha;
    public List<BookVersion> books;
    
    public static class BookVersion {
        public String id;
        public String name;
        public String jsonFile;
        public String sourceRepo;
        public String contentSha;
    }
}
```

### 6.5 UpdateManager.java（新增）

**位置：** `util/UpdateManager.java`

**职责：**
1. 从 bundled assets 读取 `version.json` → 解析本地版本
2. 请求 GitHub Releases API 获取最新 Release
3. 比对版本号，判断是否有更新
4. 下载 content zip / APK
5. 解压到 staging → 原子 rename 到 `content-current/`
6. 通知栏显示下载/解压进度
7. 支持 WiFi + 移动网络（不做网络类型限制）

**核心方法签名：**

```java
public class UpdateManager {
    
    // 读取本地版本信息
    public static VersionInfo getLocalVersion(Context context);
    
    // 静默检查内容更新（后台，不弹窗）
    public static void checkContentUpdate(Context context);
    
    // 手动检查更新（弹窗提示结果）
    public static void checkAllUpdates(Context context, UpdateCallback callback);
    
    // 下载内容包
    private static void downloadContentBundle(Context context, String url, int version);
    
    // 安装内容包（staging → atomic rename）
    private static void installContentBundle(Context context);
    
    // 下载并安装 APK
    private static void downloadAndInstallApk(Context context, String url);
    
    // 打开内容文件：优先本地下载内容，fallback 到 bundled assets
    public static InputStream openContent(Context context, String assetPath);
    
    // 获取内容根目录
    public static File getContentDir(Context context);
    
    // 上次检查时间（防频繁请求 API）
    private static boolean shouldThrottle(Context context);
    
    // 回调接口
    public interface UpdateCallback {
        void onUpdateAvailable(UpdateType type, String version, String releaseNotes);
        void onNoUpdate();
        void onError(String message);
    }
    
    public enum UpdateType { APP, CONTENT, BOTH }
}
```

**关键实现细节：**

- **GitHub API 请求**：`https://api.github.com/repos/liumeng1201/ai-ebook/releases`
- **内容更新检测**：比对本地 `books[].content_sha` 与最新 Release tag_name
- **App 更新检测**：比对本地 `app_version_code` 与最新 `v1.0.N` tag 中的 N
- **下载**：直接下载 zip / APK 二进制流，通知栏展示进度
- **原子安装**：内容先解压到 `content-staging/`，完成后 `rename` 到 `content-current/`
- **限流**：距上次检查 < 30 分钟则跳过（仅针对静默检查）
- **无网络类型限制**：不检查 ConnectivityManager 的网络类型

### 6.6 LocalFileSchemeHandler.java（新增）

**位置：** `util/LocalFileSchemeHandler.java`

**职责：** Markwon 图片加载时，优先从本地下载内容读取图片，fallback 到 assets。

```java
public class LocalFileSchemeHandler extends SchemeHandler {
    private final Context context;
    
    @Override
    public void handle(@NonNull Raw raw, @NonNull Response response) {
        // 1. 先尝试从 content-current/ 读取
        File localFile = new File(UpdateManager.getContentDir(context), rawUri);
        if (localFile.exists()) {
            response.initWithStream(new FileInputStream(localFile));
            return;
        }
        // 2. Fallback 到 assets
        try {
            response.initWithStream(context.getAssets().open(rawUri));
        } catch (IOException e) {
            // not found
        }
    }
}
```

### 6.7 ReaderActivity.java（修改）

**改动点：**

1. Markwon 初始化时使用 `LocalFileSchemeHandler` 替代 `FileSchemeHandler.createWithAssets()`
2. `readAssetContent()` → 改用 `UpdateManager.openContent()`

```java
// 原代码
markwon = Markwon.builder(this)
    .usePlugin(ImagesPlugin.create(plugin -> {
        plugin.addSchemeHandler(
            FileSchemeHandler.createWithAssets(ReaderActivity.this));
    }))
    ...

// 改为
markwon = Markwon.builder(this)
    .usePlugin(ImagesPlugin.create(plugin -> {
        plugin.addSchemeHandler(
            new LocalFileSchemeHandler(ReaderActivity.this));
    }))
    ...

// 读取内容
// 原: InputStream is = getAssets().open(assetPath);
// 改为: InputStream is = UpdateManager.openContent(this, assetPath);
```

### 6.8 BookListActivity.java（修改）

**改动点：**

`loadBooks()` 中扫描 JSON 文件时，同时扫描 bundled assets 和本地下载内容中的 JSON 文件，去重。

### 6.9 MyApp.java（修改）

**改动点：** `onCreate()` 中添加后台静默检查：

```java
@Override
public void onCreate() {
    super.onCreate();
    ThemeHelper.applyTheme(this);
    UpdateManager.checkContentUpdate(this);  // 后台静默
}
```

### 6.10 SettingsActivity.java（修改）

**改动点：** 重新设计设置页：

```
┌──────────────────────────────┐
│  ← 设置                      │
├──────────────────────────────┤
│  应用版本                     │
│  1.0.42  (abc1234)           │
│                              │
│  内容版本                     │
│  Claude Code 指南  d6cefce   │
│  Codex 指南       d6cefce   │
│  小白代码         0123354    │
│  Vibe 编码        f2e121d    │
├──────────────────────────────┤
│                              │
│  [  🔄  检查更新  ]           │
│  最后检查：刚才               │
│                              │
├──────────────────────────────┤
│  主题                         │
│  ○ 跟随系统                   │
│  ○ 浅色                       │
│  ● 深色                       │
└──────────────────────────────┘
```

- "检查更新"按钮点击 → 调用 `UpdateManager.checkAllUpdates()` → 弹窗反馈结果
- 版本信息从 `UpdateManager.getLocalVersion()` 读取显示
- 保留现有主题切换功能

### 6.11 activity_settings.xml（修改）

新增布局：
- 应用信息卡片（`version_info_card`）→ 显示 App 版本 + 各书内容版本
- 检查更新按钮（`btn_check_update`）→ 带加载状态
- 最后检查时间文本（`tv_last_check`）

---

## 七、实施步骤

| 步骤 | 文件 | 操作 |
|:----:|------|------|
| 1 | `data-preprocess/sync_assets.py` | 🆕 新增 |
| 2 | 旧脚本 + .html + .backup.json | 🗑 删除（8 个文件） |
| 3 | `.github/workflows/content-release.yml` | 🆕 新增 |
| 4 | `.github/workflows/app-release.yml` | 🆕 新增 |
| 5 | `ebook/app/build.gradle` | 📝 修改（动态版本号） |
| 6 | `AndroidManifest.xml` | 📝 修改（权限 + FileProvider） |
| 7 | `res/xml/file_paths.xml` | 🆕 新增 |
| 8 | `util/VersionInfo.java` | 🆕 新增 |
| 9 | `util/UpdateManager.java` | 🆕 新增 |
| 10 | `util/LocalFileSchemeHandler.java` | 🆕 新增 |
| 11 | `ReaderActivity.java` | 📝 修改（图片 + 文件读取） |
| 12 | `BookListActivity.java` | 📝 修改（扫描本地内容） |
| 13 | `MyApp.java` | 📝 修改（启动检查） |
| 14 | `SettingsActivity.java` | 📝 修改（版本展示 + 手动检查） |
| 15 | `activity_settings.xml` | 📝 修改（新 UI） |
| 16 | 运行 `sync_assets.py` 验证 | 🧪 测试 |
| 17 | `git commit` + `git push` | 📦 提交并推送 |
| 18 | 观察 GitHub Actions 首次运行 | 👀 验证 CI |

---

## 八、验证清单

- [ ] `sync_assets.py` 在 Windows 上运行成功，assets 目录完整
- [ ] 删除旧脚本后 `git status` 确认只有新脚本
- [ ] `version.json` 自动生成在 assets 目录
- [ ] GitHub Actions content-release 首次触发成功
- [ ] GitHub Actions app-release 首次触发成功
- [ ] Android 编译通过
- [ ] Android 设置页正确显示 App 版本 + 内容版本
- [ ] 手动检查更新按钮可用（正向：有新版本提示 / 反向：已最新提示）
- [ ] 内容更新下载 + 解压 + 原子切换正常
- [ ] Markwon 图片渲染正常（bundled assets + 下载内容）
- [ ] 阅读进度、目录跳转功能不受影响

---

## 九、注意事项

1. **keystore.jks** 目前明文存储在仓库中，密码也明文在 build.gradle。后续可迁移到 GitHub Secrets。
2. **首次运行** GitHub Actions 时，`actions/setup-java` 需要下载 JDK 17，约耗时 30 秒。
3. **GitHub API 限流**：未认证请求 60 次/小时，已通过 `shouldThrottle()` 限流处理。
4. **APK 安装**需要用户手动授权"允许安装未知来源应用"，首次弹出系统对话框。
5. **内容包解压**使用 `staging → rename` 策略确保原子性，中断不会破坏现有内容。
