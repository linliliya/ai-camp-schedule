# SOP 入库 skill Implementation Plan

**Goal:** 将已确认的轻量入库规范安装为可调用的 `sop-intake` skill。

**Architecture:** skill 保存整理决策，项目引用文件定位当前 SOP 与工作流程；项目维护规范保存人可读的内容格式。沿用原有人工确认和飞书同步边界。

**Tech Stack:** Markdown、YAML、skill-creator Python 校验器。

用户已确认方案，本会话直接实施；不提交、推送或部署。

## 实施步骤

- [x] 创建 `C:/Users/asuka/.codex/skills/sop-intake/SKILL.md`，涵盖来源核对、轻量格式、原文保留、确认后修改和结果核验。
- [x] 创建 `references/project-context.md`，定位项目、当前规范、话术及排期导出流程；生成中文 `agents/openai.yaml`。
- [x] 更新 `AI编程SOP/维护与入库/本地SOP维护规范.md` 的格式约定，在 `docs/weekly-sop-processing.md` 加入 skill 调用示例。
- [x] 运行 `quick_validate.py`，检查 YAML、相对引用、代码块及新旧规则是否一致；只报告实际验证范围。

## 验收情景

- 一条新话术：展示目标章节与完整代码块，不擅自改写，不额外创建话术总库。
- 一条与当前时间规则冲突的素材：展示差异、保留待确认，不覆盖已确认规则。
- 只有文字调整：不改排期；任务或时间调整：列明客户端影响并按批准范围验证。
- 看板无权读取或图片不可见：说明缺失范围，可继续处理已读取材料，不声称全批完成。

## 实际验证

quick_validate.py：Skill is valid!。界面 YAML、默认调用名称、自动发现设置、相对引用、7 个项目文件引用与 4 份 Markdown 的代码围栏及脚手架检查通过；已对照备份审阅两份项目规范的差异。上述验收情景已按指令走查，未连接真实看板做端到端入库测试。

原规范备份：local-sop/.state/ai-backups/2026-10-28-sop-intake-547183e5/。
