import{_ as a,o as i,c as n,a0 as l}from"./chunks/framework.jwovEGr5.js";const g=JSON.parse('{"title":"Spring Boot 3 + Micrometer 全链路 SQL 追踪方案","description":"","frontmatter":{},"headers":[],"relativePath":"modules/sql-tracing-design-architecture.md","filePath":"modules/sql-tracing-design-architecture.md"}'),t={name:"modules/sql-tracing-design-architecture.md"};function p(e,s,E,h,r,k){return i(),n("div",null,[...s[0]||(s[0]=[l(`<p>← <a href="./README.html">返回 README</a></p><h1 id="spring-boot-3-micrometer-全链路-sql-追踪方案" tabindex="-1">Spring Boot 3 + Micrometer 全链路 SQL 追踪方案 <a class="header-anchor" href="#spring-boot-3-micrometer-全链路-sql-追踪方案" aria-label="Permalink to &quot;Spring Boot 3 + Micrometer 全链路 SQL 追踪方案&quot;">​</a></h1><h2 id="_1-背景与目标" tabindex="-1">1. 背景与目标 <a class="header-anchor" href="#_1-背景与目标" aria-label="Permalink to &quot;1. 背景与目标&quot;">​</a></h2><p>在微服务架构下,为了满足线上问题排查、慢 SQL 溯源和统一运维的需求,需要对应用发出的<strong>所有 SQL</strong>(包括查询和变更)进行标记。</p><h3 id="核心需求" tabindex="-1">核心需求 <a class="header-anchor" href="#核心需求" aria-label="Permalink to &quot;核心需求&quot;">​</a></h3><ol><li><strong>全量覆盖</strong>: 所有由应用发出的 SQL(SELECT, INSERT, UPDATE, DELETE 等)均需处理</li><li><strong>规范格式</strong>: 必须通过框架自动添加注释,格式为 <code>/*traceid=xxx,topic=xxx*/ SQL...</code></li><li><strong>包含信息</strong>: <ul><li><code>traceid</code>: 当前链路追踪 ID</li><li><code>topic</code>: 项目名称(应用名)</li></ul></li><li><strong>正例参考</strong>:<div class="language-sql vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">sql</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#6A737D;--shiki-dark:#6A737D;">/*traceid=abc123xyz,topic=MyProject*/</span><span style="--shiki-light:#D73A49;--shiki-dark:#F97583;"> SELECT</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;"> id, </span><span style="--shiki-light:#D73A49;--shiki-dark:#F97583;">name</span><span style="--shiki-light:#D73A49;--shiki-dark:#F97583;"> FROM</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;"> users </span><span style="--shiki-light:#D73A49;--shiki-dark:#F97583;">WHERE</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;"> id </span><span style="--shiki-light:#D73A49;--shiki-dark:#F97583;">=</span><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;"> ?;</span></span></code></pre></div></li></ol><hr><h2 id="_2-总体设计" tabindex="-1">2. 总体设计 <a class="header-anchor" href="#_2-总体设计" aria-label="Permalink to &quot;2. 总体设计&quot;">​</a></h2><p>本方案通过扩展 Alibaba Druid 的 <code>FilterEventAdapter</code> 实现 SQL 拦截与增强。</p><h3 id="_2-1-核心组件架构" tabindex="-1">2.1 核心组件架构 <a class="header-anchor" href="#_2-1-核心组件架构" aria-label="Permalink to &quot;2.1 核心组件架构&quot;">​</a></h3><div class="language-mermaid vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">mermaid</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">graph TB</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    subgraph &quot;应用层&quot;</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        A[业务代码&lt;br/&gt;Mapper/Repository]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    end</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    subgraph &quot;ORM 框架层&quot;</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        B[MyBatis Plus / JPA]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    end</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    subgraph &quot;数据源层 - Druid&quot;</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        C[DruidDataSource]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        D[TraceIdDruidFilter&lt;br/&gt;SQL 拦截器]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    end</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    subgraph &quot;追踪上下文&quot;</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        E1[TraceIdProvider&lt;br/&gt;接口]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        E2[DefaultTraceIdProvider&lt;br/&gt;实现类]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        F1[MDC&lt;br/&gt;SLF4J]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        F2[Micrometer Tracer&lt;br/&gt;可选]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    end</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    subgraph &quot;配置层&quot;</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        G[SqlTracingProperties&lt;br/&gt;追踪配置]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        H[SqlTracingAutoConfiguration&lt;br/&gt;自动配置]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    end</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    subgraph &quot;数据库&quot;</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        I[(PostgreSQL/MySQL)]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    end</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    A --&gt;|SQL 执行| B</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    B --&gt;|JDBC 调用| C</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    C --&gt;|拦截| D</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    D --&gt;|获取 TraceID| E1</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    E1 -.实现.-&gt; E2</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    E2 --&gt;|读取| F1</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    E2 -.可选.-&gt; F2</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    D --&gt;|读取配置| G</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    H --&gt;|注册 Filter| C</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    H --&gt;|加载配置| G</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    D --&gt;|注入 SQL 注释| I</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    style D fill:#ff6b00,stroke:#333,color:#fff</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    style E2 fill:#0ea5e9,stroke:#333,color:#fff</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    style G fill:#10b981,stroke:#333,color:#fff</span></span></code></pre></div><h3 id="_2-2-sql-处理流程图" tabindex="-1">2.2 SQL 处理流程图 <a class="header-anchor" href="#_2-2-sql-处理流程图" aria-label="Permalink to &quot;2.2 SQL 处理流程图&quot;">​</a></h3><div class="language-mermaid vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">mermaid</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">sequenceDiagram</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    participant App as 业务代码</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    participant MB as MyBatis Plus</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    participant Druid as DruidDataSource</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    participant Filter as TraceIdDruidFilter</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    participant Provider as TraceIdProvider</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    participant MDC as SLF4J MDC</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    participant DB as 数据库</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    App-&gt;&gt;MB: userMapper.selectById(1)</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    MB-&gt;&gt;Druid: connection.prepareStatement(sql)</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    Druid-&gt;&gt;Filter: 拦截 SQL</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    Filter-&gt;&gt;Filter: 检查追踪模式&lt;br/&gt;(DISABLED/WRITE_ONLY/ALL)</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    alt 模式为 DISABLED</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        Filter-&gt;&gt;Druid: 返回原始 SQL</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    else 模式为 WRITE_ONLY 且为 SELECT</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        Filter-&gt;&gt;Druid: 返回原始 SQL (跳过读操作)</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    else 模式为 ALL 或 WRITE_ONLY 写操作</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        Filter-&gt;&gt;Filter: 检查是否已有注释&lt;br/&gt;(防重复注入)</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        Filter-&gt;&gt;Provider: getTraceId()</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        Provider-&gt;&gt;MDC: 按优先级查找&lt;br/&gt;traceId, trace_id, X-B3-TraceId...</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        alt MDC 中找到 TraceID</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">            MDC--&gt;&gt;Provider: 返回 TraceID (abc123)</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        else MDC 中未找到</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">            MDC--&gt;&gt;Provider: 返回 null</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">            Provider--&gt;&gt;Filter: 返回 &quot;none&quot;</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        end</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        Filter-&gt;&gt;Filter: 拼接注释&lt;br/&gt;/*traceid=abc123,topic=MyApp*/ SQL</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">        Filter-&gt;&gt;Druid: 返回处理后的 SQL</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    end</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    Druid-&gt;&gt;DB: 执行 SQL</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    DB--&gt;&gt;App: 返回结果</span></span></code></pre></div><h3 id="_2-3-配置加载流程" tabindex="-1">2.3 配置加载流程 <a class="header-anchor" href="#_2-3-配置加载流程" aria-label="Permalink to &quot;2.3 配置加载流程&quot;">​</a></h3><div class="language-mermaid vp-adaptive-theme"><button title="Copy Code" class="copy"></button><span class="lang">mermaid</span><pre class="shiki shiki-themes github-light github-dark vp-code" tabindex="0"><code><span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">flowchart TD</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    Start([Spring Boot 启动]) --&gt; A[SqlTracingAutoConfiguration&lt;br/&gt;初始化]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    A --&gt; B{检测 Micrometer}</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    B --&gt;|存在| C[记录检测到 Micrometer]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    B --&gt;|不存在| D[记录未检测到]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    C --&gt; E[创建 DefaultTraceIdProvider]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    D --&gt; E</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    E --&gt; F[BeanPostProcessor&lt;br/&gt;处理 Bean]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    F --&gt; G{Bean 是否为&lt;br/&gt;DruidDataSource?}</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    G --&gt;|否| H[跳过]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    G --&gt;|是| I[提取数据源名称&lt;br/&gt;如: default, business]</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    I --&gt; J[读取配置&lt;br/&gt;ldx2t.commons.datasource&lt;br/&gt;.datasources.{name}.sql-tracing]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    J --&gt; K{配置是否存在?}</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    K --&gt;|否| L[跳过该数据源]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    K --&gt;|是| M{mode 是否为 DISABLED?}</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    M --&gt;|是| N[跳过该数据源]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    M --&gt;|否| O[创建 TraceIdDruidFilter]</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    O --&gt; P[设置参数:&lt;br/&gt;- TraceIdProvider&lt;br/&gt;- topic&lt;br/&gt;- mode]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    P --&gt; Q[添加到 DruidDataSource&lt;br/&gt;的 proxyFilters]</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    Q --&gt; R[记录日志:&lt;br/&gt;SQL Tracing enabled]</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    L --&gt; End([配置完成])</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    N --&gt; End</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    R --&gt; End</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    H --&gt; End</span></span>
<span class="line"></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    style A fill:#10b981,stroke:#333,color:#fff</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    style O fill:#ff6b00,stroke:#333,color:#fff</span></span>
<span class="line"><span style="--shiki-light:#24292E;--shiki-dark:#E1E4E8;">    style R fill:#0ea5e9,stroke:#333,color:#fff</span></span></code></pre></div><hr>`,16)])])}const d=a(t,[["render",p]]);export{g as __pageData,d as default};
