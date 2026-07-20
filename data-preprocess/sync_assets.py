"""
sync_assets.py — 统一内容同步脚本

整合 4 本电子书的 assets 构建流程：
  · Claude Code 指南  (ai-coding-guide/claude-code/)
  · Codex 指南        (ai-coding-guide/codex/)
  · 小白代码          (easy-vibe)
  · Vibe 编码         (vibe-vibe)

额外生成 version.json（App 版本 + 各书内容版本），供 Android 端读取。

用法: python sync_assets.py
"""

import argparse
import json
import os
import shutil
import subprocess
import zipfile
from datetime import datetime, timezone


# ============================================================
# 0. 路径自动检测
# ============================================================

def get_base_dir():
    """自动获取项目根目录（脚本所在仓库的根）"""
    script_dir = os.path.dirname(os.path.abspath(__file__))
    return os.path.dirname(script_dir)  # data-preprocess/ 的上一级

BASE_DIR = get_base_dir()
GUIDE_DIR = os.path.join(BASE_DIR, "ai-coding-guide")
ASSETS_DIR = os.path.join(BASE_DIR, "ebook", "app", "src", "main", "assets")
DATA_DIR = os.path.join(BASE_DIR, "data-preprocess")


# ============================================================
# 1. Git 辅助函数
# ============================================================

def run_git(cwd, *args):
    """运行 git 命令，返回 stdout 去除换行"""
    try:
        result = subprocess.run(
            ["git"] + list(args),
            cwd=cwd,
            capture_output=True,
            text=True,
            check=True,
        )
        return result.stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return "unknown"


def collect_versions():
    """收集 App 版本 + 各书内容版本"""
    app_version_code = run_git(BASE_DIR, "rev-list", "--count", "HEAD")
    app_commit_sha = run_git(BASE_DIR, "rev-parse", "--short", "HEAD")
    app_version_name = f"1.0.{app_version_code}" if app_version_code != "unknown" else "1.0-dev"

    # 各子模块内容版本
    submodules = {
        "ai-coding-guide": GUIDE_DIR,
        "easy-vibe":       os.path.join(BASE_DIR, "easy-vibe"),
        "vibe-vibe":       os.path.join(BASE_DIR, "vibe-vibe"),
    }
    content_shas = {}
    for name, path in submodules.items():
        sha = run_git(path, "rev-parse", "--short", "HEAD")
        content_shas[name] = sha

    return {
        "app_version_code": int(app_version_code) if app_version_code != "unknown" else 0,
        "app_version_name": app_version_name,
        "app_commit_sha": app_commit_sha,
        "content_shas": content_shas,
    }


# ============================================================
# 2. 文件辅助函数
# ============================================================

def clean_dir(dirname):
    """仅删除 assets/<dirname>/ 下的文件，再重建空目录"""
    target = os.path.join(ASSETS_DIR, dirname)
    if os.path.exists(target):
        shutil.rmtree(target)
    os.makedirs(target, exist_ok=True)


def copy_file(src, dst):
    """拷贝单个文件，自动创建目标目录"""
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copy2(src, dst)
    print(f"  [COPY] {os.path.relpath(dst, ASSETS_DIR)}")


def copy_dir(src, dst):
    """拷贝整个目录"""
    if os.path.isdir(src):
        if os.path.exists(dst):
            shutil.rmtree(dst)
        shutil.copytree(src, dst)
        count = len(os.listdir(src))
        print(f"  [DIR]  {os.path.relpath(dst, ASSETS_DIR)}/ ({count} files)")


def get_all_herf(obj, herf_list):
    """递归收集 JSON 中所有 herf 值"""
    if isinstance(obj, dict):
        if "herf" in obj and obj["herf"] is not None:
            herf_list.append(obj["herf"])
        for value in obj.values():
            get_all_herf(value, herf_list)
    elif isinstance(obj, list):
        for item in obj:
            get_all_herf(item, herf_list)


