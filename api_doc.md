概述
SoruxGPT 自建海外高速服务器集群，为你提供最流畅的 ChatGPT API 服务。同时，通过我们的智能负载均衡算法，为你的每一次请求提供极致的响应体验。
API 区别
- GPT 高速聊天专用接口：通过多机房异地互联，我们为 GPT 聊天接口做了专门的优化，使得你的每一次请求拥有高速的响应。
- GPT 全功能接口：通过自建服务器群，为 GPT 全功能做了 API 封装，以官网的 API 模式调用 ChatGPT Api。
  对于通用功能，如聊天接口等，可以直接替换两个接口切换，无需变更任何代码
- MidJourney 接口：通过自建服务器群，为 MidJourney  全功能做了 API 封装，以官网的 API 模式调用 MidJourney Api。
  ChatGPT API 文档
  SoruxGPT 完全仿照 OpenAI 官网设定，你可以直接在 OpenAI API 文档查阅如何使用：https://platform.openai.com/docs/api-reference/introduction （需翻墙）
  ChatGPT API 例子
  你需要自行在 Header 部分完成鉴权：
  Authorization: Bearer sk-xxxx
  绘画
  地址：https://gpt.soruxgpt.com/api/proxy/v1/images/generations
  请求体：
  {
  "model": "gpt-4-s",
  "prompt": "",
  }
  对话
  地址：https://gpt.soruxgpt.com/api/api/v1/chat/completions
  请求体：
  {
  "model": "gpt-3.5-turbo",
  "messages": [
  {
  "role": "system",
  "content": "You are a helpful assistant."
  },
  {
  "role": "user",
  "content": "Hello!"
  }
  ]
  }
  同时，你也可以进行流请求（返回多次响应，每次返回一个字或几个字）
  {
  "model": "gpt-3.5-turbo",
  "messages": [
  {
  "role": "system",
  "content": "You are a helpful assistant."
  },
  {
  "role": "user",
  "content": "Hello!"
  }
  ],
  "stream": true
  }
  你的模型需要根据官网文档来进行配置
  MidJourney API 使用
  如果你只是需要使用 MidJourney 功能的话，请直接看本节内容：
1. 打开 SoruxGPT-MidJourney
2. 填写 API
   [图片]
   其中，MidJourney Api Secret 为你的 SoruxGPT 密钥
3. 使用即可
   MidJourney 中文版教程：访问地址
   Prompt 使用实现注意
   对开发者
   MidJourney 推荐 Prompt 是英文，因此你最好使用第三方的翻译软件、OpenAI 3.5-turbo API 将英文转换为中文后再输入给 MidJourney
   对个人用户
   如果你拥有 SoruxGPT ChatGPT 节点：
1. 打开 GPT-S，搜索 MidJourney
   [图片]
2. 例如，我们使用 MJ v6.0 作画
   部分用户打开 GPTS 可能会出现无法显示，刷新即可
   输入我们的 Prompt
   [图片]
   例如我们复制一个 Prompt：A young girl standing under the sunlight, holding a red umbrella with an inverted hook handle, a bright blue sky filled with fluffy clouds above her. She smiles warmly towards the viewer, a loyal dog leaning against her feet. The scene radiates warmth and serenity. Created Using: vivid colors, soft lighting, gentle shadows, detailed textures, lifelike expressions, naturalistic animal depiction, harmonious composition, hd quality, natural look
3. 打开 SoruxGPT 绘画程序
   [图片]
4. 查看效果
   [图片]
   MidJourney API接口说明
1. 数据结构
   任务
   暂时无法在飞书文档外展示此内容
   Button
   暂时无法在飞书文档外展示此内容
   properties 常见字段
   暂时无法在飞书文档外展示此内容
   任务提交返回
- code=1: 提交成功，result为任务ID
  {"code": 1,"description": "提交成功","result": "14001929738841620","properties": {"discordInstanceId": "1118138338562560102"
  }
  }
