using System.Security.Claims;
using Microsoft.EntityFrameworkCore;
using SnapSpend.Api.Data;
using SnapSpend.Api.Models;
using SnapSpend.Api.Services;

namespace SnapSpend.Api.Endpoints;

public static class ExpenseEndpoints
{
    public static IEndpointRouteBuilder MapExpenses(this IEndpointRouteBuilder app)
    {
        var g = app.MapGroup("/api/expenses").RequireAuthorization();
        g.MapGet("", async (ClaimsPrincipal p, AppDbContext db) => {
            var uid = UserId(p);
            var rows = await db.Expenses.Where(x => x.UserId == uid).OrderByDescending(x => x.ExpenseDate).ThenByDescending(x => x.Id)
                .Select(x => new { x.Id, x.Amount, x.Category, x.ImageUrl, x.Note, x.ExpenseDate, x.AiConfidence }).ToListAsync();
            return rows.Select(x => new ExpenseDto(x.Id, x.Amount, x.Category, x.ImageUrl, x.Note, x.ExpenseDate.ToString("yyyy-MM-dd"), x.AiConfidence)).ToList();
        });
        g.MapPost("", async (HttpRequest request, ClaimsPrincipal p, AppDbContext db, StorageService storage, AiService ai, IWebHostEnvironment env) => {
            var form = await request.ReadFormAsync();
            if (!long.TryParse(form["amount"].ToString(), out var amount) || amount <= 0) return Results.BadRequest(new { message = "Amount must be a positive integer." });
            var category = form["category"].ToString();
            if (category != "auto" && !CategoryCatalog.Keys.Contains(category)) return Results.BadRequest(new { message = "Invalid category." });
            var note = form["note"].ToString();
            var date = DateOnly.TryParse(form["expenseDate"].ToString(), out var d) ? d : DateOnly.FromDateTime(DateTime.UtcNow);
            var uid = UserId(p);
            var file = form.Files.GetFile("image");
            string? url = null;
            string? localPath = null;
            if (file is not null) {
                url = await storage.SaveAsync(file);
                localPath = Path.Combine(env.WebRootPath ?? Path.Combine(env.ContentRootPath, "wwwroot"), "uploads", new Uri(url!).AbsolutePath.Split('/').Last());
            }
            var aiResult = await ai.ClassifyAsync(localPath, note);
            var finalCategory = string.IsNullOrWhiteSpace(category) || category == "auto" ? aiResult.Category : category;
            var expense = new Expense { UserId = uid, Amount = amount, Category = finalCategory, ImageUrl = url, Note = string.IsNullOrWhiteSpace(note) ? null : note, ExpenseDate = date, AiConfidence = aiResult.Confidence, CategorySource = string.IsNullOrWhiteSpace(category) || category == "auto" ? "ai" : "user" };
            db.Expenses.Add(expense); await db.SaveChangesAsync();
            return Results.Ok(new ExpenseDto(expense.Id, expense.Amount, expense.Category, expense.ImageUrl, expense.Note, expense.ExpenseDate.ToString("yyyy-MM-dd"), expense.AiConfidence));
        });
        g.MapPut("/{id:long}", async (long id, ExpenseUpsert req, ClaimsPrincipal p, AppDbContext db) => {
            var e = await db.Expenses.SingleOrDefaultAsync(x => x.Id == id && x.UserId == UserId(p));
            if (e is null) return Results.NotFound();
            if (!CategoryCatalog.Keys.Contains(req.Category)) return Results.BadRequest(new { message = "Invalid category." });
            e.Amount = req.Amount; e.Category = req.Category; e.Note = req.Note; e.ExpenseDate = req.ExpenseDate; e.CategorySource = "user"; e.UpdatedAt = DateTime.UtcNow;
            await db.SaveChangesAsync();
            return Results.Ok(new ExpenseDto(e.Id, e.Amount, e.Category, e.ImageUrl, e.Note, e.ExpenseDate.ToString("yyyy-MM-dd"), e.AiConfidence));
        });
        g.MapDelete("/{id:long}", async (long id, ClaimsPrincipal p, AppDbContext db, StorageService storage) => {
            var e = await db.Expenses.SingleOrDefaultAsync(x => x.Id == id && x.UserId == UserId(p));
            if (e is null) return Results.NotFound();
            var imageUrl = e.ImageUrl;
            db.Expenses.Remove(e); await db.SaveChangesAsync();
            // Xóa file vật lý sau khi xóa bản ghi (an toàn nếu file không tồn tại).
            storage.DeleteByUrl(imageUrl);
            return Results.Ok(new { message = "Deleted" });
        });
        // Khôi phục bản ghi đã xóa (Undo): client gửi lại snapshot đầy đủ, giữ cả ảnh + aiConfidence.
        g.MapPost("/restore", async (ExpenseRestore req, ClaimsPrincipal p, AppDbContext db) => {
            if (req.Amount <= 0) return Results.BadRequest(new { message = "Amount must be a positive integer." });
            if (!CategoryCatalog.Keys.Contains(req.Category)) return Results.BadRequest(new { message = "Invalid category." });
            if (req.ImageUrl is not null && !req.ImageUrl.StartsWith("http", StringComparison.OrdinalIgnoreCase)) return Results.BadRequest(new { message = "Invalid imageUrl." });
            var expense = new Expense { UserId = UserId(p), Amount = req.Amount, Category = req.Category, ImageUrl = req.ImageUrl, Note = req.Note, ExpenseDate = req.ExpenseDate, AiConfidence = req.AiConfidence, CategorySource = "user" };
            db.Expenses.Add(expense); await db.SaveChangesAsync();
            return Results.Ok(new ExpenseDto(expense.Id, expense.Amount, expense.Category, expense.ImageUrl, expense.Note, expense.ExpenseDate.ToString("yyyy-MM-dd"), expense.AiConfidence));
        });
        g.MapPost("/{id:long}/share/{friendId:long}", async (long id, long friendId, ClaimsPrincipal p, AppDbContext db) => {
            var uid = UserId(p);
            var expense = await db.Expenses.SingleOrDefaultAsync(x => x.Id == id && x.UserId == uid);
            var friendship = await db.Friendships.AnyAsync(x => ((x.UserId == uid && x.FriendId == friendId) || (x.UserId == friendId && x.FriendId == uid)) && x.Status == "accepted");
            if (expense is null || !friendship) return Results.BadRequest(new { message = "Expense or friendship not available." });
            if (!await db.ExpenseShares.AnyAsync(x => x.ExpenseId == id && x.ReceiverId == friendId)) db.ExpenseShares.Add(new ExpenseShare { ExpenseId = id, OwnerId = uid, ReceiverId = friendId });
            await db.SaveChangesAsync(); return Results.Ok(new { message = "Shared" });
        });
        // Chi tiêu người khác chia sẻ cho tôi (chỉ xem).
        app.MapGet("/api/shared-with-me", async (ClaimsPrincipal p, AppDbContext db) => {
            var uid = UserId(p);
            var rows = await db.ExpenseShares.Where(s => s.ReceiverId == uid)
                .Join(db.Expenses, s => s.ExpenseId, e => e.Id, (s, e) => new { e, s.OwnerId })
                .Join(db.Users, x => x.OwnerId, u => u.Id, (x, u) => new { x.e, u.Username })
                .OrderByDescending(x => x.e.ExpenseDate).ThenByDescending(x => x.e.Id)
                .Select(x => new SharedExpenseDto(x.e.Id, x.e.Amount, x.e.Category, x.e.ImageUrl, x.e.Note, x.e.ExpenseDate.ToString("yyyy-MM-dd"), x.e.AiConfidence, x.Username))
                .ToListAsync();
            return Results.Ok(rows);
        }).RequireAuthorization();
        return app;
    }

    private static long UserId(ClaimsPrincipal p) => long.Parse(p.FindFirstValue(ClaimTypes.NameIdentifier)!);
    public record ExpenseUpsert(long Amount, string Category, string? Note, DateOnly ExpenseDate);
    public record ExpenseRestore(long Amount, string Category, string? Note, DateOnly ExpenseDate, string? ImageUrl, double? AiConfidence);
    public record SharedExpenseDto(long Id, long Amount, string Category, string? ImageUrl, string? Note, string ExpenseDate, double? AiConfidence, string OwnerUsername);
    public record ExpenseDto(long Id, long Amount, string Category, string? ImageUrl, string? Note, string ExpenseDate, double? AiConfidence);
}