def copy_images_if_exists(src_dir, dst_dir):
    """拷贝同级的 images/ 目录（若存在）"""
    src_images = os.path.join(src_dir, "images")
    if os.path.isdir(src_images):
        dst_images = os.path.join(dst_dir, "images")
        if os.path.exists(dst_images):
            shutil.rmtree(dst_images)
        shutil.copytree(src_images, dst_images)
        print(f"  [IMAGES] {os.path.relpath(dst_images, ASSETS_DIR)}/ ({len(os.listdir(src_images))} files)")


# ============================================================
# 3. sync_* 函数 — 每本书独立同步
# ============================================================

# ─── 3a. Claude Code 分组配置 ─────────────────────────────

CLAUDE_CODE_PARTS = [
    {
        "name": "一、基础入门",
        "chapters": [
            {
                "name": "基础入门",
                "sections": [
                    ("01-what-is-claude-code.md", "01 · Claude Code 简介"),
                    ("02-install.md", "02 · 安装与使用"),
                    ("03-how-it-works.md", "03 · Claude Code 如何工作"),
                    ("04-api-config.md", "04 · API 配置：订阅登录还是 API key，怎么选、怎么切"),
                    ("05-third-party-models.md", "05 · 接入第三方 / 国产模型"),
                ]
            },
        ]
    },
    {
        "name": "二、上手与项目",
        "chapters": [
            {
                "name": "上手与项目",
                "sections": [
                    ("06-coding-plan.md", "06 · Coding Plan：订阅套餐与计费"),
                    ("07-first-run.md", "07 · 第一次使用：跑通第一个例子"),
                    ("08-vscode.md", "08 · VS Code 集成"),
                    ("09-jetbrains.md", "09 · JetBrains 集成"),
                    ("10-desktop.md", "10 · 桌面 app（Desktop）"),
                    ("11-web-and-cloud.md", "11 · 网页版与云端：把 Claude Code 装进浏览器和手机"),
                    ("12-project-init.md", "12 · 项目初始化：用 /init 一键生成 CLAUDE.md"),
                    ("13-project-structure.md", "13 · 项目结构：Claude Code 在你项目里都放了什么"),
                ]
            },
        ]
    },
    {
        "name": "三、核心交互与操作",
        "chapters": [
            {
                "name": "核心交互",
                "sections": [
                    ("14-interface-and-shortcuts.md", "14 · 交互界面与快捷键：把手放对地方"),
                    ("15-prompting.md", "15 · 怎么提问和给指令：把话说到 Claude 心坎里"),
                    ("16-common-workflows.md", "16 · 四个最常用的活儿：探索代码库、修 bug、重构、写测试"),
                    ("17-images-multimodal.md", "17 · 图片与多模态"),
                ]
            },
            {
                "name": "CLAUDE.md 使用指南",
                "sections": [
                    ("18-claude-md-guide.md", "18 · CLAUDE.md 使用指南：把项目规矩写进它的记忆"),
                ]
            },
            {
                "name": "上下文管理",
                "sections": [
                    ("19-context-management.md", "19 · 上下文管理：别让它「失忆」也别烧爆 token"),
                ]
            },
            {
                "name": "权限配置",
                "sections": [
                    ("20-permissions.md", "20 · 权限配置：放多松、收多紧，你说了算"),
                ]
            },
            {
                "name": "安全与风险边界",
                "sections": [
                    ("21-security.md", "21 · 安全与风险边界：到底该不该信任 AI 碰你的代码"),
                ]
            },
        ]
    },
    {
        "name": "四、高级功能扩展",
        "chapters": [
            {
                "name": "MCP",
                "sections": [
                    ("22-mcp.md", "22 · MCP：给 Claude 接上外部世界"),
                ]
            },
            {
                "name": "子代理",
                "sections": [
                    ("23-subagents.md", "23 · 子代理（Subagents）：把活儿外包出去"),
                ]
            },
            {
                "name": "插件",
                "sections": [
                    ("24-plugins.md", "24 · 插件（Plugins）"),
                ]
            },
            {
                "name": "记忆系统",
                "sections": [
                    ("25-memory.md", "25 · 记忆系统（Memory）"),
                ]
            },
            {
                "name": "Agent Skills",
                "sections": [
                    ("26-agent-skills.md", "26 · Agent Skills：给 Claude 装一身随叫随到的专项本事"),
                    ("27-skills-in-practice.md", "27 · Skills 使用实例"),
                    ("28-skill-creator.md", "28 · Skill Creator 使用"),
                ]
            },
            {
                "name": "团队与功能选择",
                "sections": [
                    ("29-agent-teams.md", "29 · Agent teams"),
                    ("30-choosing-features.md", "30 · 功能怎么选"),
                ]
            },
        ]
    },
    {
        "name": "五、系统配置与优化",
        "chapters": [
            {
                "name": "settings.json",
                "sections": [
                    ("31-settings-json.md", "31 · settings.json：用户级 / 项目级配置"),
                ]
            },
            {
                "name": "输出与命令行",
                "sections": [
                    ("32-output-styles.md", "32 · 输出样式（Output Styles）"),
                    ("33-hooks.md", "33 · 钩子（Hooks）"),
                    ("34-cli-reference.md", "34 · CLI 参考手册"),
                    ("35-modes-and-control.md", "35 · 控制与模式"),
                    ("36-slash-commands.md", "36 · 斜杠命令（Slash Commands）"),
                    ("37-checkpoints.md", "37 · 检查点（Checkpoints）"),
                ]
            },
        ]
    },
    {
        "name": "六、高级参考与实战",
        "chapters": [
            {
                "name": "插件参考手册",
                "sections": [
                    ("38-plugins-reference.md", "38 · 插件参考手册"),
                ]
            },
            {
                "name": "实战与集成",
                "sections": [
                    ("39-getting-started-practice.md", "39 · 实战入门"),
                    ("40-chrome.md", "40 · Chrome"),
                    ("41-parallel-tasks.md", "41 · 并行任务"),
                    ("42-env-vars.md", "42 · 环境变量"),
                    ("43-git-workflow.md", "43 · Git 工作流"),
                ]
            },
            {
                "name": "GitHub Actions",
                "sections": [
                    ("44-github-actions.md", "44 · GitHub Actions"),
                ]
            },
            {
                "name": "Agent SDK",
                "sections": [
                    ("45-agent-sdk.md", "45 · Agent SDK"),
                ]
            },
            {
                "name": "开发配置与实战",
                "sections": [
                    ("46-dev-config.md", "46 · 开发配置"),
                    ("47-voice.md", "47 · Voice 语音模式"),
                    ("48-capstone-project.md", "48 · 综合实战"),
                ]
            },
        ]
    },
    {
        "name": "七、收尾与查阅",
        "chapters": [
            {
                "name": "收尾与查阅",
                "sections": [
                    ("49-best-practices.md", "49 · 最佳实践"),
                    ("50-anti-patterns.md", "50 · 反模式"),
                    ("51-troubleshooting.md", "51 · 常见问题排查"),
                    ("52-glossary.md", "52 · 术语表"),
                ]
            },
        ]
    },
]


