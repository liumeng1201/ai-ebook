---
title: "7.0 跑通你的第一个全栈应用"
description: "从零到一跑通增删改查，验证全栈数据流"
chapter: "第七章"
---

# 7.0 跑通你的第一个全栈应用

> **本节目标**：让 AI 帮你生成一个完整的 CRUD（增删改查）应用，跑起来，验证数据是否正确。

你在第六章拿到了数据库，在序言里理解了 API 的概念。现在把它们串起来——做一个最经典的 **Todo 待办事项**应用。

## 全栈数据流：一张图看懂

在动手之前，先理解数据是怎么流动的：

```
用户点击"添加" → 前端发送请求 → 后端 API 接收 → Drizzle 写入数据库
                                                        ↓
用户看到新数据 ← 前端渲染响应 ← 后端返回结果 ← 数据库返回确认
```

这就是全栈应用的核心循环。CRUD 四个操作，每一个都走这条路。

<FullStackFlow mode="dataflow" />

## 告诉 AI 创建项目

你不需要手写这些代码。直接告诉 AI：

> "帮我创建一个 Todo 应用，使用 Next.js + Drizzle ORM + PostgreSQL。需要完整的 CRUD 功能：添加待办、查看列表、标记完成、删除待办。数据库连接用 .env 里的 DATABASE_URL。"

AI 会帮你生成以下文件结构：

```
src/
├── db/
│   ├── index.ts          # 数据库连接
│   └── schema.ts         # 表结构定义
├── app/
│   ├── api/todos/
│   │   └── route.ts      # API 路由（处理 CRUD 请求）
│   └── page.tsx           # 前端页面（展示和操作）
```

## 检查 AI 的作业

AI 生成了三类文件。你不需要看懂每一行代码，但要知道每个文件的职责——这样出问题时你才知道该让 AI 改哪个文件。

### 1. 数据库表结构（schema.ts）

这个文件定义了"数据长什么样"——Todo 有哪些字段、每个字段是什么类型。相当于数据库的设计图纸。

<details>
<summary>好奇的话展开看看，不看也完全没问题</summary>

```typescript
// src/db/schema.ts
export const todos = pgTable('todos', {
  id: serial('id').primaryKey(),
  title: text('title').notNull(),
  completed: boolean('completed').default(false),
  createdAt: timestamp('created_at').defaultNow(),
})
```

对照第六章学的知识：`id` 是主键，`title` 不能为空，`completed` 默认是 `false`，`createdAt` 自动记录创建时间。

</details>

### 2. API 路由（route.ts）

这个文件是"后端接口"——前端发请求到这里，它负责跟数据库交互。`POST` 对应"增"，`GET` 对应"查"，这就是序言里讲的 RESTful 设计。

<details>
<summary>好奇的话展开看看，不看也完全没问题</summary>

```typescript
// src/app/api/todos/route.ts

// C - Create（增）：添加一条待办
export async function POST(request: Request) {
  const { title } = await request.json()
  const newTodo = await db.insert(todos).values({ title }).returning()
  return Response.json(newTodo[0])
}

// R - Read（查）：获取所有待办
export async function GET() {
  const allTodos = await db.select().from(todos).orderBy(todos.createdAt)
  return Response.json(allTodos)
}
```

</details>

### 3. 前端页面（page.tsx）

这个文件是用户看到的界面——输入框、列表、勾选框、删除按钮。它通过调用上面的 API 来操作数据：

- 页面加载时，调用 `GET /api/todos` 获取列表
- 用户输入标题点"添加"，调用 `POST /api/todos`
- 用户点勾选框，调用 `PATCH /api/todos/[id]` 更新状态
- 用户点删除，调用 `DELETE /api/todos/[id]`

## 动手跑起来

现在让它真正跑起来。第一次跑不通是正常的——环境变量没配、表没推送、端口被占用，这些都是新手必经之路。把报错丢给 AI 就行。

**第一步：推送表结构到数据库**

```bash
pnpm drizzle-kit push
```

这条命令会把你在 `schema.ts` 里定义的表结构同步到云端数据库。

**第二步：启动开发服务器**

```bash
pnpm dev
```

打开 `http://localhost:3000`，你应该能看到一个空的待办列表。

**第三步：试试 CRUD**

| 操作 | 你做什么 | 背后发生了什么 |
|------|---------|--------------|
| 添加 | 输入"学习数据库"，点添加 | `POST /api/todos` → 数据库插入一行 |
| 查看 | 页面自动刷新列表 | `GET /api/todos` → 数据库查询所有行 |
| 完成 | 点击勾选框 | `PATCH /api/todos/1` → 数据库更新 `completed` 字段 |
| 删除 | 点击删除按钮 | `DELETE /api/todos/1` → 数据库删除该行 |

## 用 Drizzle Studio 验证

想亲眼看看数据库里发生了什么？

```bash
pnpm drizzle-kit studio
```

打开后你能看到 `todos` 表里的每一行数据，和你在页面上操作的结果完全对应。

这就是你作为产品负责人的核心技能——不是读代码，而是验证结果。数据库里的数据对不对、API 返回的格式对不对、页面显示的内容对不对。能验证这三件事，你就能放心地让 AI 帮你写后端。

## 常见问题

**Q：添加后列表没更新？**
告诉 AI："添加待办后自动刷新列表，不需要手动刷新页面。"AI 会在 POST 请求成功后重新获取数据。

**Q：删除报错 404？**
检查 API 路由文件的路径是否正确。Next.js 的动态路由需要 `[id]` 文件夹：`app/api/todos/[id]/route.ts`。

**Q：数据库里没有表？**
确认执行了 `pnpm drizzle-kit push`。如果报错，检查 `.env` 里的 `DATABASE_URL` 是否正确。

## 这个示例教会你什么

你刚才完成的，是一个**最小但完整的全栈应用**。它包含了：

- **前端**：用户界面、事件处理、API 调用
- **后端**：API 路由、请求处理、数据校验
- **数据库**：表结构、CRUD 操作、数据持久化

以后不管做什么应用——博客、电商、社交——底层逻辑都是这四个字母：**CRUD**。区别只是表结构更复杂、业务逻辑更多、界面更花哨。

---

::: info 下一步
CRUD 跑通了，你已经体验了全栈数据流。接下来去 [一个接口不够用了](./01-api-growing-pains.md)——当数据量和需求增长时，一个简单的 CRUD 接口该怎么进化。
:::
