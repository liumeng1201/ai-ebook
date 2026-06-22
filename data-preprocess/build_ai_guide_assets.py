"""
构建 AI 编程指南（Claude Code + Codex）到 ebook assets 目录。

功能：
1. 将 claude-code/ 和 codex/ 的 .md 文件拷贝到 assets 对应路径
2. 拷贝 assets/ 图片目录
3. 生成 claude-code.json 和 codex.json 目录文件

运行: python build_ai_guide_assets.py
"""

import json
import os
import shutil

BASE_DIR = r"c:\souce-code-ai\ai-ebook"
GUIDE_DIR = os.path.join(BASE_DIR, "ai-coding-guide")
ASSETS_DIR = os.path.join(BASE_DIR, "ebook", "app", "src", "main", "assets")


# ─── Claude Code 分组配置（按用户的 7 部分结构）─────────────────────

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
                    ("17-images-multimodal.md", "17 · 图片与多模态：贴张截图，它就懂了"),
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
                    ("23-subagents.md", "23 · 子代理（Subagents）：把活儿外包出去，别什么都自己扛"),
                ]
            },
            {
                "name": "插件",
                "sections": [
                    ("24-plugins.md", "24 · 插件（Plugins）：把一堆零碎配置一键打包"),
                ]
            },
            {
                "name": "记忆系统",
                "sections": [
                    ("25-memory.md", "25 · 记忆系统（memory）：让它跨会话记住你"),
                ]
            },
            {
                "name": "Agent Skills",
                "sections": [
                    ("26-agent-skills.md", "26 · Agent Skills：给 Claude 装一身随叫随到的专项本事"),
                    ("27-skills-in-practice.md", "27 · Skills 使用实例：装一个、喊一声、看它干活"),
                    ("28-skill-creator.md", "28 · skill-creator 使用：用一个 skill 造你自己的 skill"),
                ]
            },
            {
                "name": "团队与功能选择",
                "sections": [
                    ("29-agent-teams.md", "29 · Agent teams 智能体团队：多会话协作"),
                    ("30-choosing-features.md", "30 · 功能怎么选：CLAUDE.md vs Skill vs Hook vs MCP vs Subagent"),
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
                    ("32-output-styles.md", "32 · 输出样式（Output Styles）：换一档「节目」，不换主持人"),
                    ("33-hooks.md", "33 · 钩子（Hooks）：在固定时机自动扣扳机"),
                    ("34-cli-reference.md", "34 · CLI 参考手册：命令与全部标志"),
                    ("35-modes-and-control.md", "35 · 控制与模式：开会话时手里那块「调音台」"),
                    ("36-slash-commands.md", "36 · 斜杠命令（Slash Commands）：一个 `/` 调出 Claude 的所有快捷动作"),
                    ("37-checkpoints.md", "37 · 检查点（Checkpoints）：随时能倒带的安全网"),
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
                    ("38-plugins-reference.md", "38 · 插件参考手册：把自己那套配置，打成一个能发出去的包"),
                ]
            },
            {
                "name": "实战与集成",
                "sections": [
                    ("39-getting-started-practice.md", "39 · 实战入门：拿一个真需求，从开工到交付走一整趟"),
                    ("40-chrome.md", "40 · Chrome：让它操作浏览器"),
                    ("41-parallel-tasks.md", "41 · 并行任务：让几个 Claude 同时开工，而不是排队"),
                    ("42-env-vars.md", "42 · 环境变量：藏在背后那排「总开关」"),
                    ("43-git-workflow.md", "43 · Git 工作流：让 Claude 当你的 git 副手"),
                ]
            },
            {
                "name": "GitHub Actions",
                "sections": [
                    ("44-github-actions.md", "44 · GitHub Actions：在 PR 里 @ 一下，让 Claude 自己干活"),
                ]
            },
            {
                "name": "Agent SDK",
                "sections": [
                    ("45-agent-sdk.md", "45 · Agent SDK：把 Claude Code 的能力搬进你自己的程序"),
                ]
            },
            {
                "name": "开发配置与实战",
                "sections": [
                    ("46-dev-config.md", "46 · 开发配置：把 Claude 干活的「工作环境」调顺"),
                    ("47-voice.md", "47 · Voice 语音模式：把提示词说出来，而不是打出来"),
                    ("48-capstone-project.md", "48 · 综合实战：从零到上线，把所学串成一条线"),
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
                    ("49-best-practices.md", "49 · 最佳实践：把零散的好习惯，攒成一套能照着做的心法"),
                    ("50-anti-patterns.md", "50 · 反模式：常见的错误用法"),
                    ("51-troubleshooting.md", "51 · 常见问题排查（FAQ / Troubleshooting）"),
                    ("52-glossary.md", "52 · 术语表（小白友好）：把这一路的「黑话」一次性翻译成人话"),
                ]
            },
        ]
    },
]

