# 模块解耦

本目录是 **Ai-Backend 模块开关与解耦** 的权威文档入口（单体可插拔，非微服务）。

| 文档 | 说明 |
| --- | --- |
| [00 设计清单](./00-design-checklist.md) | 目标、模块划分、开关契约、SPI、验收 |
| [01 前端对接](./01-frontend-integration.md) | `GET /api/app/modules`、菜单/路由隐藏 |
| [02 Pet SPI + Facade](./02-pet-spi-facade.md) | Pet 聊天/日记/TTS：Bridge SPI + PetFacade，模块关闭走 NoOp |

相关：

- 根目录 [README](../../README.md)：Java 17 / Maven / 端口 8123 / `/api` / Session / 编译启停开关
- [模块 → Schema](../../src/main/resources/sql/README-modules.md)
