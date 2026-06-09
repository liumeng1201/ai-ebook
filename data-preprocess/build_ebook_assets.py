"""
构建 Android app 的 assets 资源目录。

功能：
1. 将 easy-vibe.json 和 vibe-vibe.json 拷贝到 assets 根目录
2. 遍历两个 JSON 中所有 herf 字段，将 MD 文件按路径拷贝到 assets 对应位置
3. 同时拷贝 MD 文件同级的 images/ 目录（若存在）

运行: python build_ebook_assets.py
"""

import json
import os
import shutil

BASE_DIR = r"c:\souce-code-ai\ai-ebook"
ASSETS_DIR = os.path.join(BASE_DIR, "ebook", "app", "src", "main", "assets")
JSON_FILES = ["easy-vibe.json", "vibe-vibe.json"]


def get_all_herf(obj, herf_list):
    """递归收集所有 herf 值"""
    if isinstance(obj, dict):
        if "herf" in obj and obj["herf"] is not None:
            herf_list.append(obj["herf"])
        for value in obj.values():
            get_all_herf(value, herf_list)
    elif isinstance(obj, list):
        for item in obj:
            get_all_herf(item, herf_list)


def copy_file(src, dst):
    """拷贝单个文件，自动创建目标目录"""
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    shutil.copy2(src, dst)
    print(f"  [COPY] {os.path.relpath(src, BASE_DIR)} -> {os.path.relpath(dst, BASE_DIR)}")


def copy_images_if_exists(src_dir, dst_dir, md_rel_dir):
    """拷贝 images/ 目录"""
    src_images = os.path.join(src_dir, "images")
    if os.path.isdir(src_images):
        dst_images = os.path.join(dst_dir, "images")
        if os.path.exists(dst_images):
            shutil.rmtree(dst_images)
        shutil.copytree(src_images, dst_images)
        print(f"  [IMAGES] {md_rel_dir}/images/ ({len(os.listdir(src_images))} files)")
    else:
        # 也可能 images 在和 md 文件同目录下
        # 例如 easy-vibe/docs/zh-cn/stage-1/xxx/index.md 的同级 images
        pass


def process_json(json_name):
    """处理单个 JSON 文件"""
    json_src = os.path.join(BASE_DIR, "data-preprocess", json_name)
    json_dst = os.path.join(ASSETS_DIR, json_name)

    print(f"\n{'='*50}")
    print(f"处理 {json_name} ...")
    print(f"{'='*50}")

    # 1. 拷贝 JSON 文件本身
    copy_file(json_src, json_dst)

    # 2. 读取 JSON
    with open(json_src, "r", encoding="utf-8") as f:
        data = json.load(f)

    # 3. 收集所有 herf
    herf_list = []
    get_all_herf(data, herf_list)
    print(f"\n找到 {len(herf_list)} 个 herf 路径")

    # 4. 去重
    unique_herf = sorted(set(herf_list))
    print(f"去重后 {len(unique_herf)} 个唯一路径\n")

    for herf in unique_herf:
        # 构建源路径
        src_path = os.path.join(BASE_DIR, herf.replace("/", "\\"))

        # 构建目标路径（在 assets 下保持相同相对路径）
        dst_path = os.path.join(ASSETS_DIR, herf.replace("/", "\\"))

        if not os.path.exists(src_path):
            print(f"  [SKIP] 源文件不存在: {herf}")
            continue

        # 拷贝 MD 文件
        copy_file(src_path, dst_path)

        # 拷贝同级的 images/ 目录
        src_dir = os.path.dirname(src_path)
        copy_images_if_exists(src_dir, os.path.dirname(dst_path), herf)


def main():
    # 清理旧的 assets 目录（保留空的 assets 目录）
    if os.path.exists(ASSETS_DIR):
        for item in os.listdir(ASSETS_DIR):
            item_path = os.path.join(ASSETS_DIR, item)
            if os.path.isdir(item_path):
                shutil.rmtree(item_path)
            else:
                os.remove(item_path)
        print(f"已清空 assets 目录")

    for json_name in JSON_FILES:
        process_json(json_name)

    # 统计
    print(f"\n{'='*50}")
    print(f"构建完成!")
    total_files = sum(len(files) for _, _, files in os.walk(ASSETS_DIR))
    print(f"assets 目录共 {total_files} 个文件")
    print(f"{'='*50}")


if __name__ == "__main__":
    main()
