#!/bin/bash

# Debug APK 构建和安装脚本
# 用途：打包 debug 包并安装到连接的 Android 设备

set -e  # 遇到错误立即退出

echo "================================================"
echo "  ACC Settings - Debug APK 构建和安装"
echo "================================================"
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 检查 adb 是否可用
if ! command -v adb &> /dev/null; then
    echo -e "${RED}错误: adb 未找到，请确保 Android SDK platform-tools 在 PATH 中${NC}"
    exit 1
fi

# 检查是否有设备连接
echo "检查设备连接状态..."
DEVICE_COUNT=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l)

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo -e "${YELLOW}警告: 没有检测到已连接的设备${NC}"
    echo "请确保:"
    echo "  1. 设备已通过 USB 连接"
    echo "  2. 设备已开启 USB 调试模式"
    echo "  3. 已授权此电脑进行调试"
    echo ""
    read -p "是否继续构建 APK (不安装)? (y/n): " CONTINUE_BUILD
    if [[ ! "$CONTINUE_BUILD" =~ ^[Yy]$ ]]; then
        echo "已取消操作"
        exit 0
    fi
    INSTALL_FLAG=false
else
    echo -e "${GREEN}检测到 $DEVICE_COUNT 个设备已连接${NC}"
    adb devices -l
    echo ""
    INSTALL_FLAG=true
fi

# 清理之前的构建
echo "清理之前的构建文件..."
./gradlew clean

# 构建 Debug APK
echo ""
echo "开始构建 Debug APK..."
echo "================================================"
./gradlew assembleDebug

# 检查构建是否成功
if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}✓ Debug APK 构建成功!${NC}"
    echo ""
else
    echo ""
    echo -e "${RED}✗ Debug APK 构建失败${NC}"
    exit 1
fi

# APK 文件路径
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

# 检查 APK 是否存在
if [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}错误: 找不到 APK 文件: $APK_PATH${NC}"
    exit 1
fi

# 显示 APK 信息
echo "APK 信息:"
echo "  路径: $(pwd)/$APK_PATH"
echo "  大小: $(du -h "$APK_PATH" | cut -f1)"
echo "  修改时间: $(stat -c %y "$APK_PATH" 2>/dev/null || stat -f "%Sm" "$APK_PATH")"
echo ""

# 安装到设备
if [ "$INSTALL_FLAG" = true ]; then
    echo "================================================"
    echo "开始安装到设备..."
    echo "================================================"

    # 卸载旧版本（如果存在）
    PACKAGE_NAME="app.owlow.accsetting"
    echo "检查旧版本..."
    if adb shell pm list packages | grep -q "$PACKAGE_NAME"; then
        echo "检测到旧版本，正在卸载..."
        adb uninstall "$PACKAGE_NAME"
        echo -e "${GREEN}✓ 旧版本已卸载${NC}"
    else
        echo "未检测到旧版本"
    fi

    # 安装新版本
    echo ""
    echo "安装新版本..."
    adb install "$APK_PATH"

    if [ $? -eq 0 ]; then
        echo ""
        echo -e "${GREEN}✓ APK 安装成功!${NC}"
        echo ""
        echo "启动应用..."
        adb shell am start -n "$PACKAGE_NAME/.SettingsActivity"
        echo ""
        echo -e "${GREEN}================================================${NC}"

        echo -e "${GREEN}✓ 全部完成!${NC}"
        echo -e "${GREEN}================================================${NC}"
    else
        echo ""
        echo -e "${RED}✗ APK 安装失败${NC}"
        echo "可能的原因:"
        echo "  - 设备存储空间不足"
        echo "  - 设备权限问题"
        echo "  - APK 签名问题"
        exit 1
    fi
else
    echo -e "${GREEN}================================================${NC}"
    echo -e "${GREEN}✓ APK 构建完成!${NC}"
    echo -e "${GREEN}================================================${NC}"
    echo ""
    echo "APK 位于: $(pwd)/$APK_PATH"
    echo ""
    echo "手动安装命令:"
    echo "  adb install $APK_PATH"
fi
