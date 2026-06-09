# Ebook Android 应用 PRD

## 项目概述

构建一个 Android MD 电子书阅读器，内置 `easy-vibe` 和 `vibe-vibe` 两本图书，支持浅色/深色模式、阅读进度记录与目录导航。

---

## 一、技术规格

| 项目 | 方案 |
|---|---|
| 开发语言 | Java |
| 最低支持 | Android 10 (API 29) |
| Gradle | 8.5，下载源 https://mirrors.cloud.tencent.com/gradle/ |
| MD 渲染引擎 | **Markwon** (`io.noties.markwon`)，Android 最成熟、性能好、支持图片/表格/代码块 |
| JSON 解析 | **Gson**，轻量高性能 |
| 列表控件 | RecyclerView |
| 主题方案 | AppCompat DayNight，跟随系统 + 手动切换 |
| 进度存储 | SharedPreferences |

---

## 二、项目结构

```
ebook/                                    # Android 项目根目录
├── build.gradle                          # 根构建脚本
├── settings.gradle
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew / gradlew.bat
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/                       # Python 脚本填充的图书资源
        │   ├── easy-vibe.json
        │   ├── vibe-vibe.json
        │   ├── easy-vibe/docs/zh-cn/...  # MD + images
        │   └── vibe-vibe/docs/...
        ├── java/com/ebook/reader/
        │   ├── BookListActivity.java      # 主界面：书本列表
        │   ├── ReaderActivity.java        # 阅读界面
        │   ├── TocActivity.java           # 目录界面
        │   ├── SettingsActivity.java      # 主题设置界面
        │   ├── ThemeHelper.java           # 主题管理工具
        │   ├── model/
        │   │   ├── BookMeta.java          # 书本元数据
        │   │   ├── TocNode.java           # 目录节点（递归）
        │   │   └── ReadingRecord.java     # 阅读记录
        │   └── util/
        │       ├── BookJsonParser.java    # JSON 解析 + TOC 树构建
        │       ├── MarkdownCleaner.java   # 去除 YAML 头部 (--- ... ---)
        │       └── ProgressStore.java     # 阅读进度持久化
        └── res/
            ├── layout/
            │   ├── activity_book_list.xml
            │   ├── activity_reader.xml
            │   ├── activity_toc.xml
            │   ├── activity_settings.xml
            │   ├── item_book.xml
            │   └── item_toc_node.xml
            ├── values/
            │   ├── strings.xml
            │   ├── colors.xml
            │   ├── themes.xml             # DayNight 主题
            │   └── styles.xml
            └── values-night/
                └── colors.xml
```

---

## 三、Python 资源制作脚本

**位置**: `data-preprocess/build_ebook_assets.py`

**功能**:
1. 读取 `easy-vibe.json` 和 `vibe-vibe.json`
2. 将两个 JSON 文件直接拷贝到 `app/src/main/assets/` 根目录
3. 递归遍历两个 JSON 中所有 `herf` 字段，收集所有 MD 文件路径
4. 将每个 MD 文件按 herf 路径在 `assets/` 下创建对应目录并拷贝
5. 对于每个 MD 所在的同级目录，检查 `images/` 子目录，若存在则一并拷贝至对应位置

**运行方式**: `cd data-preprocess && python build_ebook_assets.py`

---

## 四、核心功能设计

### 4.1 主界面 — 书本列表 (BookListActivity)

- 启动时扫描 `assets/` 根目录下所有 `*.json` 文件
- 解析每个 JSON 的 `book` 字段作为书名
- 无法解析 `book` 字段的 JSON 直接忽略
- RecyclerView 卡片列表展示，简约电子书风格
- 顶部标题栏含书名和设置按钮（跳转主题设置）

### 4.2 主题管理 (ThemeHelper / SettingsActivity)

- 三种模式存储于 SharedPreferences: `SYSTEM`（默认）/ `LIGHT` / `DARK`
- SYSTEM 使用 `AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM`
- LIGHT 使用 `MODE_NIGHT_NO`，DARK 使用 `MODE_NIGHT_YES`
- 设置页面提供三选一 RadioGroup
- 应用启动时在 `Application` 或 `BookListActivity.onCreate` 中应用主题

### 4.3 阅读界面 (ReaderActivity)

**UI 布局**:
- 顶部工具栏: 左侧返回按钮 + 中心当前章节名 + 右侧目录按钮
- 下方为 MD 渲染的滚动区域
- 简约背景，无多余装饰

**阅读逻辑**:
- Intent 接收 `bookName` 和初始 `herf`（默认该书的第一个章节）
- 启动时查询 SharedPreferences 中的阅读记录
  - 有历史记录 → 加载记录中的 herf 并恢复 scrollY
  - 无历史记录 → 从该书 JSON 的第一个叶子节点开始
- 滚动时实时记录当前进度（当前 herf + scrollY），保存至 SharedPreferences
- 点击右上角目录按钮 → 跳转 TocActivity

### 4.4 目录界面 (TocActivity)

**数据结构**:
- 解析 JSON 的 `part` 数组构建三层树结构: `part (level 0)` → `chapter (level 1)` → `section (level 2)`
- 每层通过缩进区分层级

**交互规则**:
- 默认全部展开
- 当前阅读章节文字使用高亮色（如蓝色或主题色），其余默认文字色
- 进入时自动滚动至当前阅读章节所在位置
- 点击非叶子节点（无 `herf` 或 `herf` 为 null）→ 切换该层级的展开/收起状态
- 点击叶子节点（有 `herf`）→ 在返回栈中弹出 TocActivity，回到 ReaderActivity 并加载所选章节

### 4.5 YAML 头部处理 (MarkdownCleaner)

- 读取 MD 文本后，检查是否以 `---` 开头
- 如果是，查找第二个 `---` 位置
- 去除从第一个 `---` 到第二个 `---`（含）的全部内容
- 只渲染剩余的正文内容

### 4.6 MD 渲染 (Markwon)

- Markwon 配置: 支持图片、表格、代码高亮、链接
- 图片加载使用 Markwon 内置 Asset 方案或自定义 ImageLoader 从 assets 加载
- 兼容深色模式下的文字颜色适配

---

## 五、数据模型

```java
class BookMeta {
    String jsonFile;       // 如 "easy-vibe.json"
    String bookName;       // 如 "easy-vibe"
}

class TocNode {
    String name;           // 显示名称
    String herf;           // MD 文件相对路径（叶子节点有值）
    int level;             // 0=part, 1=chapter, 2=section
    boolean expanded;      // 展开状态（默认 true）
    List<TocNode> children;
}

class ReadingRecord {
    String bookName;
    String herf;           // 当前阅读的 MD 路径
    int scrollY;           // 滚动位置
    long timestamp;
}
```

---

## 六、Gradle 镜像配置

### gradle-wrapper.properties
```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.5-bin.zip
```

### settings.gradle
```groovy
pluginManagement {
    repositories {
        maven { url 'https://mirrors.cloud.tencent.com/nexus/repository/maven-public/' }
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        maven { url 'https://mirrors.cloud.tencent.com/nexus/repository/maven-public/' }
        google()
        mavenCentral()
    }
}
```

---

## 七、构建与编译环境

| 配置 | 值 |
|---|---|
| Android Gradle Plugin | 8.2.2 |
| Gradle | 8.5 |
| compileSdk | 34 |
| targetSdk | 34 |
| minSdk | 29 |
| Java 版本 | 17 |