# ─── 3b. Codex 分组配置 ─────────────────────────────────

CODEX_PARTS = [
    {
        "name": "一、基础入门",
        "chapters": [
            {
                "name": "基础入门",
                "sections": [
                    ("01-what-is-codex.md", "01 · 认识 Codex 与四种入口"),
                    ("02-core-concepts.md", "02 · Codex 核心概念速览"),
                    ("03-install.md", "03 · 安装与登录"),
                    ("04-pricing.md", "04 · 订阅与计费"),
                    ("05-third-party-models.md", "05 · 接入第三方 / 国产模型"),
                ]
            },
        ]
    },
    {
        "name": "二、各入口怎么上手",
        "chapters": [
            {
                "name": "各入口怎么上手",
                "sections": [
                    ("06-first-task.md", "06 · 跑通第一个任务"),
                    ("07-desktop-app.md", "07 · 桌面 App 全景"),
                    ("08-cli.md", "08 · 命令行 CLI 上手"),
                    ("09-ide.md", "09 · IDE 扩展（VS Code 等）"),
                    ("10-cloud.md", "10 · 云端 Codex Cloud"),
                    ("11-agents-md.md", "11 · 项目说明书 AGENTS.md"),
                ]
            },
        ]
    },
    {
        "name": "三、核心交互与操作",
        "chapters": [
            {
                "name": "斜杠命令",
                "sections": [
                    ("12-slash-commands.md", "12 · 斜杠命令与快捷键"),
                ]
            },
            {
                "name": "提示词写法",
                "sections": [
                    ("13-prompting.md", "13 · 提示词写法"),
                ]
            },
            {
                "name": "工作流与权限安全",
                "sections": [
                    ("14-workflows.md", "14 · 四类日常工作流"),
                    ("15-permissions.md", "15 · 权限、沙箱与审批"),
                    ("16-security.md", "16 · 安全与风险边界"),
                    ("17-computer-use.md", "17 · 电脑操控与浏览器"),
                ]
            },
        ]
    },
    {
        "name": "四、高级功能扩展",
        "chapters": [
            {
                "name": "配置与记忆",
                "sections": [
                    ("18-config.md", "18 · config.toml 配置详解"),
                    ("19-memory.md", "19 · 记忆系统"),
                ]
            },
            {
                "name": "协议与扩展",
                "sections": [
                    ("20-mcp.md", "20 · MCP"),
                    ("21-subagents.md", "21 · 子代理"),
                    ("22-skills.md", "22 · Agent Skills"),
                    ("23-plugins.md", "23 · 插件"),
                    ("24-hooks.md", "24 · 规则与钩子"),
                ]
            },
        ]
    },
    {
        "name": "五、工程化与自动化",
        "chapters": [
            {
                "name": "工程化与自动化",
                "sections": [
                    ("25-worktrees.md", "25 · Worktrees 并行隔离"),
                    ("26-git-github.md", "26 · Git 与 GitHub 集成"),
                    ("27-automation.md", "27 · 自动化与 CI/CD"),
                    ("28-noninteractive.md", "28 · 非交互模式"),
                    ("29-integrations.md", "29 · Slack / Linear / SDK"),
                ]
            },
        ]
    },
    {
        "name": "六、实战与进阶",
        "chapters": [
            {
                "name": "实战与进阶",
                "sections": [
                    ("30-models.md", "30 · 怎么选模型"),
                    ("31-speed.md", "31 · 进阶技巧与提速"),
                    ("32-migrate-from-claude-code.md", "32 · 从 Claude Code 迁移"),
                    ("33-windows.md", "33 · Windows 使用要点"),
                ]
            },
            {
                "name": "综合实战",
                "sections": [
                    ("34-capstone.md", "34 · 综合实战"),
                ]
            },
        ]
    },
    {
        "name": "七、收尾与查阅",
        "chapters": [
            {
                "name": "收尾与查阅",
                "sections": [
                    ("35-cheatsheet.md", "35 · 命令与配置速查表"),
                    ("36-best-practices.md", "36 · 最佳实践"),
                    ("37-faq.md", "37 · 常见问题排查"),
                    ("38-glossary.md", "38 · 术语表"),
                    ("39-enterprise.md", "39 · 企业管理与治理"),
                ]
            },
        ]
    },
]


