using System.Globalization;
using System.Security.Claims;
using System.Text;
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
        g.MapGet("", async (int? page, int? pageSize, string? search, string? category, string? from, string? to, string? sort, ClaimsPrincipal p, AppDbContext db) => {
            var uid = UserId(p);
            var pg = Math.Max(1, page ?? 1);
            var ps = Math.Clamp(pageSize ?? 20, 1, 100);
            // Lọc category theo danh mục chuẩn (giữ tương thích: tham số vắng thì không lọc).
            if (!string.IsNullOrWhiteSpace(category) && !CategoryCatalog.Keys.Contains(category))
                return Results.BadRequest(new { message = "Invalid category." });
            // Lọc ngày optional (yyyy-MM-dd), sai định dạng hoặc from > to thì 400.
            DateOnly? f = null, t = null;
            if (!string.IsNullOrWhiteSpace(from) || !string.IsNullOrWhiteSpace(to)) {
                if (!string.IsNullOrWhiteSpace(from) && !TryParseDate(from, out var ff))
                    return Results.BadRequest(new { message = "Invalid date. Use yyyy-MM-dd." });
                if (!string.IsNullOrWhiteSpace(to) && !TryParseDate(to, out var tt))
                    return Results.BadRequest(new { message = "Invalid date. Use yyyy-MM-dd." });
                if (!string.IsNullOrWhiteSpace(from)) f = DateOnly.ParseExact(from, "yyyy-MM-dd", CultureInfo.InvariantCulture);
                if (!string.IsNullOrWhiteSpace(to)) t = DateOnly.ParseExact(to, "yyyy-MM-dd", CultureInfo.InvariantCulture);
                if (f.HasValue && t.HasValue && f > t)
                    return Results.BadRequest(new { message = "from must be on or before to." });
            }
            var baseQuery = db.Expenses.Where(x => x.UserId == uid);
            if (!string.IsNullOrWhiteSpace(category)) baseQuery = baseQuery.Where(x => x.Category == category);
            if (f.HasValue) baseQuery = baseQuery.Where(x => x.ExpenseDate >= f.Value);
            if (t.HasValue) baseQuery = baseQuery.Where(x => x.ExpenseDate <= t.Value);
            // Tải theo user/category/ngày trước, rồi lọc search bỏ dấu trong bộ nhớ
            // (chạy được cả Postgres thật lẫn InMemory test, khớp cả "phở" lẫn "pho").
            var rows = await baseQuery.ToListAsync();
            if (!string.IsNullOrWhiteSpace(search)) {
                var needle = RemoveDiacritics(search.Trim());
                rows = rows.Where(x => RemoveDiacritics(x.Note ?? "").Contains(needle, StringComparison.Ordinal)).ToList();
            }
            var asc = string.Equals(sort, "oldest", StringComparison.OrdinalIgnoreCase);
            rows = (asc
                ? rows.OrderBy(x => x.ExpenseDate).ThenBy(x => x.Id)
                : rows.OrderByDescending(x => x.ExpenseDate).ThenByDescending(x => x.Id)).ToList();
            var total = rows.Count;
            var page_rows = rows.Skip((pg - 1) * ps).Take(ps).ToList();
            var items = page_rows.Select(x => new ExpenseDto(x.Id, x.Amount, x.Category, x.ImageUrl, x.Note, x.ExpenseDate.ToString("yyyy-MM-dd"), x.AiConfidence)).ToList();
            return Results.Ok(new PagedExpensesDto(items, total, pg, ps));
        });
        // Xóa hàng loạt (bulk action đầu tiên): chỉ xóa bản ghi của chính user + dọn ảnh.
        g.MapPost("/bulk-delete", async (BulkDeleteRequest req, ClaimsPrincipal p, AppDbContext db, StorageService storage) => {
            var uid = UserId(p);
            var ids = (req.Ids ?? new List<long>()).Distinct().Take(100).ToList();
            if (ids.Count == 0) return Results.BadRequest(new { message = "Ids is required." });
            var rows = await db.Expenses.Where(x => x.UserId == uid && ids.Contains(x.Id)).ToListAsync();
            var urls = rows.Select(x => x.ImageUrl).ToList();
            db.Expenses.RemoveRange(rows);
            await db.SaveChangesAsync();
            foreach (var url in urls) storage.DeleteByUrl(url);
            return Results.Ok(new { deleted = rows.Count });
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

    /// <summary>Parse chặt theo yyyy-MM-dd, không phụ thuộc culture của máy.</summary>
    private static bool TryParseDate(string value, out DateOnly date) =>
        DateOnly.TryParseExact(value, "yyyy-MM-dd", CultureInfo.InvariantCulture, DateTimeStyles.None, out date);

    /// <summary>Bỏ dấu tiếng Việt + thường hóa để tìm kiếm khớp cả "phở" lẫn "pho".</summary>
    private static string RemoveDiacritics(string value)
    {
        var normalized = value.ToLowerInvariant().Normalize(NormalizationForm.FormD);
        var sb = new StringBuilder(normalized.Length);
        foreach (var c in normalized) {
            if (CharUnicodeInfo.GetUnicodeCategory(c) == UnicodeCategory.NonSpacingMark) continue;
            sb.Append(c == 'đ' ? 'd' : c);
        }
        return sb.ToString().Normalize(NormalizationForm.FormC);
    }
    public record ExpenseUpsert(long Amount, string Category, string? Note, DateOnly ExpenseDate);
    public record BulkDeleteRequest(List<long> Ids);
    public record PagedExpensesDto(List<ExpenseDto> Items, int Total, int Page, int PageSize);
    public record ExpenseRestore(long Amount, string Category, string? Note, DateOnly ExpenseDate, string? ImageUrl, double? AiConfidence);
    public record SharedExpenseDto(long Id, long Amount, string Category, string? ImageUrl, string? Note, string ExpenseDate, double? AiConfidence, string OwnerUsername);
    public record ExpenseDto(long Id, long Amount, string Category, string? ImageUrl, string? Note, string ExpenseDate, double? AiConfidence);
}
