using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using SnapSpend.Api.Data;

namespace SnapSpend.Api.Tests;

/// <summary>
/// WebApplicationFactory dùng DB InMemory, không phụ thuộc Postgres local.
/// Mỗi factory một DB riêng để test độc lập.
/// </summary>
public class ApiFactory : WebApplicationFactory<Program>
{
    private readonly string _dbName = $"snapspend-tests-{Guid.NewGuid():N}";

    protected override void ConfigureWebHost(IWebHostBuilder builder)
    {
        builder.UseEnvironment("Testing");
        builder.UseSetting("Jwt:Key", "test-secret-key-0123456789abcdefghijklmnop");
        builder.ConfigureServices(services =>
        {
            // Gỡ toàn bộ đăng ký DbContext của Npgsql (gồm IDbContextOptionsConfiguration) trước khi thay bằng InMemory.
            var remove = services.Where(d =>
                d.ServiceType == typeof(AppDbContext) ||
                (d.ServiceType.FullName?.Contains("DbContextOptions") ?? false) ||
                (d.ServiceType.FullName?.Contains("IDbContextOptionsConfiguration") ?? false)).ToList();
            foreach (var d in remove) services.Remove(d);
            services.AddDbContext<AppDbContext>(o => o.UseInMemoryDatabase(_dbName).UseSnakeCaseNamingConvention());
        });
    }
}
