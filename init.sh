#!/bin/bash

# FlightChat 项目初始化脚本

echo "FlightChat - 飞行模式聊天应用"
echo "================================"
echo ""
echo "初始化步骤："
echo ""
echo "1. 创建 local.properties 文件"
if [ ! -f "local.properties" ]; then
    cp local.properties.example local.properties
    echo "   ✓ local.properties 已创建，请编辑 android_sdk_dir 路径"
else
    echo "   ✓ local.properties 已存在"
fi

echo ""
echo "2. 构建项目"
./gradlew clean build

echo ""
echo "3. （可选）安装到模拟器或真机"
echo "   ./gradlew installDebug"

echo ""
echo "✓ 初始化完成！"
echo ""
echo "快速开始："
echo "  1. 在 Android Studio 中打开此项目"
echo "  2. 等待 Gradle 同步完成"
echo "  3. 点击 'Run app' 启动应用"
echo ""
echo "更多信息请参考 README.md"
