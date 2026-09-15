using SnapSpend.Api.Models;

namespace SnapSpend.Api.Endpoints;

public static class CategoryEndpoints
{
    public static IEndpointRouteBuilder MapCategories(this IEndpointRouteBuilder app)
    {
        // Đọc công khai để client (kể cả chưa đăng nhập) hiển thị danh mục.
        app.MapGet("/api/categories", () =>
            Results.Ok(CategoryCatalog.All.Select(c => new CategoryDto(c.Key, c.Name, c.Emoji))));
        return app;
    }

    public record CategoryDto(string Key, string Name, string Emoji);
}