- code=22: 提交成功，进入队列等待
  {"code": 22,"description": "排队中，前面还有1个任务","result": "14001929738841620","properties": {"numberOfQueues": 1,"discordInstanceId": "1118138338562560102"
  }
  }
- code=23: 队列已满，请稍后尝试
  {"code": 23,"description": "队列已满，请稍后尝试","result": "14001929738841620","properties": {"discordInstanceId": "1118138338562560102"
  }
  }
- code=24: prompt包含敏感词
  {"code": 24,"description": "可能包含敏感词","properties": {"promptEn": "nude body","bannedWord": "nude"
  }
  }
- other: 提交错误，description为错误描述
  执行任务的关联动作
  调用 /mj/submit/action，几乎所有的button都做了支持，除了以下情况:
- 图生文结果的 🎉Imagine all
- 图片放大后的 ❤️
  {
  // 关联任务的ID
  "taskId": "1689216801333574",
  // 动作标识
  "customId": "MJ::JOB::reroll::0::1c6dff5e-5632-40c6-9d4c-afb261705313::SOLO"
  }
  ⚠️ 注意: 某些场景需要modal弹框确认
- 执行CustomZoom(自定义变焦)
- 执行️Region(局部重绘)
- 执行PicReader(Describe后选择生图)
- 执行PromptAnalyzer(Shorten后选择生图)
  开启Remix模式时，执行Reroll、Variation、Pan也需要弹框确认；但账号设置了remix自动提交时，不需要确认
  需要确认时返回的code为 21，示例:
  {
  "code": 21,
  "description": "窗口等待",
  "result": "14001929738841620"
  }
  该任务状态为MODAL，但不会进队列影响并发。需调用/mj/submit/modal提交最终任务
  {
  // 需确认的任务ID
  "taskId": "1689228047868174",
  // prompt: 为空时使用原任务的prompt
  "prompt": "Cat"
  }
