#!/usr/bin/env python3
"""
灵犀助手 - Termux端控制器
通过HTTP与手机上的AccessibilityService通信
"""

import requests
import json
import time
import sys
import base64

class XianyuAuto:
    def __init__(self, host="localhost", port=8848):
        self.base = f"http://{host}:{port}"
    
    def status(self):
        """检查服务状态"""
        return self._get("/status")
    
    def get_tree(self):
        """获取UI元素树"""
        return self._get("/tree")
    
    def find(self, text=None, clickable=None, desc=None):
        """查找元素"""
        data = {}
        if text: data["text"] = text
        if clickable is not None: data["clickable"] = clickable
        if desc: data["desc"] = desc
        return self._post("/find", data)
    
    def click(self, text=None, x=None, y=None):
        """点击"""
        if text:
            return self._get(f"/click?text={requests.utils.quote(text)}")
        elif x is not None and y is not None:
            return self._get(f"/click?x={x}&y={y}")
        else:
            raise ValueError("需要text或x,y参数")
    
    def long_click(self, x, y):
        """长按"""
        return self._get(f"/longclick?x={x}&y={y}")
    
    def input_text(self, text):
        """输入文字"""
        return self._post("/input", {"text": text})
    
    def scroll(self, direction="down", distance=500):
        """滑动"""
        return self._post("/scroll", {"direction": direction, "distance": distance})
    
    def back(self):
        """返回"""
        return self._get("/back")
    
    def home(self):
        """回桌面"""
        return self._get("/home")
    
    def gesture(self, gesture_type, x1=None, y1=None, x2=None, y2=None, duration=300):
        """手势操作"""
        data = {"type": gesture_type}
        if x1 is not None: data["x1"] = x1
        if y1 is not None: data["y1"] = y1
        if x2 is not None: data["x2"] = x2
        if y2 is not None: data["y2"] = y2
        data["duration"] = duration
        return self._post("/gesture", data)
    
    # ==================== 闲鱼专用操作 ====================
    
    def open_xianyu(self):
        """打开闲鱼"""
        import subprocess
        subprocess.run(["adb", "shell", "monkey", "-p", "com.taobao.idlefish", 
                        "-c", "android.intent.category.LAUNCHER", "1"])
        time.sleep(3)
        return {"success": True}
    
    def publish_service(self, title, description, price, duration="1-5天"):
        """
        发布闲鱼服务（完整流程）
        
        这个方法处理闲鱼发布服务的所有步骤：
        1. 点击"卖闲置"
        2. 点击"发服务"
        3. 处理权限/推荐页
        4. 输入描述
        5. 选择工期
        6. 选择计价方式
        7. 设置价格
        8. 选择位置
        9. 上传图片
        10. 点击发布
        """
        results = []
        
        # 1. 点击卖闲置
        print("📌 步骤1: 点击卖闲置...")
        r = self.click(text="卖闲置")
        results.append({"step": "卖闲置", "result": r})
        time.sleep(2)
        
        # 2. 点击发服务
        print("📌 步骤2: 点击发服务...")
        r = self.click(text="发服务")
        results.append({"step": "发服务", "result": r})
        time.sleep(2)
        
        # 3. 处理权限弹窗
        print("📌 步骤3: 检查权限弹窗...")
        tree = self.get_tree()
        tree_str = json.dumps(tree)
        if "仅在使用中允许" in tree_str:
            r = self.click(text="仅在使用中允许")
            results.append({"step": "权限", "result": r})
            time.sleep(2)
        
        # 4. 处理推荐页
        if "跳过" in json.dumps(self.get_tree()):
            print("📌 步骤4: 跳过推荐页...")
            r = self.click(text="跳过")
            results.append({"step": "跳过推荐", "result": r})
            time.sleep(2)
        
        # 5. 处理旧草稿
        tree_str = json.dumps(self.get_tree())
        if "放弃" in tree_str:
            print("📌 步骤5: 放弃旧草稿...")
            r = self.click(text="放弃")
            results.append({"step": "放弃草稿", "result": r})
            time.sleep(2)
        
        # 6. 点击描述区域并输入
        print("📌 步骤6: 输入描述...")
        r = self.click(x=720, y=1100)
        time.sleep(0.5)
        r = self.input_text(description)
        results.append({"step": "输入描述", "result": r})
        time.sleep(2)
        
        # 7. 选择工期
        print("📌 步骤7: 选择工期...")
        duration_coords = {
            "1-5天": (441, 2191),
            "5-10天": (681, 2191),
            "10-15天": (945, 2191),
            "15天以上": (1230, 2191),
        }
        if duration in duration_coords:
            x, y = duration_coords[duration]
            r = self.click(x=x, y=y)
            results.append({"step": f"工期{duration}", "result": r})
            time.sleep(0.5)
        
        # 8. 选择计价方式：元/起
        print("📌 步骤8: 选择计价方式...")
        r = self.click(x=669, y=2595)
        results.append({"step": "计价方式", "result": r})
        time.sleep(0.5)
        
        # 9. 等待AI生成标题
        print("📌 步骤9: 等待AI生成标题...")
        time.sleep(3)
        
        # 10. 设置价格
        print("📌 步骤10: 设置价格...")
        # 先找到价格区域并点击
        r = self.click(x=720, y=2400)
        time.sleep(2)
        # 输入价格数字
        self.click(x=540, y=2829)  # 8
        time.sleep(0.3)
        self.click(x=540, y=3039)  # 0
        time.sleep(0.3)
        # 确认
        r = self.click(x=1260, y=2934)
        results.append({"step": "价格", "result": r})
        time.sleep(1)
        
        return {
            "success": True,
            "title": title,
            "price": price,
            "steps": results,
            "note": "位置选择和图片上传需手动完成，完成后点击发布即可"
        }
    
    # ==================== HTTP方法 ====================
    
    def _get(self, path):
        try:
            r = requests.get(f"{self.base}{path}", timeout=10)
            return r.json()
        except Exception as e:
            return {"error": str(e)}
    
    def _post(self, path, data):
        try:
            r = requests.post(f"{self.base}{path}", json=data, timeout=10)
            return r.json()
        except Exception as e:
            return {"error": str(e)}


