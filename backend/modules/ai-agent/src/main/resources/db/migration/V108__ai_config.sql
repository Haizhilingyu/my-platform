-- AI Copilot 配置种子：把 DeepSeek 连接参数迁到 sys_config（界面可配），不再依赖服务器环境变量。
-- 去重插入，兼容已存在数据 / 重复执行。
INSERT INTO sys_config (config_key, config_value, config_type, description, category) VALUES
  ('ai.deepseek.api-key', '', 'SECRET',
   'DeepSeek/OpenAI 兼容 API Key（界面配置，勿放入服务器环境变量）', 'ai'),
  ('ai.deepseek.base-url', 'https://api.deepseek.com', 'STRING',
   'OpenAI 兼容 base-url，默认指向 DeepSeek 官方', 'ai'),
  ('ai.deepseek.model', 'deepseek-chat', 'STRING',
   '模型名，默认 deepseek-chat', 'ai')
ON CONFLICT (config_key) DO NOTHING;
