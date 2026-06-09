"""
更新两个 JSON 文件中的 herf 字段，指向实际的 md 文件路径。

映射规则：
  easy-vibe: /easy-vibe/zh-cn/.../  -> easy-vibe/docs/zh-cn/.../index.md
              /easy-vibe/zh-cn/.../yyy.html -> easy-vibe/docs/zh-cn/.../yyy.md
  vibe-vibe: /Basic/.../yyy.html    -> vibe-vibe/docs/Basic/.../yyy.md
             /Advanced/.../yyy.html  -> vibe-vibe/docs/Advanced/.../yyy.md
"""

import json
import os

BASE_DIR = r"c:\souce-code-ai\ai-ebook"


def normalize_path(path: str) -> str:
    """将路径中的反斜杠统一为正斜杠"""
    return path.replace("\\", "/")


def update_herf_recursive(obj, book_name: str, book_docs_dir: str):
    """递归遍历 JSON 对象，更新所有 herf 字段"""
    if isinstance(obj, dict):
        if "herf" in obj and obj["herf"] is not None:
            herf = obj["herf"]
            # 处理 herf 映射
            if book_name == "easy-vibe":
                # easy-vibe: /easy-vibe/zh-cn/xxx/ 或 /easy-vibe/zh-cn/xxx/yyy.html
                if herf.startswith("/easy-vibe/"):
                    rel_path = herf[len("/easy-vibe/"):]  # zh-cn/xxx/...
                    if rel_path.endswith("/"):
                        # 目录形式 -> index.md
                        rel_path = rel_path + "index.md"
                    elif rel_path.endswith(".html"):
                        # .html -> .md
                        rel_path = rel_path[:-5] + ".md"
                    full_path = os.path.join(book_docs_dir, rel_path)
                    # 统一用正斜杠
                    obj["herf"] = normalize_path(full_path)
                else:
                    print(f"  [WARN] easy-vibe 未知 herf 格式: {herf}")
            elif book_name == "vibe-vibe":
                # vibe-vibe: /Basic/xxx/yyy.html 或 /Advanced/xxx/yyy.html
                if herf.startswith("/"):
                    rel_path = herf[1:]  # Basic/xxx/yyy.html
                    if rel_path.endswith(".html"):
                        rel_path = rel_path[:-5] + ".md"
                    full_path = os.path.join(book_docs_dir, rel_path)
                    obj["herf"] = normalize_path(full_path)
                else:
                    print(f"  [WARN] vibe-vibe 未知 herf 格式: {herf}")
        # 递归处理子字段
        for key, value in obj.items():
            update_herf_recursive(value, book_name, book_docs_dir)
    elif isinstance(obj, list):
        for item in obj:
            update_herf_recursive(item, book_name, book_docs_dir)


def process_json(json_path: str, book_name: str, book_docs_dir: str):
    """处理单个 JSON 文件"""
    print(f"处理 {json_path} ...")
    
    # 读取 JSON
    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    
    # 更新 herf
    update_herf_recursive(data, book_name, book_docs_dir)
    
    # 写入备份
    backup_path = json_path.replace(".json", ".backup.json")
    with open(backup_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"  备份已保存到: {backup_path}")
    
    # 写入更新后的 JSON
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    
    print(f"  更新完成: {json_path}")
    
    # 提取所有 herf 值进行验证
    herf_values = []
    def collect_herf(obj):
        if isinstance(obj, dict):
            if "herf" in obj and obj["herf"] is not None:
                herf_values.append(obj["herf"])
            for v in obj.values():
                collect_herf(v)
        elif isinstance(obj, list):
            for item in obj:
                collect_herf(item)
    
    collect_herf(data)
    
    # 验证文件是否存在
    missing = []
    for h in herf_values:
        # 将正斜杠转回系统路径
        sys_path = h.replace("/", "\\")
        if not os.path.exists(sys_path):
            missing.append(sys_path)
    
    if missing:
        print(f"\n  [WARN] 以下 {len(missing)} 个文件不存在:")
        for m in missing:
            print(f"    - {m}")
    else:
        print(f"\n  所有 {len(herf_values)} 个 herf 指向的文件均存在 [OK]")


if __name__ == "__main__":
    # 处理 easy-vibe.json
    process_json(
        json_path=os.path.join(BASE_DIR, "data-preprocess", "easy-vibe.json"),
        book_name="easy-vibe",
        book_docs_dir=os.path.join(BASE_DIR, "easy-vibe", "docs"),
    )
    
    print("\n" + "=" * 60 + "\n")
    
    # 处理 vibe-vibe.json
    process_json(
        json_path=os.path.join(BASE_DIR, "data-preprocess", "vibe-vibe.json"),
        book_name="vibe-vibe",
        book_docs_dir=os.path.join(BASE_DIR, "vibe-vibe", "docs"),
    )