# ==================== CLI入口 ====================

if __name__ == "__main__":
    auto = XianyuAuto()
    
    if len(sys.argv) < 2:
        print("""
🔮 灵犀助手 - 闲鱼自动化控制器

用法:
  python3 xianyu_controller.py status          # 检查服务状态
  python3 xianyu_controller.py tree            # 获取UI树
  python3 xianyu_controller.py find <文本>      # 查找元素
  python3 xianyu_controller.py click <文本>     # 点击文本
  python3 xianyu_controller.py click <x> <y>    # 点击坐标
  python3 xianyu_controller.py input <文字>     # 输入文字
  python3 xianyu_controller.py scroll <方向>    # 滑动(up/down/left/right)
  python3 xianyu_controller.py back             # 返回
  python3 xianyu_controller.py open             # 打开闲鱼

发布服务:
  python3 xianyu_controller.py publish
        """)
        sys.exit(0)
    
    cmd = sys.argv[1]
    
    if cmd == "status":
        print(json.dumps(auto.status(), indent=2, ensure_ascii=False))
    elif cmd == "tree":
        print(json.dumps(auto.get_tree(), indent=2, ensure_ascii=False))
    elif cmd == "find" and len(sys.argv) > 2:
        print(json.dumps(auto.find(text=sys.argv[2]), indent=2, ensure_ascii=False))
    elif cmd == "click":
        if len(sys.argv) == 3:
            print(json.dumps(auto.click(text=sys.argv[2]), indent=2, ensure_ascii=False))
        elif len(sys.argv) == 4:
            print(json.dumps(auto.click(x=int(sys.argv[2]), y=int(sys.argv[3])), indent=2, ensure_ascii=False))
    elif cmd == "input" and len(sys.argv) > 2:
        text = " ".join(sys.argv[2:])
        print(json.dumps(auto.input_text(text), indent=2, ensure_ascii=False))
    elif cmd == "scroll" and len(sys.argv) > 2:
        print(json.dumps(auto.scroll(direction=sys.argv[2]), indent=2, ensure_ascii=False))
    elif cmd == "back":
        print(json.dumps(auto.back(), indent=2, ensure_ascii=False))
    elif cmd == "open":
        print(json.dumps(auto.open_xianyu(), indent=2, ensure_ascii=False))
    elif cmd == "publish":
        # 示例发布
        result = auto.publish_service(
            title="专业商业文案策划",
            description="专业商业文案策划服务！\n1. 服务流程：沟通需求→撰写初稿→确认修改→定稿交付\n2. 服务时长：1-3天\n3. 计费方式：单条产品描述¥80，详情页全套¥150\n4. 个人优势：10年+商业文案经验",
            price=80,
            duration="1-5天"
        )
        print(json.dumps(result, indent=2, ensure_ascii=False))
    else:
        print(f"未知命令: {cmd}")
