# Spring Boot 集成 OpenAI API：从零搭建一个 AI 对话服务

> **作者**：苏游（王星凯）  
> **技术栈**：Spring Boot 3.4.1 + Spring AI 1.0.0-M6 + Java 24 + Maven  
> **项目源码**：[GitHub - java-ai-lab](https://github.com/suyou/java-ai-lab)（待补充仓库地址）

---

## 前言

这是我学习 Java + AI 的第二篇实战笔记。上一篇文章《Java 开发者如何入门 AI：路线图与工具链概览》梳理了学习路径，这篇直接进入代码——手把手带你用 Spring Boot + Spring AI 搭建一个**带流式输出、对话记忆的 AI 聊天接口**。

我会把整个过程中踩的坑、不理解的地方、以及最终怎么解决的，全部记录下来。如果你也在学 Spring AI，希望这篇笔记能帮你少走弯路。

---

## 一、环境准备

### 1.1 项目创建

```bash
# 使用 Spring Initializr 或 IDEA 创建项目
# 依赖：Spring Web, Spring AI OpenAI
```

`pom.xml` 关键依赖：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>1.0.0-M6</version>
</dependency>
```

> ⚠️ **注意**：我用的版本是 `1.0.0-M6`（里程碑版），不是正式版。这个版本的 API 和正式版有差异，后面会讲到踩的坑。

### 1.2 配置 API Key

生产环境应该走配置中心（Nacos / ACM）+ 密钥管理服务（KMS）。学习阶段可以直接在 IDEA 的 Run Configuration 里设置环境变量：

```
Name: AI_API_KEY
Value: sk-ws-H.PIYYPMP.rkLq.MEUCIAjVJRJ2V6tDY9m9XNgXRlBZzIZ9CCUWLQALRjXaIYLLAiEAmTCna1GO_O3Wme1xTAizxM9UYcCPksJv9pHKpIkhbFQ
```

`application.yml` 里只写占位符：

```yaml
spring:
  ai:
    openai:
      base-url: https://dashscope.aliyuncs.com/compatible-mode
      api-key: ${AI_API_KEY}
      chat:
        options:
          model: qwen3.8-omni-flash
```

> 💡 **一个 API Key 通吃百炼所有模型**。Key 是你的"身份凭证 + 计费账户"，切换模型只需要改 yml 里的 `model` 字段，不需要换 Key。

---

## 二、第一个同步接口

先跑通最基础的 GET 接口，验证链路是否通畅：

```java
@RestController
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam(defaultValue = "你好") String message) {
        try {
            return chatClient.prompt()
                    .user(message)
                    .call()
                    .content();
        } catch (Exception e) {
            return "❌ " + e.getMessage();
        }
    }
}
```

浏览器访问 `http://localhost:8080/chat?message=你好`，看到回复就说明基础链路通了。

![第一次调通](docs/images/image-1790057665754-bvweevdlu3r.png)

---

## 三、踩坑记录

### 坑 1：404 NonTransientAiException

**现象**：调用报 `NonTransientAiException 404`

**排查过程**：
1.  一开始以为是模型名称不对，把 `qwen3.8-omni-flash` 改成 `qwen-plus` 还是 404
2.  用 curl 直接调百炼 API 能通，说明 Key、URL、模型都没问题
3.  最后发现是 **Spring AI M6 对 base-url 的处理逻辑有问题**

**根因**：Spring AI 的 `OpenAiApi` 内部会自动拼接 `/chat/completions`。我的 base-url 写了 `https://dashscope.aliyuncs.com/compatible-mode/v1`，拼完变成 `.../compatible-mode/v1/chat/completions`。但 M6 版本会把 base-url 里的 `/v1` 去掉再拼，导致最终路径变成 `.../compatible-mode/chat/completions`（少了 `/v1`），所以 404。

**解决**：base-url 不要带 `/v1`，让 Spring AI 自己拼：

```yaml
spring:
  ai:
    openai:
      base-url: https://dashscope.aliyuncs.com/compatible-mode  # 去掉 /v1
```

### 坑 2：CONVERSATION_ID 常量不存在

**现象**：IDEA 里 `ChatMemory.CONVERSATION_ID` 标红报错

![CONVERSATION_ID 报错](docs/images/image-1790058020829-atgrwzndfim.png)

**根因**：Spring AI M6 的 `ChatMemory` 接口里没有定义 `CONVERSATION_ID` 常量。正式版才有。

**解决**：直接用字符串 `"chat_memory_conversation_id"`：

```java
.advisors(a -> a.param("chat_memory_conversation_id", request.sessionId()))
```

### 坑 3：SSE 流式输出格式不兼容

**现象**：前端收到的流式数据解析出来全是 `data:` 前缀，文字显示成 `data:你好！有什么data:我可以帮你的吗data:?`

![data: 前缀显示](docs/images/image-1790059004165-u0ntf4ryg6g.png)

**根因**：Spring AI M6 返回的 SSE 格式是 `data:xxx`（冒号后无空格），而前端代码判断的是 `startsWith('data: ')`（有空格），匹配不上就走到了 else 分支，把整行当纯文本显示了。

**解决**：改为 `startsWith('data:')`，然后用 `slice(5).trimStart()` 剥离前缀：

