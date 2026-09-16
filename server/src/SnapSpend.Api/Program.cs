using System.Text;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using SnapSpend.Api.Data;
using SnapSpend.Api.Endpoints;
using SnapSpend.Api.Services;

var builder = WebApplication.CreateBuilder(args);
var connection = builder.Configuration.GetConnectionString("Default")!;
builder.Services.AddDbContext<AppDbContext>(options => options.UseNpgsql(connection).UseSnakeCaseNamingConvention());
builder.Services.AddScoped<AuthService>();
builder.Services.AddScoped<StorageService>();
builder.Services.AddScoped<RecognitionService>();
builder.Services.AddScoped<OpenRouterService>();
builder.Services.AddScoped<AiService>();
builder.Services.AddMemoryCache();
builder.Services.AddHttpClient("ai", client => client.Timeout = TimeSpan.FromSeconds(8));
builder.Services.AddHttpClient("openrouter", client => client.Timeout = TimeSpan.FromSeconds(30));
var jwt = builder.Configuration.GetSection("Jwt");
var jwtKey = jwt["Key"];
if (string.IsNullOrWhiteSpace(jwtKey) || jwtKey.Length < 32 || jwtKey.Contains("CHANGE_ME", StringComparison.OrdinalIgnoreCase))
    throw new InvalidOperationException("Cấu hình Jwt:Key thiếu, ngắn hơn 32 ký tự hoặc vẫn là placeholder. Hãy đặt secret mạnh qua biến môi trường Jwt__Key.");
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme).AddJwtBearer(options => {
    options.TokenValidationParameters = new TokenValidationParameters { ValidateIssuer = true, ValidateAudience = true, ValidateLifetime = true, ValidateIssuerSigningKey = true, ValidIssuer = jwt["Issuer"], ValidAudience = jwt["Audience"], IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtKey)) };
});
builder.Services.AddAuthorization();

var app = builder.Build();
app.UseStaticFiles();
app.UseAuthentication();
app.UseAuthorization();
app.MapGet("/", () => Results.Ok(new { app = "SnapSpend API", status = "ok" }));
app.MapGet("/health", async (AppDbContext db) => {
    try {
        return await db.Database.CanConnectAsync()
            ? Results.Ok(new { status = "healthy" })
            : Results.Json(new { status = "unhealthy" }, statusCode: StatusCodes.Status503ServiceUnavailable);
    } catch {
        return Results.Json(new { status = "unhealthy" }, statusCode: StatusCodes.Status503ServiceUnavailable);
    }
});
app.MapAuth();
app.MapExpenses();
app.MapStats();
app.MapFriends();
app.MapAccount();
app.MapCategories();
app.MapAi();

using (var scope = app.Services.CreateScope()) {
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    // Khi test: dùng DB in-memory (EnsureCreated); bình thường: apply migration.
    if (app.Environment.IsEnvironment("Testing")) await db.Database.EnsureCreatedAsync();
    else await db.Database.MigrateAsync();
}

app.Run();

// Cho phép WebApplicationFactory<Program> trong project test truy cập.
public partial class Program { }
