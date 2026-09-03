using Microsoft.EntityFrameworkCore;
using SnapSpend.Api.Data;
using SnapSpend.Api.Services;

namespace SnapSpend.Api.Endpoints;

public static class AuthEndpoints
{
    public static IEndpointRouteBuilder MapAuth(this IEndpointRouteBuilder app)
    {
        var g = app.MapGroup("/api/auth").AllowAnonymous();
        g.MapPost("/register", async (RegisterRequest req, AppDbContext db, AuthService auth) => {
            if (req.Password.Length < 6) return Results.BadRequest(new { message = "Password must be at least 6 characters." });
            if (await db.Users.AnyAsync(u => u.Email == req.Email.ToLowerInvariant())) return Results.Conflict(new { message = "Email already exists." });
            if (await db.Users.AnyAsync(u => u.Username == req.Username)) return Results.Conflict(new { message = "Username already exists." });
            var user = new SnapSpend.Api.Models.User { Email = req.Email.Trim().ToLowerInvariant(), Username = req.Username.Trim(), PasswordHash = auth.HashPassword(req.Password) };
            db.Users.Add(user); await db.SaveChangesAsync();
            return Results.Ok(new AuthResponse(auth.CreateToken(user), new UserDto(user.Id, user.Username, user.Email)));
        });
        g.MapPost("/login", async (LoginRequest req, AppDbContext db, AuthService auth) => {
            var user = await db.Users.SingleOrDefaultAsync(u => u.Email == req.Email.Trim().ToLowerInvariant());
            if (user is null || !auth.Verify(req.Password, user.PasswordHash)) return Results.Unauthorized();
            return Results.Ok(new AuthResponse(auth.CreateToken(user), new UserDto(user.Id, user.Username, user.Email)));
        });
        return app;
    }

    public record LoginRequest(string Email, string Password);
    public record RegisterRequest(string Email, string Username, string Password);
    public record UserDto(long Id, string Username, string Email);
    public record AuthResponse(string Token, UserDto User);
}
