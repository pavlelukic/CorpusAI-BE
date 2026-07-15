package com.corpusai.metrics;

import com.corpusai.metrics.dto.MetricsResponse;
import com.corpusai.metrics.dto.UsageGroupResponse;
import com.corpusai.metrics.dto.UsageTotalsResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@RestController
@RequestMapping("/api/admin/metrics")
public class MetricsController {

    private static final List<String> CSV_HEADER = List.of(
            "group", "calls", "totalInputTokens", "totalOutputTokens", "totalTokens", "avgLatencyMs", "p95LatencyMs");

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping
    public MetricsResponse getMetrics(@RequestParam(required = false) String from,
                                      @RequestParam(required = false) String to,
                                      @RequestParam(required = false) String groupBy) {
        MetricsService.MetricsResult result = metricsService.aggregate(from, to, groupBy);
        UsageTotalsResponse overall = toTotalsResponse(result.overall());
        List<UsageGroupResponse> groups = result.groups().stream()
                .map(this::toGroupResponse)
                .toList();
        return new MetricsResponse(overall, groups);
    }

    @GetMapping("/export")
    public void export(@RequestParam(required = false) String from,
                       @RequestParam(required = false) String to,
                       @RequestParam(required = false) String groupBy,
                       HttpServletResponse response) throws IOException {
        MetricsService.MetricsResult result = metricsService.aggregate(from, to, groupBy);

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"llm-usage-metrics.csv\"");

        try (PrintWriter writer = response.getWriter()) {
            writer.println(String.join(",", CSV_HEADER));
            writeRow(writer, "OVERALL", result.overall());
            for (UsageAggregateProjection group : result.groups()) {
                writeRow(writer, group.getGroupKey(), group);
            }
        }
    }

    private void writeRow(PrintWriter writer, String groupLabel, UsageAggregateProjection row) {
        writer.println(String.join(",",
                csvField(groupLabel),
                String.valueOf(row.getCalls()),
                String.valueOf(row.getTotalInputTokens()),
                String.valueOf(row.getTotalOutputTokens()),
                String.valueOf(row.getTotalTokens()),
                String.valueOf(row.getAvgLatencyMs()),
                String.valueOf(row.getP95LatencyMs())));
    }

    private String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private UsageTotalsResponse toTotalsResponse(UsageAggregateProjection row) {
        return new UsageTotalsResponse(row.getCalls(), row.getTotalInputTokens(), row.getTotalOutputTokens(),
                row.getTotalTokens(), row.getAvgLatencyMs(), row.getP95LatencyMs());
    }

    private UsageGroupResponse toGroupResponse(UsageAggregateProjection row) {
        return new UsageGroupResponse(row.getGroupKey(), row.getCalls(), row.getTotalInputTokens(),
                row.getTotalOutputTokens(), row.getTotalTokens(), row.getAvgLatencyMs(), row.getP95LatencyMs());
    }
}