# 🔮 灵犀助手 - 闲鱼无障碍自动化

## 功能
通过Android无障碍服务操控闲鱼APP，突破ADB反爬限制。

## 能做什么
- ✅ 自动点击任意UI元素（包括自定义组件）
- ✅ 自动输入文字到任意输入框
- ✅ 自动滑动/长按/手势操作
- ✅ 读取完整UI元素树
- ✅ 通过HTTP API远程控制

## 项目结构
```
app/src/main/
├── java/com/lingxi/xianyuauto/
│   ├── AutoService.kt      # 核心无障碍服务（HTTP服务器+UI操作）
│   └── MainActivity.kt     # 启动Activity（引导开启无障碍权限）
├── res/
│   └── xml/accessibility_config.xml  # 无障碍服务配置
└── AndroidManifest.xml
xianyu_controller.py         # Termux端Python控制器
```

## 安装步骤

### 方式1：本地构建
```bash
cd projects/xianyu-auto
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 方式2：GitHub Actions构建
1. Push到GitHub
2. Actions自动构建
3. 下载APK安装到手机

### 安装后配置
1. 打开App → 点击「开启无障碍服务」
2. 在系统设置中找到「灵犀助手」→ 开启
3. 返回App，确认状态显示「✅ 无障碍已开启」

## 使用方法

### Termux端
```bash
# 安装依赖
pip install requests

# 检查服务状态
python3 xianyu_controller.py status

# 获取UI元素树
python3 xianyu_controller.py tree

# 点击元素
python3 xianyu_controller.py click "卖闲置"
python3 xianyu_controller.py click 720 1000

# 输入文字
python3 xianyu_controller.py input "你好"

# 滑动
python3 xianyu_controller.py scroll down

# 一键发布服务
python3 xianyu_controller.py publish
```

### HTTP API
```
GET  http://localhost:8848/status      # 服务状态
GET  http://localhost:8848/tree        # UI元素树
GET  http://localhost:8848/click?text=xxx   # 点击文本
GET  http://localhost:8848/click?x=100&y=200 # 点击坐标
POST http://localhost:8848/input       # {"text": "你好"}
POST http://localhost:8848/scroll      # {"direction": "down"}
GET  http://localhost:8848/back        # 返回
POST http://localhost:8848/find        # 查找元素
POST http://localhost:8848/gesture     # 手势操作
```

## 为什么需要这个
闲鱼使用自定义组件，检测ADB自动化输入（标记INJECT_SOURCE_AUTOMATED）并直接屏蔽。
无障碍服务使用系统级performAction() API，无法被App检测和屏蔽。

## 支持的App
默认只监控闲鱼（com.taobao.idlefish）。
修改 accessibility_config.xml 中的 packageNames 可支持其他App。
