using System.Security.Claims;
using Microsoft.EntityFrameworkCore;
using SnapSpend.Api.Data;

namespace SnapSpend.Api.Endpoints;

public static class AccountEndpoints
{
    public static IEndpointRouteBuilder MapAccount(this IEndpointRouteBuilder app)
    {
        var g = app.MapGroup("/api/account").RequireAuthorization();
        g.MapDelete("", async (ClaimsPrincipal p, AppDbContext db) => {
            var uid = long.Parse(p.FindFirstValue(ClaimTypes.NameIdentifier)!);
            var user = await db.Users.SingleOrDefaultAsync(x => x.Id == uid);
            if (user is null) return Results.NotFound();
            var imageUrls = await db.Expenses.Where(x => x.UserId == uid && x.ImageUrl != null).Select(x => x.ImageUrl!).ToListAsync();
            db.Users.Remove(user);
            foreach (var url in imageUrls) {
                try {
                    var fileName = Path.GetFileName(new Uri(url).AbsolutePath);
                    var filePath = Path.Combine(Directory.GetCurrentDirectory(), "wwwroot", "uploads", fileName);
                    if (File.Exists(filePath)) File.Delete(filePath);
                } catch { }
            }
            await db.SaveChangesAsync();
            return Results.Ok(new { message = "Account and associated data deleted." });
        });
        return app;
    }
}
