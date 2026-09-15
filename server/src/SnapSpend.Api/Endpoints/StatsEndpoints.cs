using System.Globalization;
using System.Security.Claims;
using Microsoft.EntityFrameworkCore;
using SnapSpend.Api.Data;
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
        g.MapPost("/ai/analyze", async (string from, string to, ClaimsPrincipal p, AppDbContext db, AiService ai) => {
            var uid = long.Parse(p.FindFirstValue(ClaimTypes.NameIdentifier)!);
            if (!TryParseDate(from, out var f) || !TryParseDate(to, out var t)) return Results.BadRequest(new { message = "Invalid date. Use yyyy-MM-dd." });
            if (f > t) return Results.BadRequest(new { message = "from must be on or before to." });
            var data = await db.Expenses.Where(x => x.UserId == uid && x.ExpenseDate >= f && x.ExpenseDate <= t).ToListAsync();
            var lines = new List<string> { $"Period: {f:yyyy-MM-dd} to {t:yyyy-MM-dd}", $"Total: {data.Sum(x => x.Amount)}", $"Average daily: {data.Sum(x => x.Amount) / (double)Math.Max(1, t.DayNumber - f.DayNumber + 1)}" };
            lines.AddRange(data.GroupBy(x => x.Category).OrderByDescending(g => g.Sum(x => x.Amount)).Select(g => $"Category {g.Key}: {g.Sum(x => x.Amount)}"));
            lines.AddRange(data.GroupBy(x => x.ExpenseDate).OrderByDescending(g => g.Sum(x => x.Amount)).Take(7).Select(g => $"Day {g.Key:yyyy-MM-dd}: {g.Sum(x => x.Amount)}"));
            var result = await ai.AnalyzeAsync(string.Join("\n", lines));
            return Results.Ok(result);
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
        return app;
    }

    /// <summary>Parse chặt theo yyyy-MM-dd, không phụ thuộc culture của máy.</summary>
    private static bool TryParseDate(string value, out DateOnly date) =>
        DateOnly.TryParseExact(value, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out date);
}
