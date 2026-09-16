using System.Globalization;
using System.Security.Claims;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;
using SnapSpend.Api.Data;
using SnapSpend.Api.Models;
using SnapSpend.Api.Services;

namespace SnapSpend.Api.Endpoints;

public static class StatsEndpoints
{
    public static IEndpointRouteBuilder MapStats(this IEndpointRouteBuilder app)
    {
        var g = app.MapGroup("/api").RequireAuthorization();
        g.MapGet("/stats", async (string from, string to, ClaimsPrincipal p, AppDbContext db) => {
            var uid = long.Parse(p.FindFirstValue(ClaimTypes.NameIdentifier)!);
            if (!TryParseDate(from, out var f) || !TryParseDate(to, out var t)) return Results.BadRequest(new { message = "Invalid date. Use yyyy-MM-dd." });
            if (f > t) return Results.BadRequest(new { message = "from must be on or before to." });
            var data = await db.Expenses.Where(x => x.UserId == uid && x.ExpenseDate >= f && x.ExpenseDate <= t).ToListAsync();
            var total = data.Sum(x => x.Amount);
            var dayCount = Math.Max(1, t.DayNumber - f.DayNumber + 1);
            return Results.Ok(new {
                total,
                averageDaily = total / (double)dayCount,
                byCategory = data.GroupBy(x => x.Category).ToDictionary(g => g.Key, g => g.Sum(x => x.Amount)),
                byDay = data.GroupBy(x => x.ExpenseDate).OrderBy(g => g.Key).ToDictionary(g => g.Key.ToString("yyyy-MM-dd"), g => g.Sum(x => x.Amount))
            });
        });
        // Phân loại trước khi lưu: nhận ảnh + ghi chú, trả category/confidence để client cho sửa.
        g.MapPost("/ai/classify", async (HttpRequest request, AiService ai) => {
            var form = await request.ReadFormAsync();
            var file = form.Files.GetFile("image");
            var note = form["note"].ToString();
            if (file is null || file.Length == 0) return Results.Ok(await ai.ClassifyAsync((byte[]?)null, null, note));
            using var ms = new MemoryStream();
            await file.CopyToAsync(ms);
            return Results.Ok(await ai.ClassifyAsync(ms.ToArray(), file.ContentType, note));
        });
        // Phân loại từng món (hóa đơn nhiều loại) bằng engine nội bộ từ DB; trả danh mục từng món.
        g.MapPost("/ai/classify-items", async (ClassifyItemsRequest req, RecognitionService recognition) => {
            var items = (req.Items ?? new List<string>()).Take(20).ToList();
            var result = new List<ItemCategoryDto>();
            foreach (var name in items)
            {
                var r = await recognition.ClassifyTextAsync(name);
                result.Add(new ItemCategoryDto(name, r.Category, r.Confidence));
            }
            return Results.Ok(result);
        });
        // Phân tích cơ bản: chỉ số liệu (tổng, trung bình, top danh mục, mức độ) — không cần AI.
        g.MapPost("/ai/analyze-basic", async (string from, string to, ClaimsPrincipal p, AppDbContext db) => {
            var uid = long.Parse(p.FindFirstValue(ClaimTypes.NameIdentifier)!);
            if (!TryParseDate(from, out var f) || !TryParseDate(to, out var t)) return Results.BadRequest(new { message = "Invalid date. Use yyyy-MM-dd." });
            if (f > t) return Results.BadRequest(new { message = "from must be on or before to." });
            return Results.Ok(await BuildBasicAsync(uid, f, t, db));
        });
        // AI phân tích: phân tích cơ bản + đánh giá/lời khuyên của AI (có cache theo dữ liệu).
        g.MapPost("/ai/analyze-full", async (string from, string to, ClaimsPrincipal p, AppDbContext db, AiService ai, IMemoryCache cache) => {
            var uid = long.Parse(p.FindFirstValue(ClaimTypes.NameIdentifier)!);
            if (!TryParseDate(from, out var f) || !TryParseDate(to, out var t)) return Results.BadRequest(new { message = "Invalid date. Use yyyy-MM-dd." });
            if (f > t) return Results.BadRequest(new { message = "from must be on or before to." });
            var maxUpdated = await db.Expenses.Where(x => x.UserId == uid && x.ExpenseDate >= f && x.ExpenseDate <= t)
                .Select(x => x.UpdatedAt).DefaultIfEmpty().MaxAsync();
            var versionCount = await db.Expenses.Where(x => x.UserId == uid && x.ExpenseDate >= f && x.ExpenseDate <= t).CountAsync();
            var key = $"aifull:{uid}:{f:yyyyMMdd}:{t:yyyyMMdd}:{maxUpdated:O}:{versionCount}";
            if (cache.TryGetValue(key, out AiAnalysisDto? cached) && cached is not null) return Results.Ok(cached);
            var basic = await BuildBasicAsync(uid, f, t, db);
            var data = await db.Expenses.Where(x => x.UserId == uid && x.ExpenseDate >= f && x.ExpenseDate <= t).ToListAsync();
            var lines = new List<string> { $"Period: {f:yyyy-MM-dd} to {t:yyyy-MM-dd}", $"Total: {basic.Total}", $"Average daily: {basic.AverageDaily}" };
            lines.AddRange(data.GroupBy(x => x.Category).OrderByDescending(g => g.Sum(x => x.Amount)).Select(g => {
                var name = Models.CategoryCatalog.All.FirstOrDefault(c => c.Key == g.Key).Name ?? g.Key;
                return $"Category {name}: {g.Sum(x => x.Amount)}";
            }));
            var evaluation = await ai.EvaluateAsync(string.Join("\n", lines));
            var dto = new AiAnalysisDto(basic, basic.Summary, evaluation, basic.TopTrend, basic.TopAnomaly, basic.TopRecommendation);
            cache.Set(key, dto, TimeSpan.FromMinutes(10));
            return Results.Ok(dto);
        });
        return app;
    }