# ─── 3c. 同步函数 ────────────────────────────────────────

def sync_claude_code():
    """同步 Claude Code 指南"""
    source_dir = os.path.join(GUIDE_DIR, "claude-code")
    assets_subdir = "claude-code"
    book_name = "Claude Code 指南"

    print(f"\n{'='*60}")
    print(f"处理 {book_name}")
    print(f"{'='*60}")

    clean_dir(assets_subdir)
    json_data = {"book": book_name, "part": []}

    for part in CLAUDE_CODE_PARTS:
        part_obj = {"name": part["name"], "herf": None, "chapter": []}
        for ch in part["chapters"]:
            for filename, title in ch["sections"]:
                herf = f"{assets_subdir}/{filename}"
                src_path = os.path.join(source_dir, filename)
                if not os.path.exists(src_path):
                    print(f"  [SKIP] 源文件不存在: {filename}")
                    continue
                dst_path = os.path.join(ASSETS_DIR, herf)
                copy_file(src_path, dst_path)
                part_obj["chapter"].append({"name": title, "herf": herf})
        json_data["part"].append(part_obj)

    # 拷贝 assets/ 图片目录
    src_images = os.path.join(source_dir, "assets")
    dst_images = os.path.join(ASSETS_DIR, assets_subdir, "assets")
    if os.path.isdir(src_images):
        copy_dir(src_images, dst_images)

    # 写 JSON
    json_path = os.path.join(ASSETS_DIR, f"{assets_subdir}.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(json_data, f, ensure_ascii=False, indent=2)
    print(f"\n  [JSON] 已生成: {assets_subdir}.json")

    total_md = sum(1 for p in json_data["part"] for c in p["chapter"])
    total_images = len(os.listdir(src_images)) if os.path.isdir(src_images) else 0
    total_chapters = sum(len(p["chapter"]) for p in json_data["part"])
    print(f"  统计: {len(json_data['part'])} 部分 / {total_chapters} 章 / {total_md} 篇文章 / {total_images} 张图片")

    return json_data


def sync_codex():
    """同步 Codex 指南"""
    source_dir = os.path.join(GUIDE_DIR, "codex")
    assets_subdir = "codex"
    book_name = "Codex 指南"

    print(f"\n{'='*60}")
    print(f"处理 {book_name}")
    print(f"{'='*60}")

    clean_dir(assets_subdir)
    json_data = {"book": book_name, "part": []}

    for part in CODEX_PARTS:
        part_obj = {"name": part["name"], "herf": None, "chapter": []}
        for ch in part["chapters"]:
            for filename, title in ch["sections"]:
                herf = f"{assets_subdir}/{filename}"
                src_path = os.path.join(source_dir, filename)
                if not os.path.exists(src_path):
                    print(f"  [SKIP] 源文件不存在: {filename}")
                    continue
                dst_path = os.path.join(ASSETS_DIR, herf)
                copy_file(src_path, dst_path)
                part_obj["chapter"].append({"name": title, "herf": herf})
        json_data["part"].append(part_obj)

    # 拷贝 assets/ 图片目录
    src_images = os.path.join(source_dir, "assets")
    dst_images = os.path.join(ASSETS_DIR, assets_subdir, "assets")
    if os.path.isdir(src_images):
        copy_dir(src_images, dst_images)

    # 写 JSON
    json_path = os.path.join(ASSETS_DIR, f"{assets_subdir}.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(json_data, f, ensure_ascii=False, indent=2)
    print(f"\n  [JSON] 已生成: {assets_subdir}.json")

    total_md = sum(1 for p in json_data["part"] for c in p["chapter"])
    total_images = len(os.listdir(src_images)) if os.path.isdir(src_images) else 0
    total_chapters = sum(len(p["chapter"]) for p in json_data["part"])
    print(f"  统计: {len(json_data['part'])} 部分 / {total_chapters} 章 / {total_md} 篇文章 / {total_images} 张图片")

    return json_data


def sync_easy_vibe():
    """同步 小白代码"""
    json_name = "easy-vibe.json"
    book_name = "小白代码"
    assets_subdir = "easy-vibe"

    print(f"\n{'='*60}")
    print(f"处理 {book_name}")
    print(f"{'='*60}")

    clean_dir(assets_subdir)

    # 拷贝 JSON 到 assets 根目录
    json_src = os.path.join(DATA_DIR, json_name)
    json_dst = os.path.join(ASSETS_DIR, json_name)
    copy_file(json_src, json_dst)

    # 读取 JSON
    with open(json_src, "r", encoding="utf-8") as f:
        data = json.load(f)

    # 收集所有 herf
    herf_list = []
    get_all_herf(data, herf_list)
    unique_herf = sorted(set(herf_list))
    print(f"\n找到 {len(unique_herf)} 个 herf 路径\n")

    md_count = 0
    for herf in unique_herf:
        src_path = os.path.join(BASE_DIR, herf.replace("/", os.sep))
        dst_path = os.path.join(ASSETS_DIR, herf.replace("/", os.sep))
        if not os.path.exists(src_path):
            print(f"  [SKIP] 源文件不存在: {herf}")
            continue
        copy_file(src_path, dst_path)
        md_count += 1
        # 拷贝同级的 images/
        copy_images_if_exists(os.path.dirname(src_path), os.path.dirname(dst_path))

    print(f"\n  统计: {md_count} 篇文章")


def sync_vibe_vibe():
    """同步 Vibe 编码"""
    json_name = "vibe-vibe.json"
    book_name = "Vibe 编码"
    assets_subdir = "vibe-vibe"

    print(f"\n{'='*60}")
    print(f"处理 {book_name}")
    print(f"{'='*60}")

    clean_dir(assets_subdir)

    # 拷贝 JSON 到 assets 根目录
    json_src = os.path.join(DATA_DIR, json_name)
    json_dst = os.path.join(ASSETS_DIR, json_name)
    copy_file(json_src, json_dst)

    # 读取 JSON
    with open(json_src, "r", encoding="utf-8") as f:
        data = json.load(f)

    # 收集所有 herf
    herf_list = []
    get_all_herf(data, herf_list)
    unique_herf = sorted(set(herf_list))
    print(f"\n找到 {len(unique_herf)} 个 herf 路径\n")

    md_count = 0
    for herf in unique_herf:
        src_path = os.path.join(BASE_DIR, herf.replace("/", os.sep))
        dst_path = os.path.join(ASSETS_DIR, herf.replace("/", os.sep))
        if not os.path.exists(src_path):
            print(f"  [SKIP] 源文件不存在: {herf}")
            continue
        copy_file(src_path, dst_path)
        md_count += 1
        # 拷贝同级的 images/
        copy_images_if_exists(os.path.dirname(src_path), os.path.dirname(dst_path))

    print(f"\n  统计: {md_count} 篇文章")


# ============================================================
# 4. 版本信息生成
# ============================================================

def generate_version_json(versions, release_tag=None, repository=None):
    """生成 version.json 到 assets 目录"""
    books = [
        {
            "id": "claude-code",
            "name": "Claude Code 指南",
            "json_file": "claude-code.json",
            "source_repo": "ai-coding-guide",
            "content_sha": versions["content_shas"].get("ai-coding-guide", "unknown"),
            "content_version": versions["content_shas"].get("ai-coding-guide", "unknown"),
            "archive_name": "claude-code.zip",
        },
        {
            "id": "codex",
            "name": "Codex 指南",
            "json_file": "codex.json",
            "source_repo": "ai-coding-guide",
            "content_sha": versions["content_shas"].get("ai-coding-guide", "unknown"),
            "content_version": versions["content_shas"].get("ai-coding-guide", "unknown"),
            "archive_name": "codex.zip",
        },
        {
            "id": "easy-vibe",
            "name": "小白代码",
            "json_file": "easy-vibe.json",
            "source_repo": "easy-vibe",
            "content_sha": versions["content_shas"].get("easy-vibe", "unknown"),
            "content_version": versions["content_shas"].get("easy-vibe", "unknown"),
            "archive_name": "easy-vibe.zip",
        },
        {
            "id": "vibe-vibe",
            "name": "Vibe 编码",
            "json_file": "vibe-vibe.json",
            "source_repo": "vibe-vibe",
            "content_sha": versions["content_shas"].get("vibe-vibe", "unknown"),
            "content_version": versions["content_shas"].get("vibe-vibe", "unknown"),
            "archive_name": "vibe-vibe.zip",
        },
    ]

    for book in books:
        if repository and release_tag:
            book["download_url"] = (
                f"https://github.com/{repository}/releases/download/{release_tag}/"
                f"{book['archive_name']}"
            )

    version_data = {
        "schema_version": 2,
        "app_version_code": versions["app_version_code"],
        "app_version_name": versions["app_version_name"],
        "app_commit_sha": versions["app_commit_sha"],
        "books": books,
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }

    json_path = os.path.join(ASSETS_DIR, "manifest.json")
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(version_data, f, ensure_ascii=False, indent=2)
    print(f"\n  [MANIFEST] 已生成: manifest.json")
    return version_data


def package_books(output_dir):
    """将每本书的目录 JSON 与其资源目录打成独立 ZIP。"""
    os.makedirs(output_dir, exist_ok=True)
    for book_id in ("claude-code", "codex", "easy-vibe", "vibe-vibe"):
        archive_path = os.path.join(output_dir, f"{book_id}.zip")
        json_path = os.path.join(ASSETS_DIR, f"{book_id}.json")
        content_dir = os.path.join(ASSETS_DIR, book_id)
        with zipfile.ZipFile(archive_path, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.write(json_path, os.path.basename(json_path))
            for root, _, files in os.walk(content_dir):
                for filename in files:
                    file_path = os.path.join(root, filename)
                    archive.write(file_path, os.path.relpath(file_path, ASSETS_DIR))
        print(f"  [PACKAGE] {archive_path}")


def parse_args():
    parser = argparse.ArgumentParser(description="同步并拆分电子书内容包")
    parser.add_argument("--output-dir", help="内容包与 manifest 输出目录")
    parser.add_argument("--release-tag", default=os.environ.get("CONTENT_RELEASE_TAG"))
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY"))
    return parser.parse_args()


# ============================================================
# 5. Main
# ============================================================

def main():
    args = parse_args()
    print("=" * 60)
    print("统一内容同步 — sync_assets.py")
    print("=" * 60)
    print(f"项目根目录: {BASE_DIR}")
    print(f"assets 目录: {ASSETS_DIR}")

    # 1. 收集版本信息
    print(f"\n{'='*60}")
    print("收集版本信息")
    print(f"{'='*60}")
    versions = collect_versions()
    print(f"  App version_code: {versions['app_version_code']}")
    print(f"  App version_name: {versions['app_version_name']}")
    print(f"  App commit SHA:   {versions['app_commit_sha']}")
    for name, sha in versions["content_shas"].items():
        print(f"  {name}: {sha}")

    # 2. 同步各电子书
    sync_claude_code()
    sync_codex()
    sync_easy_vibe()
    sync_vibe_vibe()

    # 3. 生成 version.json
    generate_version_json(versions, args.release_tag, args.repository)

    if args.output_dir:
        package_books(args.output_dir)
        shutil.copy2(os.path.join(ASSETS_DIR, "manifest.json"),
                     os.path.join(args.output_dir, "manifest.json"))

    # 4. 汇总
    print(f"\n{'='*60}")
    print("构建完成!")
    total_files = sum(len(files) for _, _, files in os.walk(ASSETS_DIR))
    print(f"assets 目录共 {total_files} 个文件")
    print("=" * 60)


if __name__ == "__main__":
    main()