- CustomZoom的prompt需要设置--zoom(1到2之间)，例如: Cat --zoom 1.5
- ️Vary (Region) 需要额外传maskBase64: 局部重绘的蒙版base64(底色纯黑，选中区域纯白)
  /mj/submit/describe 图生文
  {
  // 图片的base64字符串
  "base64": "data:image/png;base64,xxx"
  }
  后续任务完成后，properties中finalPrompt即为图片生成的prompt，finalZhPrompt为翻译的中文
  {
  "id":"14001929738841620",
  "action":"DESCRIBE",
  "status": "SUCCESS",
  "description":"/describe 14001929738841620.png",
  "imageUrl":"https://cdn.discordapp.com/attachments/xxx/xxx/14001929738841620.png",
  "properties": {
  "finalPrompt": "1️⃣ Cat --ar 5:4\n\n2️⃣ Cat2 --ar 5:4\n\n3️⃣ Cat3 --ar 5:4\n\n4️⃣ Cat4 --ar 5:4",
  "finalZhPrompt": "1️⃣ 猫 --ar 5:4\n\n2️⃣ 猫2 --ar 5:4\n\n3️⃣ 猫3 --ar 5:4\n\n4️⃣ 猫4 --ar 5:4"
  }
  // ...
  }
  /mj/submit/shorten prompt分析
  {
  "prompt": "️appdash appdash, in the style of expert draftsmanship, commission for, ethereal, dreamlike quality, dadaistic, toonami"
  }
  后续任务完成后，properties中finalPrompt即为分析结果，finalZhPrompt为翻译的中文
  {
  "id":"1689252749098647",
  "action":"SHORTEN",
  "status": "SUCCESS",
  "description":"/shorten appdash appdash, in the style of expert draftsmanship, commission for, ethereal, dreamlike quality, dadaistic, toonami",
  "properties": {
  "finalPrompt": "## Important tokens\n**appdash** **appdash**, in the ~~style~~ of ~~expert~~ **draftsmanship**, commission for, ethereal, dreamlike quality, ~~dadaistic~~, **toonami**\n## Shortened prompts\n1️⃣ appdash appdash, draftsmanship, commission for, ethereal, toonami\n\n2️⃣ appdash appdash, draftsmanship, commission, toonami\n\n3️⃣ appdash appdash, draftsmanship, toonami\n\n4️⃣ appdash appdash, toonami\n\n5️⃣ appdash appdash",
  "finalZhPrompt": "## 重要词汇\n**appdash** **appdash**，以专家的绘画风格，委托制作，飘渺的，梦幻般的质感，达达主义的，**toonami**\n## 简化提示\n1️⃣ appdash appdash，绘画风格，委托制作，飘渺的，toonami\n\n2️⃣ appdash appdash，绘画风格，委托制作，toonami\n\n3️⃣ appdash appdash，绘画风格，toonami\n\n4️⃣ appdash appdash，toonami\n\n5️⃣ appdash appdash"
  }
  // ...
  }
  对该任务执行 Show Details 动作，能获得进一步的分析结果
  {
  "id":"1689253263953453",
  "action":"SHORTEN",
  "status": "SUCCESS",
  "description":"/up 168925266642808397 Show Details",
  "properties": {
  "finalPrompt": "## Important tokens\n**appdash** (1.00) **appdash** (0.79), in the style (0.01) of expert (0.00) **draftsmanship** (0.09), commission (0.08) for, ethereal (0.05), dreamlike (0.02) quality (0.01), dadaistic (0.01), **toonami** (0.19)\n\n██████████ appdash\n████████░░ appdash\n██░░░░░░░░ toonami\n█░░░░░░░░░ draftsmanship\n█░░░░░░░░░ commission\n█░░░░░░░░░ ethereal\n## Shortened prompts\n1️⃣ appdash appdash, draftsmanship, commission for, ethereal, toonami\n\n2️⃣ appdash appdash, draftsmanship, commission, toonami\n\n3️⃣ appdash appdash, draftsmanship, toonami\n\n4️⃣ appdash appdash, toonami\n\n5️⃣ appdash app",
  "finalZhPrompt": "## 重要的词语\n**appdash** (1.00) **appdash** (0.79)，以专家级(0.01) **绘画技巧** (0.09) 的风格，委托(0.08) 制作，飘渺的(0.05)，梦幻般的(0.02) 质感(0.01)，达达主义的(0.01)，**toonami** (0.19)\n\n██████████ appdash\n████████░░ appdash\n██░░░░░░░░ toonami\n█░░░░░░░░░ draftsmanship\n█░░░░░░░░░ commission\n█░░░░░░░░░ ethereal\n## 简化的提示\n1️⃣ appdash appdash，绘画技巧，委托制作，飘渺，toonami\n\n2️⃣ appdash appdash，绘画技巧，委托制作，toonami\n\n3️⃣ appdash appdash，绘画技巧，toonami\n\n4️⃣ appdash appdash，toonami\n\n5️⃣ appdash appdash"
  }
  // ...
  }
  /mj/submit/shorten prompt分析
  {
  "prompt": "️appdash appdash, in the style of expert draftsmanship, commission for, ethereal, dreamlike quality, dadaistic, toonami"
  }
  后续任务完成后，properties中finalPrompt即为分析结果，finalZhPrompt为翻译的中文
  {
  "id":"1689252749098647",
  "action":"SHORTEN",
  "status": "SUCCESS",
  "description":"/shorten appdash appdash, in the style of expert draftsmanship, commission for, ethereal, dreamlike quality, dadaistic, toonami",
  "properties": {
  "finalPrompt": "## Important tokens\n**appdash** **appdash**, in the ~~style~~ of ~~expert~~ **draftsmanship**, commission for, ethereal, dreamlike quality, ~~dadaistic~~, **toonami**\n## Shortened prompts\n1️⃣ appdash appdash, draftsmanship, commission for, ethereal, toonami\n\n2️⃣ appdash appdash, draftsmanship, commission, toonami\n\n3️⃣ appdash appdash, draftsmanship, toonami\n\n4️⃣ appdash appdash, toonami\n\n5️⃣ appdash appdash",
  "finalZhPrompt": "## 重要词汇\n**appdash** **appdash**，以专家的绘画风格，委托制作，飘渺的，梦幻般的质感，达达主义的，**toonami**\n## 简化提示\n1️⃣ appdash appdash，绘画风格，委托制作，飘渺的，toonami\n\n2️⃣ appdash appdash，绘画风格，委托制作，toonami\n\n3️⃣ appdash appdash，绘画风格，toonami\n\n4️⃣ appdash appdash，toonami\n\n5️⃣ appdash appdash"
  }
  // ...
  }
  对该任务执行 Show Details 动作，能获得进一步的分析结果
  {
  "id":"1689253263953453",
  "action":"SHORTEN",
  "status": "SUCCESS",
  "description":"/up 168925266642808397 Show Details",
  "properties": {
  "finalPrompt": "## Important tokens\n**appdash** (1.00) **appdash** (0.79), in the style (0.01) of expert (0.00) **draftsmanship** (0.09), commission (0.08) for, ethereal (0.05), dreamlike (0.02) quality (0.01), dadaistic (0.01), **toonami** (0.19)\n\n██████████ appdash\n████████░░ appdash\n██░░░░░░░░ toonami\n█░░░░░░░░░ draftsmanship\n█░░░░░░░░░ commission\n█░░░░░░░░░ ethereal\n## Shortened prompts\n1️⃣ appdash appdash, draftsmanship, commission for, ethereal, toonami\n\n2️⃣ appdash appdash, draftsmanship, commission, toonami\n\n3️⃣ appdash appdash, draftsmanship, toonami\n\n4️⃣ appdash appdash, toonami\n\n5️⃣ appdash app",
  "finalZhPrompt": "## 重要的词语\n**appdash** (1.00) **appdash** (0.79)，以专家级(0.01) **绘画技巧** (0.09) 的风格，委托(0.08) 制作，飘渺的(0.05)，梦幻般的(0.02) 质感(0.01)，达达主义的(0.01)，**toonami** (0.19)\n\n██████████ appdash\n████████░░ appdash\n██░░░░░░░░ toonami\n█░░░░░░░░░ draftsmanship\n█░░░░░░░░░ commission\n█░░░░░░░░░ ethereal\n## 简化的提示\n1️⃣ appdash appdash，绘画技巧，委托制作，飘渺，toonami\n\n2️⃣ appdash appdash，绘画技巧，委托制作，toonami\n\n3️⃣ appdash appdash，绘画技巧，toonami\n\n4️⃣ appdash appdash，toonami\n\n5️⃣ appdash appdash"
  }
  // ...
  }
  获取任务图片的seed
  绘图任务执行后，不会设置seed，如需获取seed，需要执行 /mj/task/{id}/image-seed
  ⚠️ 注意: 必须配置账号的Midjourney Bot私信ID，否则无法调用
- code=1: 获取成功，result为图片对应的seed
  {
  "code": 1,
  "description": "成功",
  "result": "636646138"
  }
- other: 执行错误，description为错误描述
  任务变更回调
  任务状态变化或进度改变时，会调用业务系统的接口
- 接口地址为配置的 mj.notify-hook，任务提交时支持传notifyHook以改变此任务的回调地址
- 两者都为空时，不触发回调
  POST application/json
  {
  "id": "14001929738841620",
  "action": "IMAGINE",
  "status": "SUCCESS",
  "prompt": "猫猫",
  "promptEn": "Cat",
  "description": "/imagine 猫猫",
  "submitTime": 1689231405854,
  "startTime": 1689231442755,
  "finishTime": 1689231544312,
  "progress": "100%",
  "imageUrl": "https://cdn.discordapp.com/attachments/xxx/xxx/xxxx.png",
  "failReason": null,
  "properties": {
  "finalPrompt": "Cat"
  },
  "buttons": []
  }