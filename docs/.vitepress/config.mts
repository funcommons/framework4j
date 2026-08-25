import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'framework4j',
  description: '企业级 Spring Boot 基础设施 SDK',
  lang: 'zh-CN',
  base: '/',
  ignoreDeadLinks: true,
  themeConfig: {
    nav: [
      { text: '首页', link: '/' },
      { text: '快速开始', link: '/guide/quick-start' },
      { text: '模块', link: '/modules/api' },
      { text: 'Skill', link: '/skills/fwk4j-sdk' },
      { text: '配置工具', link: '/config/generator' },
      { text: 'FAQ', link: '/faq' },
    ],
    sidebar: {
      '/guide/': [
        {
          text: '入门',
          items: [
            { text: '快速开始', link: '/guide/quick-start' },
            { text: '架构总览', link: '/guide/architecture' },
          ]
        }
      ],
      '/modules/': [
        {
          text: '基础',
          items: [
            { text: 'API 契约', link: '/modules/api' },
            { text: 'Web 层', link: '/modules/web' },
          ]
        },
        {
          text: '数据',
          items: [
            { text: 'Redis 多数据源', link: '/modules/redis' },
            { text: 'DataSource 多数据源', link: '/modules/datasource' },
            { text: 'SQL 追踪', link: '/modules/sql-tracing' },
            { text: '分布式 ID', link: '/modules/id' },
            { text: '时间处理', link: '/modules/datetime' },
          ]
        },
        {
          text: '安全',
          items: [
            { text: 'AccessToken 鉴权', link: '/modules/accesstoken' },
            { text: '接口签名', link: '/modules/signature' },
            { text: '审计日志', link: '/modules/audit' },
            { text: '字段脱敏/加密', link: '/modules/sensitive' },
          ]
        },
        {
          text: '流量',
          items: [
            { text: '限流', link: '/modules/rate-limit' },
            { text: '幂等键', link: '/modules/idempotency' },
            { text: '多级缓存', link: '/modules/cache' },
          ]
        },
        {
          text: '集成',
          items: [
            { text: 'HTTP 传输', link: '/modules/transport' },
            { text: '动态追踪日志', link: '/modules/tracelog' },
          ]
        }
      ],
      '/config/': [
        {
          text: '配置',
          items: [
            { text: '配置参考', link: '/config/reference' },
            { text: '在线配置生成', link: '/config/generator' },
          ]
        }
      ],
      '/skills/': [
        {
          text: 'SDK Skill',
          items: [
            { text: '总览', link: '/skills/fwk4j-sdk' },
          ]
        },
        {
          text: '基础',
          items: [
            { text: 'API 契约', link: '/skills/fwk4j-api' },
            { text: 'Web 层', link: '/skills/fwk4j-web' },
          ]
        },
        {
          text: '安全',
          items: [
            { text: 'AccessToken', link: '/skills/fwk4j-accesstoken' },
            { text: '签名', link: '/skills/fwk4j-signature' },
            { text: '审计', link: '/skills/fwk4j-audit' },
            { text: '脱敏/加密', link: '/skills/fwk4j-sensitive' },
          ]
        },
        {
          text: '流量',
          items: [
            { text: '限流', link: '/skills/fwk4j-rate-limit' },
            { text: '幂等', link: '/skills/fwk4j-idempotency' },
            { text: '缓存', link: '/skills/fwk4j-cache' },
          ]
        },
        {
          text: '数据',
          items: [
            { text: 'Redis', link: '/skills/fwk4j-redis' },
            { text: 'DataSource', link: '/skills/fwk4j-datasource' },
            { text: 'SQL 追踪', link: '/skills/fwk4j-sql-tracing' },
            { text: '分布式 ID', link: '/skills/fwk4j-id' },
            { text: '时间处理', link: '/skills/fwk4j-datetime' },
          ]
        },
        {
          text: '集成',
          items: [
            { text: 'HTTP 传输', link: '/skills/fwk4j-transport' },
            { text: '动态追踪日志', link: '/skills/fwk4j-tracelog' },
          ]
        }
      ]
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/your-org/framework4j' }
    ],
    footer: {
      message: '基于 Apache-2.0 协议开源',
      copyright: 'Copyright © 2026 framework4j'
    }
  }
})
