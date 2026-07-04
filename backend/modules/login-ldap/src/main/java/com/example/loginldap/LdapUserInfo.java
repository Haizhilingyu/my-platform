package com.example.loginldap;

/**
 * LDAP 绑定认证成功后提取的用户信息。
 *
 * @param username 用户名（用于查/建本地 SysUser）
 * @param email LDAP mail 属性值，可能为 null（用于自动建号的 email 字段）
 * @param realName LDAP cn 属性值，可能为 null（用于自动建号的 realName 字段）
 */
record LdapUserInfo(String username, String email, String realName) {}