# ─── Codex 分组配置（2 层结构）─────────────────────────────────────────

CODEX_PARTS = [
    {
        "name": "一、基础入门",
        "chapters": [
            {
                "name": "基础入门",
                "sections": [
                    ("01-what-is-codex.md", "01 · 认识 Codex 与四种入口"),
                    ("02-core-concepts.md", "02 · Codex 核心概念速览"),
                    ("03-install.md", "03 · 安装与登录（Mac / Windows / Linux）"),
                    ("04-pricing.md", "04 · 订阅与计费"),
                    ("05-third-party-models.md", "05 · 接入 DeepSeek 等国产模型"),
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
                    ("10-cloud.md", "10 · 云端 Codex Cloud：把活丢上云，喝着咖啡等结果"),
                    ("11-agents-md.md", "11 · 项目说明书 AGENTS.md：把规矩焊进 Codex 的开工流程"),
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
                    ("12-slash-commands.md", "12 · 斜杠命令与快捷键：会话里的「快捷操作面板」"),
                ]
            },
            {
                "name": "提示词写法",
                "sections": [
                    ("13-prompting.md", "13 · 提示词（Prompt）写法：把话说到 Codex 心坎里"),
                ]
            },
            {
                "name": "工作流与权限安全",
                "sections": [
                    ("14-workflows.md", "14 · 四类日常工作流：探索、修 bug、重构、写测试"),
                    ("15-permissions.md", "15 · 权限、沙箱与审批：放多松、收多紧，自己拧"),
                    ("16-security.md", "16 · 安全与风险边界：到底该不该放手让它碰你的代码"),
                    ("17-computer-use.md", "17 · 电脑操控与浏览器（Computer Use）：让 Codex 长出手"),
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
                    ("18-config.md", "18 · config.toml 配置详解：一个文件管住所有旋钮"),
                    ("19-memory.md", "19 · 记忆系统（Memories 与 Chronicle）：让 Codex 跨会话记住你"),
                ]
            },
            {
                "name": "协议与扩展",
                "sections": [
                    ("20-mcp.md", "20 · 用 MCP 接外部工具：给 Codex 装上「外接口」"),
                    ("21-subagents.md", "21 · 子代理（Subagents）：把活儿拆出去并行跑"),
                    ("22-skills.md", "22 · Agent Skills 技能：把一套活儿打包，教会 Codex 自己接"),
                    ("23-plugins.md", "23 · 插件（Plugins）：一键装一整套能力"),
                    ("24-hooks.md", "24 · 规则与钩子（Rules & Hooks）：给 Codex 装上「卡点」和「扳机」"),
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
                    ("25-worktrees.md", "25 · Worktrees 并行隔离：让几个 Codex 各干各的，互不打架"),
                    ("26-git-github.md", "26 · Git 与 GitHub 集成：让 Codex 在你的 PR 里当审查员"),
                    ("27-automation.md", "27 · 自动化与 CI/CD：让 Codex 在你不在的时候自己干活"),
                    ("28-noninteractive.md", "28 · 非交互模式 codex exec：把它塞进脚本和 CI 里跑"),
                    ("29-integrations.md", "29 · Slack / Linear 与 SDK 集成：在别处召唤 Codex"),
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
                    ("30-models.md", "30 · 怎么选模型：同一句话，到底该派哪个模型去跑"),
                    ("31-speed.md", "31 · 进阶技巧与提速：拖慢你的不是模型，是你给的烂上下文"),
                    ("32-migrate-from-claude-code.md", "32 · 从 Claude Code 迁移：旧地图换个工具，照样能找到家"),
                    ("33-windows.md", "33 · Windows 使用要点：原生还是 WSL，到底怎么跑才省心"),
                ]
            },
            {
                "name": "综合实战",
                "sections": [
                    ("34-capstone.md", "34 · 综合实战：从零给一个 TODO 小工具加功能、提交一次"),
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
                    ("36-best-practices.md", "36 · 最佳实践：那些「正确的废话」之外，真正能落地的几条"),
                    ("37-faq.md", "37 · 常见问题排查：装不上、登不了、不肯改文件，挨个拆"),
                    ("38-glossary.md", "38 · 术语表"),
                    ("39-enterprise.md", "39 · 企业管理与治理：一个人玩和一家公司用，是两件事"),
                ]
            },
        ]
    },
]


def copy_file(src, dst, desc=""):
    """拷贝单个文件，自动创建目标目录"""
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copy2(src, dst)
    label = f" [{desc}]" if desc else ""
    print(f"  [COPY]{label} {os.path.relpath(dst, ASSETS_DIR)}")


