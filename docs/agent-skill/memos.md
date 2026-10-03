# 随口记 API

## 文字随口记

第三方 Agent 只创建 `TEXT` 类型：

```json
{"memo":{"type":"TEXT","bodyText":"内容","durationMs":0,"asTodo":false}}
```

- `createQuickMemo`：`{"memo":MEMO}`，返回数字 `id`。
- `getQuickMemo`：`{"id":123}`。
- `queryQuickMemos`：`{"type":"TEXT","text":可选,"todoState":可选,"limit":50}`。字段均可省略。
- `updateQuickMemo`：`{"id":123,"patch":{"bodyText":"新内容","todoState":"ACTIVE"}}`。可只传一个修改字段。
- `setQuickMemoPinned`：`{"id":123,"pinned":true}`。
- `deleteQuickMemo`：`{"id":123}`。

`todoState` 使用 `NONE`、`ACTIVE` 或 `COMPLETED`。

## 第三方媒体限制

以下方法需要文件传输，第三方 Agent 标记 `UNSUPPORTED`：

- `attachQuickMemoImage`
- `removeQuickMemoImage`
- `attachQuickMemoVoice`

不要创建 `IMAGE` 或 `VOICE` 类型随口记。官方同签名 Agent 才能完整处理媒体。
