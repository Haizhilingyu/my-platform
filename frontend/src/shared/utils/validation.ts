import type { FormItemRule } from 'naive-ui'

/**
 * 表单校验工具集（Phase 1）。
 *
 * 设计目标：
 *  - 与后端 DTO 注解保持一一对应（同样的 min/max/pattern），确保前后端校验同源；
 *  - 每个 factory 返回单条 Naive UI `FormItemRule`，调用方通过数组组合多规则；
 *  - 全部显式类型，无 `any`，符合 TS strict 模式。
 *
 * 后续 Phase 将把这套规则复用到 role / menu / unit / config 等表单。
 */

/** 用户名：仅字母、数字、下划线（与后端 @Pattern 一致）。 */
export const USERNAME_PATTERN = /^[a-zA-Z0-9_]+$/

/** 中国大陆手机号：1 开头、第二位 3-9、共 11 位（与后端 @Pattern 一致）。 */
export const PHONE_PATTERN = /^1[3-9]\d{9}$/

/** 配置键：字母、数字、点、下划线、连字符（与后端 ConfigDTO @Pattern 一致）。 */
export const CONFIG_KEY_PATTERN = /^[a-zA-Z0-9._-]+$/

/**
 * 必填规则。
 *
 * Naive UI 中 `required` 与 `type`/`pattern` 等是相互独立的：
 * 仅当显式声明 `required: true` 时才强制非空。
 */
export function requiredRule(message: string): FormItemRule {
  return { required: true, message, trigger: ['blur', 'input'] }
}

/**
 * 长度区间规则（适用于字符串字段）。
 *
 * Naive UI 底层使用 async-validator，`min` / `max` 对字符串字段表示字符数区间。
 */
export function lengthRule(min: number, max: number, message: string): FormItemRule {
  return { min, max, message, trigger: ['blur', 'input'] }
}

export function maxLengthRule(max: number, message: string): FormItemRule {
  return { max, message, trigger: ['blur', 'input'] }
}

export function patternRule(pattern: RegExp, message: string): FormItemRule {
  return { pattern, message, trigger: ['blur', 'input'] }
}

/**
 * 邮箱格式规则。
 *
 * 使用 Naive UI 内置 `type: 'email'` 校验（底层 async-validator 实现，
 * 比 RFC 正则更稳健）。注意：`type: 'email'` 仅在字段有值时触发，
 * 不会强制非空 —— 这正是可选字段期望的行为。如需必填，请叠加 `requiredRule`。
 */
export function emailRule(message = '邮箱格式不正确'): FormItemRule {
  return { type: 'email', message, trigger: ['blur', 'input'] }
}
