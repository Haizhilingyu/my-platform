import pluginVue from 'eslint-plugin-vue'
import { withVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'

export default withVueTs(
  {
    name: 'app/ignores',
    ignores: [
      'dist/**',
      'node_modules/**',
      'packages/**',
      'env.d.ts',
      'src/auto-imports.d.ts',
      'src/components.d.ts',
    ],
  },
  pluginVue.configs['flat/recommended'],
  vueTsConfigs.recommended,
  {
    name: 'app/rules',
    rules: {
      'vue/multi-word-component-names': 'off',
      '@typescript-eslint/no-explicit-any': 'warn',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/no-restricted-syntax': [
        'error',
        {
          selector: "VAttribute[directive=false][key.name='style']",
          message:
            '禁止在模板中使用静态 style 属性。请改用 Tailwind class，或对 Naive UI 组件使用 :style 动态绑定。',
        },
      ],
    },
  },
)
