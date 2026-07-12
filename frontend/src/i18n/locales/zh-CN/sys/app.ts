export default {
  clientId: 'Client ID',
  appName: '应用名称',
  grantType: '授权类型',
  status: '状态',
  createTime: '创建时间',
  operation: '操作',
  enabled: '启用',
  disabled: '禁用',

  grantTypes: {
    authCode: 'authorization_code（授权码）',
    refreshToken: 'refresh_token（刷新令牌）',
    clientCredentials: 'client_credentials（客户端凭据）',
  },

  placeholders: {
    search: '搜索 Client ID / 应用名称',
    clientName: '如：移动端 App',
    redirectUri: 'https://example.com/callback',
    postLogoutRedirectUri: 'https://example.com/post-logout',
    scopes: '选择或输入 scope',
    grantTypes: '选择授权类型',
  },

  validation: {
    appNameRequired: '应用名称不能为空',
    appNameMaxLength: '应用名称长度不能超过100',
    atLeastOneRedirectUri: '至少需要一个重定向URI',
    atLeastOneScope: '请至少选择一个权限范围',
    atLeastOneGrantType: '请至少选择一个授权类型',
  },

  buttons: {
    addApp: '新增应用',
    editApp: '编辑应用',
    search: '查询',
    addRedirectUri: '添加回调地址',
    addPostLogoutUri: '添加登出回调',
    save: '保存',
    cancel: '取消',
    edit: '编辑',
    resetSecret: '重置密钥',
    delete: '删除',
    copy: '复制',
    saved: '我已保存',
  },

  secretModal: {
    title: 'Client Secret（仅显示一次）',
    warning: '此密钥仅显示一次，关闭后将无法再次查看。请立即复制并妥善保存。',
    clientIdLabel: 'Client ID',
    clientSecretLabel: 'Client Secret',
  },

  toast: {
    queryFailed: '查询失败',
    modifySuccess: '修改成功',
    operationFailed: '操作失败',
    enabled: '已启用',
    disabled: '已禁用',
    resetSecretFailed: '重置密钥失败',
    deleteSuccess: '删除成功',
    deleteFailed: '删除失败',
    copied: '已复制到剪贴板',
    copyFailed: '复制失败，请手动选择复制',
  },
}