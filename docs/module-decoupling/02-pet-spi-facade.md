# Pet Bridge SPI + PetFacade

选型：**Bridge SPI + PetFacade**（与 Knowledge↔Blog 的 `NoteBlogPublisher` 同一模式）。

Pet 设备端聊天 / 日记 / TTS 走独立 Controller，但业务实现仍复用网站 chat/diary/tts 模块。Facade 始终作为 platform Bean 存在；模块关闭时对应 Controller 与真实 Bridge 不注册，由 NoOp Bridge 给 Facade 注入，避免启动 NPE。本期不引入 `spring.main.allow-circular-references`。

## 分层

```text
Pet*Controller  (@ConditionalOnModule)
    └── UserService.getLoginUser   // 鉴权留在 Controller
    └── PetFacade                  // platform，始终注册；只注入 Bridge SPI
            ├── PetChatBridge  → PetChatBridgeImpl | NoOpPetChatBridge
            ├── PetDiaryBridge → PetDiaryBridgeImpl | NoOpPetDiaryBridge
            └── PetTtsBridge   → PetTtsBridgeImpl | NoOpPetTtsBridge
```

| 模块关 (`app.modules.<m>=false`) | 不注册 | 仍注册 |
| --- | --- | --- |
| `chat=false` | `PetChatController`、`PetChatBridgeImpl` | `NoOpPetChatBridge`、`PetFacade` |
| `diary=false` | `PetDiaryController`、`PetDiaryBridgeImpl` | `NoOpPetDiaryBridge`、`PetFacade` |
| `tts=false` | `PetTtsController`、`PetTtsBridgeImpl` | `NoOpPetTtsBridge`、`PetFacade` |

NoOp 使用 `@ConditionalOnProperty(name="app.modules.<m>", havingValue="false")`，与 `NoOpNoteBlogPublisher` 一致。写/流式方法抛 `OPERATION_ERROR`（`chat/diary/tts 模块未启用`）；列表类方法返回空集合或 `null`。

PetFacade **不** `@Resource` chat/diary/tts 业务 Service。网站原有 `ChatController` / `DiaryEntryController` / `TtsController` 不变。

## 鉴权

能力接口接受 `UserService.getLoginUser` 解析出的身份（当前为 Session）。Facade 业务方法不读 Session、不做设备绑定校验。后续 Device Bearer 过滤器只需写入同一登录上下文，不必改 Facade。设备绑定管理（Session-only）不在本阶段。

## 未做

- PetDevice token 产品（表 / Filter / 绑定 UI）
- worklog：主站尚无 `WorkLogEntryService`，Pet worklog 待模块迁入后再加 Bridge
