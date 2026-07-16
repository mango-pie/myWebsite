# 04 V2：真实搜索与批量精炼

> **推荐路径（方案 B）**：DeepSeek 官方联网找文 → 勾选 → 合蒸一篇 → 博客/知识库。  
> 联调见 [06-v2-frontend-sync.md](./06-v2-frontend-sync.md)。

## 产品四步

1. 告诉 AI 要学什么  
2. 联网搜索（DeepSeek web_search，类网页版）→ 列表供预览勾选  
3. DeepSeek 读取勾选网页，物化材料包；拼接后按 **自定义提示词** 一次重构成一篇完整文章  
4. 人工确认后发博客 / 入知识库  

Tavily 仍可作为 `search.provider=tavily` 备用，默认应为 `deepseek`。
搜索与读页使用 `search-model` / `reading-model`（默认均为 `deepseek-v4-flash`）；精读重构使用 `distill-model`（默认 `deepseek-v4-pro`）。

## 验收

- 配置 DeepSeek 官方 Key 后能出文章候选 URL  
- 勾选后只生成一篇 note  
- 合蒸不再先逐篇摘要；`SourceDocument.rawText` 保存拼接后的材料包  
- 该 note 可发博客、可入知识库  
- 改 `reading.distill.system_prompt` 或请求体 `distillPrompt` 能影响最终精读结构  
