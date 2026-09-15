using System.Security.Claims;
using Microsoft.EntityFrameworkCore;
using SnapSpend.Api.Data;
using SnapSpend.Api.Services;

namespace SnapSpend.Api.Endpoints;

public static class AccountEndpoints
{
    public static IEndpointRouteBuilder MapAccount(this IEndpointRouteBuilder app)
    {
        var g = app.MapGroup("/api/account").RequireAuthorization();
        g.MapDelete("", async (ClaimsPrincipal p, AppDbContext db, StorageService storage) => {
            var uid = long.Parse(p.FindFirstValue(ClaimTypes.NameIdentifier)!);
            var user = await db.Users.SingleOrDefaultAsync(x => x.Id == uid);
            if (user is null) return Results.NotFound();
            var imageUrls = await db.Expenses.Where(x => x.UserId == uid && x.ImageUrl != null).Select(x => x.ImageUrl!).ToListAsync();
            db.Users.Remove(user);
            await db.SaveChangesAsync();
            // Dọn ảnh sau khi xóa dữ liệu (dùng chung helper với DELETE expense).
            foreach (var url in imageUrls) storage.DeleteByUrl(url);
            return Results.Ok(new { message = "Account and associated data deleted." });
        });
        return app;
    }
}
