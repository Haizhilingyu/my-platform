import { describe, it, expect } from 'vitest'
import {
  USERNAME_PATTERN,
  PHONE_PATTERN,
  CONFIG_KEY_PATTERN,
  requiredRule,
  lengthRule,
  maxLengthRule,
  patternRule,
  emailRule,
} from '@/shared/utils/validation'

/**
 * 表单校验工具集单元测试。
 *
 * 覆盖点：
 *  - 每个 factory 返回结构正确的 FormItemRule；
 *  - 内置正则常量对合法/非法/边界输入的判定。
 */
describe('validation 工具集', () => {
  describe('requiredRule', () => {
    it('should return required=true rule with given message and trigger', () => {
      expect(requiredRule('用户名不能为空')).toEqual({
        required: true,
        message: '用户名不能为空',
        trigger: ['blur', 'input'],
      })
    })
  })

  describe('lengthRule', () => {
    it('should return min/max rule with given bounds', () => {
      expect(lengthRule(3, 32, '用户名长度需在3-32之间')).toEqual({
        min: 3,
        max: 32,
        message: '用户名长度需在3-32之间',
        trigger: ['blur', 'input'],
      })
    })
  })

  describe('maxLengthRule', () => {
    it('should return max-only rule with given bound', () => {
      expect(maxLengthRule(50, '姓名长度不能超过50')).toEqual({
        max: 50,
        message: '姓名长度不能超过50',
        trigger: ['blur', 'input'],
      })
    })
  })

  describe('patternRule', () => {
    it('should embed the provided RegExp and message', () => {
      expect(patternRule(USERNAME_PATTERN, '用户名只能包含字母、数字、下划线')).toEqual({
        pattern: USERNAME_PATTERN,
        message: '用户名只能包含字母、数字、下划线',
        trigger: ['blur', 'input'],
      })
    })
  })

  describe('emailRule', () => {
    it('should use naive-ui built-in email type with default message', () => {
      expect(emailRule()).toEqual({
        type: 'email',
        message: '邮箱格式不正确',
        trigger: ['blur', 'input'],
      })
    })

    it('should allow overriding the message', () => {
      expect(emailRule('custom').message).toBe('custom')
    })
  })

  describe('USERNAME_PATTERN', () => {
    it('should accept letters, digits and underscores', () => {
      expect(USERNAME_PATTERN.test('abc')).toBe(true)
      expect(USERNAME_PATTERN.test('user_123')).toBe(true)
      expect(USERNAME_PATTERN.test('ABC')).toBe(true)
    })

    it('should reject hyphens and other punctuation', () => {
      expect(USERNAME_PATTERN.test('abc-def')).toBe(false)
      expect(USERNAME_PATTERN.test('a.b')).toBe(false)
    })

    it('should reject non-ASCII characters', () => {
      expect(USERNAME_PATTERN.test('用户')).toBe(false)
      expect(USERNAME_PATTERN.test('admin用户')).toBe(false)
    })
  })

  describe('PHONE_PATTERN', () => {
    it('should accept valid Chinese mainland mobile numbers', () => {
      expect(PHONE_PATTERN.test('13800138000')).toBe(true)
      expect(PHONE_PATTERN.test('15912345678')).toBe(true)
    })

    it('should reject numbers not starting with 1[3-9]', () => {
      expect(PHONE_PATTERN.test('12345678901')).toBe(false)
      expect(PHONE_PATTERN.test('10000000000')).toBe(false)
    })

    it('should reject wrong length', () => {
      expect(PHONE_PATTERN.test('1380013800')).toBe(false)
      expect(PHONE_PATTERN.test('138001380001')).toBe(false)
    })
  })

  describe('CONFIG_KEY_PATTERN', () => {
    it('should accept letters, digits, dots, underscores, hyphens', () => {
      expect(CONFIG_KEY_PATTERN.test('sys.security.captcha')).toBe(true)
      expect(CONFIG_KEY_PATTERN.test('config_key')).toBe(true)
      expect(CONFIG_KEY_PATTERN.test('config-key')).toBe(true)
      expect(CONFIG_KEY_PATTERN.test('key123')).toBe(true)
    })

    it('should reject spaces and special characters', () => {
      expect(CONFIG_KEY_PATTERN.test('config key')).toBe(false)
      expect(CONFIG_KEY_PATTERN.test('config@key')).toBe(false)
      expect(CONFIG_KEY_PATTERN.test('config:key')).toBe(false)
    })
  })
})
