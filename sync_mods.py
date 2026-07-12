#!/usr/bin/env python3
"""
Place Anywhere 模组同步脚本
同步构建产物到服务端和客户端 mods 目录（删除旧 jar + 复制新 jar）
"""
import shutil
import os
from pathlib import Path

# 源目录
SRC_BASE = Path(r"C:\Users\顾智宸\Desktop\Place anywhere\1.21.1 fabric mod")

# 构建产物
JARS = {
    "placeanywhere": SRC_BASE / "Place anywhere core" / "build" / "libs" / "placeanywhere-0.1.0+1.21.1.jar",
    "betterslab": SRC_BASE / "better slab" / "build" / "libs" / "betterslab-1.0.0+1.21.1.jar",
    "freeplacement": SRC_BASE / "Place anywhere free placement" / "build" / "libs" / "freeplacement-0.1.0+1.21.1.jar",
}

# 目标目录
SERVER_MODS = SRC_BASE / "mc" / "versions" / "place_anywhere" / "mods"
CLIENT_MODS = Path(r"D:\mc\.minecraft\versions\better slab\mods")

# RCON 配置
RCON_HOST = "127.0.0.1"
RCON_PORT = 25575
RCON_PASS = "test123"


def rcon_send(commands):
    """通过 RCON 发送命令（简易实现）"""
    import socket
    import struct

    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.connect((RCON_HOST, RCON_PORT))
    sock.settimeout(5)

    # 登录
    def send_packet(req_id, ptype, body):
        data = body.encode("utf-8") + b"\x00\x00"
        length = len(data) + 8
        sock.sendall(struct.pack("<iii", length, req_id, ptype) + data)

    def recv_packet():
        length_data = sock.recv(4)
        length = struct.unpack("<i", length_data)[0]
        data = sock.recv(length)
        req_id = struct.unpack("<i", data[:4])[0]
        ptype = struct.unpack("<i", data[4:8])[0]
        body = data[8:-2].decode("utf-8", errors="replace")
        return req_id, ptype, body

    send_packet(1, 3, RCON_PASS)
    recv_packet()  # 登录响应

    for cmd in commands:
        send_packet(2, 2, cmd)
        _, _, body = recv_packet()
        print(f"  > {cmd}")
        if body:
            print(f"    {body}")

    sock.close()


def sync_mods(target_dir, jars):
    """同步 jar 到目标目录：删除同前缀旧文件，复制新文件"""
    target_dir = Path(target_dir)
    if not target_dir.exists():
        print(f"  目录不存在: {target_dir}")
        return

    for prefix, src_jar in jars.items():
        if not src_jar.exists():
            print(f"  [跳过] 源文件不存在: {src_jar.name}")
            continue

        # 删除同前缀的旧 jar
        for old in target_dir.glob(f"{prefix}*.jar"):
            print(f"  [删除] {old.name}")
            old.unlink()

        # 复制新 jar
        dst = target_dir / src_jar.name
        shutil.copy2(src_jar, dst)
        print(f"  [复制] {src_jar.name} -> {dst}")


def main():
    import sys

    targets = sys.argv[1:] if len(sys.argv) > 1 else ["server", "client"]

    # 尝试停止服务端
    if "server" in targets:
        print("=== 停止服务端 ===")
        try:
            rcon_send(["stop"])
            import time
            time.sleep(3)
        except Exception as e:
            print(f"  RCON 连接失败（服务端可能未运行）: {e}")

    # 同步
    if "server" in targets:
        print("\n=== 同步到服务端 ===")
        print(f"  目标: {SERVER_MODS}")
        sync_mods(SERVER_MODS, JARS)

    if "client" in targets:
        print("\n=== 同步到客户端 ===")
        print(f"  目标: {CLIENT_MODS}")
        sync_mods(CLIENT_MODS, JARS)

    # 重启服务端
    if "server" in targets:
        print("\n=== 重启服务端 ===")
        import subprocess
        subprocess.Popen(
            ["java", "-Xmx2G", "-jar", "fabric-server-launch.jar", "nogui"],
            cwd=str(SRC_BASE / "mc" / "versions" / "place_anywhere"),
            creationflags=subprocess.CREATE_NEW_CONSOLE
        )
        print("  服务端已启动")

    print("\n=== 同步完成 ===")


if __name__ == "__main__":
    main()