def copy_dir(src, dst):
    """拷贝整个目录"""
    if os.path.isdir(src):
        if os.path.exists(dst):
            shutil.rmtree(dst)
        shutil.copytree(src, dst)
        count = len(os.listdir(src))
        print(f"  [DIR]  {os.path.relpath(dst, ASSETS_DIR)}/ ({count} files)")


def build_json_2level(book_name, parts, source_dir, assets_subdir):
    """构建 2 层 JSON（part → chapter），每篇文章直接作为 chapter 叶子节点"""
    json_data = {
        "book": book_name,
        "part": []
    }

    for part in parts:
        part_obj = {
            "name": part["name"],
            "herf": None,
            "chapter": []
        }

        for ch in part["chapters"]:
            for filename, title in ch["sections"]:
                herf = f"{assets_subdir}/{filename}"

                src_path = os.path.join(source_dir, filename)
                if not os.path.exists(src_path):
                    print(f"  [SKIP] 源文件不存在: {src_path}")
                    continue

                dst_path = os.path.join(ASSETS_DIR, herf)
                copy_file(src_path, dst_path)

                chapter_obj = {
                    "name": title,
                    "herf": herf
                }
                part_obj["chapter"].append(chapter_obj)

        json_data["part"].append(part_obj)

    return json_data


def build_json_3level(book_name, parts, source_dir, assets_subdir):
    """构建 3 层 JSON（part → chapter → section）"""
    json_data = {
        "book": book_name,
        "part": []
    }

    for part in parts:
        part_obj = {
            "name": part["name"],
            "herf": None,
            "chapter": []
        }

        for ch in part["chapters"]:
            chapter_obj = {
                "name": ch["name"],
                "herf": None,
                "section": []
            }

            for filename, title in ch["sections"]:
                herf = f"{assets_subdir}/{filename}"

                src_path = os.path.join(source_dir, filename)
                if not os.path.exists(src_path):
                    print(f"  [SKIP] 源文件不存在: {src_path}")
                    continue

                dst_path = os.path.join(ASSETS_DIR, herf)
                copy_file(src_path, dst_path)

                section_obj = {
                    "name": title,
                    "herf": herf
                }
                chapter_obj["section"].append(section_obj)

            part_obj["chapter"].append(chapter_obj)

        json_data["part"].append(part_obj)

    return json_data


def process_guide(book_name, parts, source_subdir):
    """处理单个指南"""
    source_dir = os.path.join(GUIDE_DIR, source_subdir)
    assets_subdir = source_subdir  # 在 assets 中用同样目录名

    print(f"\n{'='*60}")
    print(f"处理 {book_name} ({source_subdir}/)")
    print(f"{'='*60}")
    print(f"来源: {source_dir}")
    print(f"目标: {os.path.join(ASSETS_DIR, assets_subdir)}")

    # 1. 拷贝 .md 文件并构建 JSON（2 层结构：part → chapter）
    json_data = build_json_2level(book_name, parts, source_dir, assets_subdir)

    # 2. 拷贝 assets/ 图片目录
    src_images = os.path.join(source_dir, "assets")
    dst_images = os.path.join(ASSETS_DIR, assets_subdir, "assets")
    if os.path.isdir(src_images):
        copy_dir(src_images, dst_images)
    else:
        print(f"  [SKIP] 图片目录不存在: {src_images}")

    # 3. 写 JSON 文件
    json_filename = f"{source_subdir}.json"
    json_path = os.path.join(ASSETS_DIR, json_filename)
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(json_data, f, ensure_ascii=False, indent=2)
    print(f"\n  [JSON] 已生成: {json_filename}")

    total_md = sum(1 for part in json_data["part"] for ch in part["chapter"] if ch.get("herf"))
    total_images = len(os.listdir(src_images)) if os.path.isdir(src_images) else 0
    total_parts = len(json_data["part"])
    total_chapters = sum(len(part["chapter"]) for part in json_data["part"])
    print(f"  统计: {total_parts} 部分 / {total_chapters} 章 / {total_md} 篇文章 / {total_images} 张图片")


def main():
    print("=" * 60)
    print("构建 AI 编程指南 ebook 资源")
    print("=" * 60)

    # 处理 Claude Code
    process_guide("Claude Code 指南", CLAUDE_CODE_PARTS, "claude-code")

    # 处理 Codex
    process_guide("Codex 指南", CODEX_PARTS, "codex")

    # 统计
    print(f"\n{'='*60}")
    print(f"构建完成!")
    total_files = sum(len(files) for _, _, files in os.walk(ASSETS_DIR))
    print(f"assets 目录共 {total_files} 个文件")
    print(f"{'='*60}")


if __name__ == "__main__":
    main()
