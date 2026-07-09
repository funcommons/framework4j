package fun.commons.framework4j.demo.controller;

import fun.commons.framework4j.demo.model.TestResult;
import fun.commons.framework4j.demo.service.DemoTestRunnerService;
import fun.commons.framework4j.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 集成测试 Runner + 可视化报告
 */
@RestController
@RequestMapping("/v1/demo")
@RequiredArgsConstructor
public class TestRunnerController {

    private final DemoTestRunnerService testRunnerService;

    /**
     * 一键运行全模块集成测试
     */
    @GetMapping("/test-runner")
    public ApiResponse<List<TestResult>> runAllTests() {
        List<TestResult> results = testRunnerService.runAllTests();
        return ApiResponse.success(results);
    }

    /**
     * HTML 报告页（浏览器打开 → 点击"开始测试" → 实时看结果）
     */
    @GetMapping(value = "/report", produces = "text/html;charset=UTF-8")
    public String reportPage() {
        return """
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>framework4j 集成测试报告</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                       background: #f5f5f5; color: #333; padding: 20px; }
                .container { max-width: 900px; margin: 0 auto; }
                .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                          color: white; padding: 30px; border-radius: 12px; margin-bottom: 20px;
                          text-align: center; }
                .header h1 { font-size: 24px; margin-bottom: 8px; }
                .header p { opacity: 0.9; font-size: 14px; }
                .btn-run { background: #4caf50; color: white; border: none; padding: 12px 32px;
                           border-radius: 8px; font-size: 16px; cursor: pointer; margin: 20px 0;
                           transition: all 0.2s; }
                .btn-run:hover { background: #43a047; transform: translateY(-1px); }
                .btn-run:disabled { background: #ccc; cursor: not-allowed; }
                .summary { display: flex; gap: 16px; margin-bottom: 20px; }
                .summary-card { flex: 1; background: white; border-radius: 8px; padding: 16px;
                                text-align: center; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .summary-card .num { font-size: 28px; font-weight: bold; }
                .summary-card .label { font-size: 12px; color: #888; margin-top: 4px; }
                .pass .num { color: #4caf50; }
                .fail .num { color: #f44336; }
                .total .num { color: #2196f3; }
                table { width: 100%; border-collapse: collapse; background: white;
                        border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                th { background: #f0f0f0; padding: 12px 16px; text-align: left; font-size: 13px;
                     color: #666; text-transform: uppercase; }
                td { padding: 12px 16px; border-top: 1px solid #eee; font-size: 14px; }
                .status-pass { color: #4caf50; font-weight: bold; }
                .status-fail { color: #f44336; font-weight: bold; }
                .status-skip { color: #ff9800; font-weight: bold; }
                .loading { text-align: center; padding: 40px; color: #888; }
                .spinner { display: inline-block; width: 24px; height: 24px;
                           border: 3px solid #ddd; border-top-color: #667eea;
                           border-radius: 50%; animation: spin 0.8s linear infinite; }
                @keyframes spin { to { transform: rotate(360deg); } }
                .empty { text-align: center; padding: 40px; color: #aaa; }
                code { background: #f5f5f5; padding: 2px 6px; border-radius: 3px; font-size: 13px; }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>framework4j 集成测试报告</h1>
                    <p>点击下方按钮，一键运行全模块集成测试</p>
                </div>

                <div style="text-align: center;">
                    <button class="btn-run" id="btnRun" onclick="runTests()">开始测试</button>
                </div>

                <div class="summary" id="summary" style="display:none;">
                    <div class="summary-card pass">
                        <div class="num" id="passCount">0</div>
                        <div class="label">通过</div>
                    </div>
                    <div class="summary-card fail">
                        <div class="num" id="failCount">0</div>
                        <div class="label">失败</div>
                    </div>
                    <div class="summary-card total">
                        <div class="num" id="totalCount">0</div>
                        <div class="label">总计</div>
                    </div>
                    <div class="summary-card">
                        <div class="num" id="totalTime">0</div>
                        <div class="label">总耗时(ms)</div>
                    </div>
                </div>

                <div id="loading" style="display:none;" class="loading">
                    <div class="spinner"></div>
                    <p style="margin-top:12px;">正在运行测试...</p>
                </div>

                <div id="empty" class="empty">点击"开始测试"运行集成测试</div>

                <table id="resultTable" style="display:none;">
                    <thead>
                        <tr>
                            <th>状态</th>
                            <th>模块</th>
                            <th>场景</th>
                            <th>详情</th>
                            <th>耗时</th>
                        </tr>
                    </thead>
                    <tbody id="resultBody"></tbody>
                </table>
            </div>

            <script>
                async function runTests() {
                    const btn = document.getElementById('btnRun');
                    btn.disabled = true;
                    btn.textContent = '测试中...';

                    document.getElementById('loading').style.display = 'block';
                    document.getElementById('empty').style.display = 'none';
                    document.getElementById('summary').style.display = 'none';
                    document.getElementById('resultTable').style.display = 'none';

                    try {
                        const resp = await fetch('/v1/demo/test-runner');
                        const json = await resp.json();
                        const results = json.data || [];

                        document.getElementById('loading').style.display = 'none';

                        let pass = 0, fail = 0, totalTime = 0;
                        const tbody = document.getElementById('resultBody');
                        tbody.innerHTML = '';

                        results.forEach(r => {
                            totalTime += r.durationMs;
                            if (r.status === 'PASS') pass++;
                            else if (r.status === 'FAIL') fail++;

                            const statusClass = r.status === 'PASS' ? 'status-pass'
                                              : r.status === 'FAIL' ? 'status-fail' : 'status-skip';
                            const icon = r.status === 'PASS' ? '✅' : r.status === 'FAIL' ? '❌' : '⏭️';

                            tbody.innerHTML += '<tr>'
                                + '<td><span class="' + statusClass + '">' + icon + ' ' + r.status + '</span></td>'
                                + '<td><code>' + r.module + '</code></td>'
                                + '<td>' + r.scenario + '</td>'
                                + '<td style="color:#666;font-size:13px;">' + (r.detail || '-') + '</td>'
                                + '<td>' + r.durationMs + 'ms</td>'
                                + '</tr>';
                        });

                        document.getElementById('passCount').textContent = pass;
                        document.getElementById('failCount').textContent = fail;
                        document.getElementById('totalCount').textContent = results.length;
                        document.getElementById('totalTime').textContent = totalTime;
                        document.getElementById('summary').style.display = 'flex';
                        document.getElementById('resultTable').style.display = 'table';
                    } catch (err) {
                        document.getElementById('loading').style.display = 'none';
                        document.getElementById('empty').textContent = '测试运行失败: ' + err.message;
                        document.getElementById('empty').style.display = 'block';
                    } finally {
                        btn.disabled = false;
                        btn.textContent = '重新测试';
                    }
                }
            </script>
        </body>
        </html>
        """;
    }
}
