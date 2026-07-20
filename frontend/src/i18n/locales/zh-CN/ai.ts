export default {
  title: 'AI 助手',
  // 注意：vue-i18n 把 `@` 当作 linked message 语法，必须用字面插值 {'@'} 转义，否则编译报 "Invalid linked format"。
  placeholder: "输入指令，例如：创建用户 alice 密码 Alice{'@'}123",
  send: '发送',
  callingTool: '调用工具',
  thinking: '正在处理…',
  viewResult: '查看结果',
  welcome: '我是 AI 助手，可以按你的权限帮你管理系统。试试：',
  exampleCreate: "创建用户 alice 密码 Alice{'@'}123",
  exampleDelete: '删除用户 42',
  confirmExecute: '执行',
  confirmCancel: '取消',
  confirmExecuted: '已执行',
  confirmCancelled: '已取消',
}
