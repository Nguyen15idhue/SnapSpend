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
            if (!DateOnly.TryParse(from, out var f) || !DateOnly.TryParse(to, out var t)) return Results.BadRequest(new { message = "Use yyyy-MM-dd." });
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
            if (!DateOnly.TryParse(from, out var f) || !DateOnly.TryParse(to, out var t)) return Results.BadRequest(new { message = "Use yyyy-MM-dd." });
            var data = await db.Expenses.Where(x => x.UserId == uid && x.ExpenseDate >= f && x.ExpenseDate <= t).ToListAsync();
            var lines = new List<string> { $"Period: {f:yyyy-MM-dd} to {t:yyyy-MM-dd}", $"Total: {data.Sum(x => x.Amount)}", $"Average daily: {data.Sum(x => x.Amount) / (double)Math.Max(1, t.DayNumber - f.DayNumber + 1)}" };
            lines.AddRange(data.GroupBy(x => x.Category).OrderByDescending(g => g.Sum(x => x.Amount)).Select(g => $"Category {g.Key}: {g.Sum(x => x.Amount)}"));
            lines.AddRange(data.GroupBy(x => x.ExpenseDate).OrderByDescending(g => g.Sum(x => x.Amount)).Take(7).Select(g => $"Day {g.Key:yyyy-MM-dd}: {g.Sum(x => x.Amount)}"));
            var result = await ai.AnalyzeAsync(string.Join("\n", lines));
            return Results.Ok(result);
        });
        return app;
    }
}