    public record ClassifyItemsRequest(List<string> Items);
    public record ItemCategoryDto(string Name, string Category, double Confidence);
    public record CategoryShareDto(string Key, string Name, long Amount, int Share);
    public record TopExpenseDto(long Id, long Amount, string Category, string? Note, string Date);
    public record RecurringDto(string Note, int Count, long Total);
    public record BasicAnalysisDto(
        long Total, double AverageDaily, int DayCount, long PreviousTotal,
        string TopCategory, int TopShare, string Level,
        List<CategoryShareDto> Breakdown, List<TopExpenseDto> TopExpenses,
        string? BiggestDay, long BiggestDayAmount,
        long WeekendTotal, long WeekdayTotal, List<RecurringDto> Recurring,
        string Summary,
        List<string> TopTrend, List<string> TopAnomaly, List<string> TopRecommendation);
    public record AiAnalysisDto(
        BasicAnalysisDto Basic, string Summary, string? Evaluation,
        List<string> Trends, List<string> Anomalies, List<string> Recommendations);

    /// <summary>Đánh giá mức độ chi tiêu từ số liệu: so kỳ trước + độ tập trung nhóm cao nhất.</summary>
    private static async Task<BasicAnalysisDto> BuildBasicAsync(long uid, DateOnly f, DateOnly t, AppDbContext db)
    {
        var data = await db.Expenses.Where(x => x.UserId == uid && x.ExpenseDate >= f && x.ExpenseDate <= t).ToListAsync();
        var total = data.Sum(x => x.Amount);
        var days = Math.Max(1, t.DayNumber - f.DayNumber + 1);
        var avg = total / (double)days;
        var groups = data.GroupBy(x => x.Category).OrderByDescending(g => g.Sum(x => x.Amount)).ToList();
        var breakdown = groups.Select(g => {
            var sum = g.Sum(x => x.Amount);
            var key = g.Key;
            var name = Models.CategoryCatalog.All.FirstOrDefault(c => c.Key == key).Name ?? key;
            var share = total > 0 ? (int)Math.Round(sum * 100.0 / total) : 0;
            return new CategoryShareDto(key, name, sum, share);
        }).ToList();
        var top = breakdown.FirstOrDefault();
        var topKey = top?.Key ?? "other";
        var topName = top?.Name ?? "Khác";
        var topShare = top?.Share ?? 0;

        var spanDays = Math.Max(1, t.DayNumber - f.DayNumber + 1);
        var prevTotal = await db.Expenses
            .Where(x => x.UserId == uid && x.ExpenseDate >= f.AddDays(-spanDays) && x.ExpenseDate <= f.AddDays(-1))
            .SumAsync(x => x.Amount);
        string level;
        if (prevTotal <= 0 && total <= 0) level = "Chưa có dữ liệu";
        else if (prevTotal <= 0) level = "Kỳ mới phát sinh chi tiêu";
        else {
            var pct = (int)Math.Round((total - prevTotal) * 100.0 / prevTotal);
            level = pct > 20 ? $"Tăng mạnh ({pct}% so với kỳ trước)"
                : pct > 0 ? $"Tăng ({pct}% so với kỳ trước)"
                : pct == 0 ? "Ổn định so với kỳ trước"
                : pct > -20 ? $"Giảm ({Math.Abs(pct)}% so với kỳ trước)"
                : $"Giảm mạnh ({Math.Abs(pct)}% so với kỳ trước)";
        }
        if (topShare >= 50) level += $"; tập trung cao ở {topName}";

        var summary = total <= 0
            ? "Chưa có chi tiêu trong kỳ này."
            : $"Tổng {total:N0}đ/{days} ngày ({avg:N0}đ/ngày). Nhóm {topName} cao nhất {topShare}%. {level}.";

        var topExpenses = data.OrderByDescending(x => x.Amount).Take(3)
            .Select(x => new TopExpenseDto(x.Id, x.Amount, x.Category, x.Note, x.ExpenseDate.ToString("yyyy-MM-dd"))).ToList();

        var bestDay = data.GroupBy(x => x.ExpenseDate).OrderByDescending(g => g.Sum(x => x.Amount)).FirstOrDefault();
        var biggestDay = bestDay?.Key.ToString("yyyy-MM-dd");
        var biggestDayAmount = bestDay?.Sum(x => x.Amount) ?? 0;

        var weekendTotal = data.Where(x => x.ExpenseDate.DayOfWeek is DayOfWeek.Saturday or DayOfWeek.Sunday).Sum(x => x.Amount);
        var weekdayTotal = total - weekendTotal;

        var recurring = data.Where(x => !string.IsNullOrWhiteSpace(x.Note))
            .GroupBy(x => x.Note!.Trim().ToLowerInvariant())
            .Where(g => g.Count() >= 2)
            .OrderByDescending(g => g.Count())
            .Take(3)
            .Select(g => new RecurringDto(g.First().Note!, g.Count(), g.Sum(x => x.Amount))).ToList();

        var trends = breakdown.Take(3).Select(b => $"{b.Name}: {b.Amount:N0}đ ({b.Share}%).").ToList();
        var anomalies = new List<string>();
        if (topShare >= 50) anomalies.Add($"{topName} chiếm hơn một nửa tổng chi ({topShare}%).");
        foreach (var b in breakdown.Where(b => b.Share >= 30 && b.Key != topKey).Take(2))
            anomalies.Add($"{b.Name} tăng mạnh, chiếm {b.Share}% tổng chi.");
        if (weekendTotal > 0 && weekdayTotal > 0 && weekendTotal / 2.0 > (weekdayTotal / 5.0) * 1.5)
            anomalies.Add($"Chi cuối tuần cao hơn hẳn ngày thường ({weekendTotal:N0}đ so với trung bình ngày thường).");
        foreach (var r in recurring)
            anomalies.Add($"Khoản lặp lại: \"{r.Note}\" {r.Count} lần, tổng {r.Total:N0}đ.");
        var biggest = data.OrderByDescending(x => x.Amount).FirstOrDefault();
        if (biggest is not null && avg > 0 && biggest.Amount > avg * 5)
            anomalies.Add($"Khoản chi lớn bất thường: {biggest.Amount:N0}đ ({biggest.Note ?? biggest.Category}) ngày {biggest.ExpenseDate:yyyy-MM-dd}.");
        var recs = new List<string>();
        if (topShare >= 50) recs.Add($"Đặt hạn mức cho nhóm {topName} để cân đối.");
        if (breakdown.Count > 0) {
            var cut = (long)Math.Round(breakdown[0].Amount * 0.1 / 1000.0) * 1000;
            if (cut > 0) recs.Add($"Nếu giảm khoảng {cut:N0}đ ở nhóm {breakdown[0].Name}, bạn tiết kiệm thêm {cut:N0}đ/kỳ.");
        }
        if (recurring.Count > 0) recs.Add($"Xem lại các khoản lặp lại ({recurring[0].Note}) có thật sự cần thiết không.");
        return new(total, avg, days, prevTotal, topKey, topShare, level, breakdown, topExpenses,
            biggestDay, biggestDayAmount, weekendTotal, weekdayTotal, recurring,
            summary, trends, anomalies, recs);
    }

    /// <summary>Parse chặt theo yyyy-MM-dd, không phụ thuộc culture của máy.</summary>
    private static bool TryParseDate(string value, out DateOnly date) =>
        DateOnly.TryParseExact(value, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out date);
}
