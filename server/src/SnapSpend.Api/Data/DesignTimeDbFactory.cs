using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace SnapSpend.Api.Data;

/// <summary>
/// Factory cho `dotnet ef` lúc design-time (tạo migration).
/// Runtime vẫn cấu hình trong Program.cs. Không chứa secret:
/// đọc từ biến môi trường, fallback về Postgres local.
/// </summary>
public class DesignTimeDbFactory : IDesignTimeDbContextFactory<AppDbContext>
{
    public AppDbContext CreateDbContext(string[] args)
    {
        var options = new DbContextOptionsBuilder<AppDbContext>();
        var cs = Environment.GetEnvironmentVariable("ConnectionStrings__Default")
            ?? "Host=localhost;Port=5432;Database=snapspend;Username=snapspend;Password=snapspend";
        options.UseNpgsql(cs).UseSnakeCaseNamingConvention();
        return new AppDbContext(options.Options);
    }
}
