"""
将两个 JSON 文件中的 herf 从绝对路径改为相对路径。
去掉 "c:/souce-code-ai/ai-ebook/" 前缀。
"""
import json
import os

BASE_DIR = r"c:\souce-code-ai\ai-ebook"
PREFIX = "c:/souce-code-ai/ai-ebook/"

def fix_herf_recursive(obj):
    if isinstance(obj, dict):
        if "herf" in obj and obj["herf"] is not None:
            herf = obj["herf"]
            if herf.startswith(PREFIX):
                obj["herf"] = herf[len(PREFIX):]
        for key, value in obj.items():
            fix_herf_recursive(value)
    elif isinstance(obj, list):
        for item in obj:
            fix_herf_recursive(item)

def process_json(json_path):
    print(f"处理 {os.path.basename(json_path)} ...")
    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)
    fix_herf_recursive(data)
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print(f"  完成")

if __name__ == "__main__":
    process_json(os.path.join(BASE_DIR, "data-preprocess", "easy-vibe.json"))
    process_json(os.path.join(BASE_DIR, "data-preprocess", "vibe-vibe.json"))
