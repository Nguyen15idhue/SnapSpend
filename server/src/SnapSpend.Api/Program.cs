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
builder.Services.AddScoped<AiService>();
builder.Services.AddHttpClient("openai", client => client.Timeout = TimeSpan.FromSeconds(45));
var jwt = builder.Configuration.GetSection("Jwt");
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme).AddJwtBearer(options => {
    options.TokenValidationParameters = new TokenValidationParameters { ValidateIssuer = true, ValidateAudience = true, ValidateLifetime = true, ValidateIssuerSigningKey = true, ValidIssuer = jwt["Issuer"], ValidAudience = jwt["Audience"], IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwt["Key"]!)) };
});
builder.Services.AddAuthorization();

var app = builder.Build();
app.UseStaticFiles();
app.UseAuthentication();
app.UseAuthorization();
app.MapGet("/", () => Results.Ok(new { app = "SnapSpend API", status = "ok" }));
app.MapAuth();
app.MapExpenses();
app.MapStats();
app.MapFriends();
app.MapAccount();

using (var scope = app.Services.CreateScope()) {
    var db = scope.ServiceProvider.GetRequiredService<AppDbContext>();
    await db.Database.EnsureCreatedAsync();
    if (!await db.Categories.AnyAsync()) {
        db.Categories.AddRange(
            new SnapSpend.Api.Models.Category { Key = "food", Name = "Ăn uống", Emoji = "🍜" },
            new SnapSpend.Api.Models.Category { Key = "shopping", Name = "Shopping", Emoji = "🛍️" },
            new SnapSpend.Api.Models.Category { Key = "transport", Name = "Đi lại", Emoji = "🛵" },
            new SnapSpend.Api.Models.Category { Key = "entertainment", Name = "Giải trí", Emoji = "🎬" },
            new SnapSpend.Api.Models.Category { Key = "housing", Name = "Nhà ở", Emoji = "🏠" },
            new SnapSpend.Api.Models.Category { Key = "health", Name = "Sức khỏe", Emoji = "💊" },
            new SnapSpend.Api.Models.Category { Key = "education", Name = "Học tập", Emoji = "📚" },
            new SnapSpend.Api.Models.Category { Key = "bills", Name = "Hóa đơn", Emoji = "🧾" },
            new SnapSpend.Api.Models.Category { Key = "other", Name = "Khác", Emoji = "•" });
        await db.SaveChangesAsync();
    }
}

app.Run();