```javascript
if (trimmed.startsWith('data:')) {
    assistantDiv.textContent += trimmed.slice(5).trimStart();
}
```

### 坑 4：样式丑、分不清谁发的消息

**现象**：所有消息堆在一起，看不出哪条是用户问的、哪条是 AI 回的

![样式调整前](docs/images/image-1790059104667-myq6lwb21al.png)

**解决**：重写成气泡式布局，用户消息右对齐蓝色气泡，AI 回复左对齐白色气泡，每条消息顶部加角色标签。

---

## 四、流式输出 + 对话记忆实现

### 4.1 会话记忆服务

Spring AI 内置了 `InMemoryChatMemory`，按 sessionId 自动管理消息列表，不用自己手写 Map：

```java
@Service
public class ChatMemoryService {
    private final ChatMemory chatMemory = new InMemoryChatMemory();

    public ChatMemory getMemory() {
        return chatMemory;
    }
}
```

### 4.2 流式接口

```java
@PostMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamChat(@RequestBody ChatRequest request) {
    ChatMemory memory = memoryService.getMemory();

    return chatClient.prompt()
            .user(request.message())
            .advisors(a -> a.param("chat_memory_conversation_id", request.sessionId()))
            .stream()
            .content();
}

record ChatRequest(String message, String sessionId) {}
```

关键点：
-   `.advisors(a -> a.param(...))` —— 让 Spring AI 自动读写对话历史
-   `.stream().content()` —— 返回 `Flux<String>`，每个 chunk 是一个 token
-   `produces = TEXT_EVENT_STREAM_VALUE` —— 告诉浏览器这是 SSE

### 4.3 前端页面

完整的 `chat.html` 包含：
-   气泡式消息布局（用户右蓝、AI 左白）
-   角色标签（"你"/"AI"）
-   输入框固定底部
-   防重复发送（流式期间按钮禁用）
-   自动滚动到底部
-   SSE 解析兼容 M6 格式

最终效果：

![最终效果](docs/images/image-1790059564730-jaqvrk0f1mf.png)

Network 面板可以看到 SSE 事件流逐条到达：

![EventStream 面板](docs/images/image-1790059564730-jaqvrk0f1mf.png)

---

## 五、关于 Spring AI 依赖注入的疑问

**问**：调用 Spring AI 不能直接依赖注入吗？一定要通过 Builder 再 build() 吗？

**答**：Spring AI M6 只提供了 `ChatClient.Builder` 的 Bean，没有直接提供 `ChatClient` Bean。所以你不能这样写：

```java
@Autowired
private ChatClient chatClient; // ❌ M6 里没有这个 Bean
```

必须通过 Builder 构建：

```java
public ChatController(ChatClient.Builder builder) {
    this.chatClient = builder.build();
}
```

这不是"new 一个裸对象"，而是由 Spring 管理的生命周期。Builder 已经自动配置好了 API Key、base-url、默认 model，`build()` 只是组装。

升级到 Spring AI 1.0+ 正式版后，就可以直接 `@Autowired ChatClient` 了。

---

## 六、代码提交到 GitHub

### 6.1 初始化 Git 仓库

```bash
cd /Users/suyou/IdeaProjects/suyou/java-ai-lab
git init
```

### 6.2 创建 .gitignore

```gitignore
# Maven
target/
!.mvn/wrapper/maven-wrapper.jar

# IDE
.idea/
*.iml
*.iws
*.ipr

# OS
.DS_Store
Thumbs.db

# 敏感文件
.env
application-local.yml
```

### 6.3 提交代码

```bash
git add .
git commit -m "feat: Spring Boot + Spring AI 流式对话服务（含对话记忆）"
```

### 6.4 关联远程仓库并推送

```bash
# 先在 GitHub 上创建一个空仓库，然后执行：
git remote add origin https://github.com/suyou/java-ai-lab.git
git branch -M main
git push -u origin main
```

> ⚠️ **注意**：提交前确认 `.gitignore` 已生效，不要把 `application.yml` 里的 API Key 或 IDEA 的 `AI_API_KEY` 环境变量提交上去。API Key 只存在于你本地 IDEA 配置里，不会进 Git。

---

## 七、总结

这篇文章记录了从零搭建 Spring Boot AI 对话服务的完整过程，包括：

-   ✅ 同步接口验证链路
-   ✅ 流式输出（SSE）实现
-   ✅ 对话记忆（多轮上下文）
-   ✅ 前端气泡式聊天界面
-   ✅ 4 个踩坑记录和解决方案

整个过程最大的收获是：**Spring AI M6 作为里程碑版本，API 和正式版有差异，遇到报错不要慌，先看源码或查文档确认当前版本的正确用法**。

下一步计划：
-   [ ] 升级 Spring AI 到正式版
-   [ ] 接入数据库持久化对话历史
-   [ ] 加用户认证和权限控制

如果你觉得这篇笔记对你有帮助，欢迎在评论区交流～

---

**系列文章**：
1.  [Java 开发者如何入门 AI：路线图与工具链概览](#)
2.  Spring Boot 集成 OpenAI API：从零搭建一个 AI 对话服务（本文）
