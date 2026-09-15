namespace SnapSpend.Api.Models;

/// <summary>
/// Nguồn category duy nhất: dùng cho seed, validate và endpoint GET /api/categories.
/// Thêm/đổi category chỉ sửa ở đây.
/// </summary>
public static class CategoryCatalog
{
    public static readonly (string Key, string Name, string Emoji)[] All =
    [
        ("food", "Ăn uống", "🍜"),
        ("shopping", "Mua sắm", "🛍️"),
        ("transport", "Đi lại", "🛵"),
        ("entertainment", "Giải trí", "🎬"),
        ("housing", "Nhà ở", "🏠"),
        ("health", "Sức khỏe", "💊"),
        ("education", "Giáo dục", "📚"),
        ("bills", "Hóa đơn", "🧾"),
        ("other", "Khác", "•")
    ];

    public static readonly HashSet<string> Keys = new(All.Select(c => c.Key), StringComparer.Ordinal);

    public static IEnumerable<Category> AsEntities() => All.Select(c => new Category { Key = c.Key, Name = c.Name, Emoji = c.Emoji });
}
