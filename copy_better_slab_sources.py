"""Copy Better Slab Forge 1.20.1 sources to the other 5 versions.

- Java sources: copied verbatim from 1.20.1/java/com/betterslab/
- Resources: betterslab.mixins.json, betterslab.client.mixins.json, assets/ copied
- META-INF/mods.toml and META-INF/neoforge.mods.toml are PRESERVED (version-specific)
- Existing java/com/betterslab/ in target is deleted first
"""

import shutil
from pathlib import Path

BASE = Path(r"C:\Users\顾智宸\Desktop\Place anywhere\src\better-slab\forge")
SRC = BASE / "1.20.1"
TARGETS = ["1.18.2", "1.19.2", "1.19.4", "1.20.6", "1.21.1"]


def copy_tree(src: Path, dst: Path):
    """Copy a directory tree, overwriting destination."""
    if dst.exists():
        shutil.rmtree(dst)
    shutil.copytree(src, dst)


def main():
    src_java = SRC / "java" / "com" / "betterslab"
    src_resources = SRC / "resources"

    for ver in TARGETS:
        tgt = BASE / ver
        print(f"=== Copying to {ver} ===")

        # 1. Java sources
        tgt_java = tgt / "java" / "com" / "betterslab"
        copy_tree(src_java, tgt_java)
        print(f"  Java: {tgt_java}")

        # 2. Resource files (mixins json + assets), preserve META-INF
        tgt_resources = tgt / "resources"

        # betterslab.mixins.json
        shutil.copy2(src_resources / "betterslab.mixins.json",
                     tgt_resources / "betterslab.mixins.json")
        # betterslab.client.mixins.json
        shutil.copy2(src_resources / "betterslab.client.mixins.json",
                     tgt_resources / "betterslab.client.mixins.json")

        # assets directory
        src_assets = src_resources / "assets"
        tgt_assets = tgt_resources / "assets"
        if src_assets.exists():
            copy_tree(src_assets, tgt_assets)
            print(f"  Assets: {tgt_assets}")

        # META-INF is preserved (mods.toml or neoforge.mods.toml already exist)
        print(f"  META-INF: preserved")

    print("\nDone! All 5 versions copied.")


if __name__ == "__main__":
    main()
