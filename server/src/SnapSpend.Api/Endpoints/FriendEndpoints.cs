using System.Security.Claims;
using Microsoft.EntityFrameworkCore;
using SnapSpend.Api.Data;
using SnapSpend.Api.Models;

namespace SnapSpend.Api.Endpoints;

public static class FriendEndpoints
{
    public static IEndpointRouteBuilder MapFriends(this IEndpointRouteBuilder app)
    {
        var g = app.MapGroup("/api/friends").RequireAuthorization();
        g.MapGet("", async (ClaimsPrincipal p, AppDbContext db) => {
            var uid = UserId(p);
            var ids = await db.Friendships.Where(x => (x.UserId == uid || x.FriendId == uid) && x.Status == "accepted").Select(x => x.UserId == uid ? x.FriendId : x.UserId).ToListAsync();
            return await db.Users.Where(u => ids.Contains(u.Id)).Select(u => new FriendDto(u.Id, u.Username, u.Email)).ToListAsync();
        });
        g.MapPost("", async (AddFriendRequest req, ClaimsPrincipal p, AppDbContext db) => {
            var uid = UserId(p); var target = await db.Users.SingleOrDefaultAsync(x => x.Username == req.Username && x.Id != uid);
            if (target is null) return Results.NotFound(new { message = "User not found." });
            var existing = await db.Friendships.SingleOrDefaultAsync(x => (x.UserId == uid && x.FriendId == target.Id) || (x.UserId == target.Id && x.FriendId == uid));
            if (existing is null) { existing = new Friendship { UserId = uid, FriendId = target.Id, Status = "accepted" }; db.Friendships.Add(existing); }
            else existing.Status = "accepted";
            await db.SaveChangesAsync(); return Results.Ok(new FriendDto(target.Id, target.Username, target.Email));
        });
        return app;
    }
    private static long UserId(ClaimsPrincipal p) => long.Parse(p.FindFirstValue(ClaimTypes.NameIdentifier)!);
    public record AddFriendRequest(string Username);
    public record FriendDto(long Id, string Username, string Email);
}
