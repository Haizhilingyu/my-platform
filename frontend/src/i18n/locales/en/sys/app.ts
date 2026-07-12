export default {
  clientId: 'Client ID',
  appName: 'App Name',
  grantType: 'Grant Type',
  status: 'Status',
  createTime: 'Created At',
  operation: 'Operation',
  enabled: 'Enabled',
  disabled: 'Disabled',

  grantTypes: {
    authCode: 'authorization_code (Auth Code)',
    refreshToken: 'refresh_token (Refresh Token)',
    clientCredentials: 'client_credentials (Client Credentials)',
  },

  placeholders: {
    search: 'Search Client ID / App Name',
    clientName: 'e.g., Mobile App',
    redirectUri: 'https://example.com/callback',
    postLogoutRedirectUri: 'https://example.com/post-logout',
    scopes: 'Select or input scope',
    grantTypes: 'Select grant types',
  },

  validation: {
    appNameRequired: 'App name is required',
    appNameMaxLength: 'App name cannot exceed 100 characters',
    atLeastOneRedirectUri: 'At least one redirect URI is required',
    atLeastOneScope: 'Please select at least one scope',
    atLeastOneGrantType: 'Please select at least one grant type',
  },

  buttons: {
    addApp: 'Add App',
    editApp: 'Edit App',
    search: 'Search',
    addRedirectUri: 'Add Redirect URI',
    addPostLogoutUri: 'Add Post-Logout URI',
    save: 'Save',
    cancel: 'Cancel',
    edit: 'Edit',
    resetSecret: 'Reset Secret',
    delete: 'Delete',
    copy: 'Copy',
    saved: 'I have saved',
  },

  secretModal: {
    title: 'Client Secret (shown only once)',
    warning: 'This secret is shown only once and cannot be viewed again. Please copy and save it immediately.',
    clientIdLabel: 'Client ID',
    clientSecretLabel: 'Client Secret',
  },

  toast: {
    queryFailed: 'Failed to query',
    modifySuccess: 'Modified successfully',
    operationFailed: 'Operation failed',
    enabled: 'Enabled',
    disabled: 'Disabled',
    resetSecretFailed: 'Failed to reset secret',
    deleteSuccess: 'Deleted successfully',
    deleteFailed: 'Delete failed',
    copied: 'Copied to clipboard',
    copyFailed: 'Copy failed, please copy manually',
  },
}