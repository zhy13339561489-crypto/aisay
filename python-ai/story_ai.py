# 故事 AI 兼容门面模块
# 本文件是 story_ai_pkg 包的兼容性转发层，保持外部导入接口不变。
# 实际实现位于 story_ai_pkg/ 包中，按职责拆分为多个模块：
#   - models.py:      所有 Pydantic 数据模型
#   - formatters.py:  文本格式化工具函数
#   - templates.py:   PromptTemplate 初始化
#   - outline.py:     剧情大纲生成与修改
#   - volumes.py:     分卷大纲生成、修改与正文生成
#   - sections.py:    分卷小节生成
#   - assets.py:      人物/场景资产识别与豆包图片生成
#   - scripts.py:     分镜脚本生成
#   - router.py:      FastAPI 路由挂载
#
# main.py 和 rabbitmq_worker.py 通过 from story_ai import ... 导入，
# 无需修改导入路径即可使用拆分后的功能。

from story_ai_pkg import *
