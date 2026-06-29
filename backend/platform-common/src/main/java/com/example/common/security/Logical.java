package com.example.common.security;

/** 逻辑运算符，用于多权限组合判断。 */
public enum Logical {
    /** 所有权限都需要。 */
    AND,
    /** 任一权限即可。 */
    OR
}
